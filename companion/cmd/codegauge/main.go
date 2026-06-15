package main

import (
	"context"
	"crypto/rand"
	"errors"
	"fmt"
	"log"
	"math/big"
	"net/http"
	"os"
	"os/signal"
	"path/filepath"
	"strconv"
	"syscall"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/collector"
	"github.com/xiexiansheng/codegauge/companion/internal/config"
	"github.com/xiexiansheng/codegauge/companion/internal/discovery"
	"github.com/xiexiansheng/codegauge/companion/internal/server"
	"github.com/xiexiansheng/codegauge/companion/internal/store"
	codestream "github.com/xiexiansheng/codegauge/companion/internal/stream"
	"github.com/xiexiansheng/codegauge/companion/internal/tray"
	"github.com/xiexiansheng/codegauge/companion/internal/watcher"
)

const version = "dev"

func main() {
	if err := run(); err != nil {
		log.Printf("codegauge companion failed: %v", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	if err := os.MkdirAll(filepath.Dir(cfg.DatabasePath), 0o755); err != nil {
		return fmt.Errorf("create database directory: %w", err)
	}
	db, err := store.Open(cfg.DatabasePath)
	if err != nil {
		return err
	}
	defer db.Close()

	pairCode := cfg.PairCode
	if pairCode == "" {
		generated, err := generatePairCode()
		if err != nil {
			return err
		}
		pairCode = generated
	}
	log.Printf("CodeGauge pairing code: %s", pairCode)

	appCtx, cancelApp := context.WithCancel(context.Background())
	defer cancelApp()

	mdnsAdvertiser := discovery.NewAdvertiser(nil)
	if err := mdnsAdvertiser.Start(appCtx, discovery.ServiceConfig{
		Instance:   cfg.ServerName,
		ServerName: cfg.ServerName,
		Host:       cfg.Host,
		Port:       cfg.Port,
		Version:    version,
	}.Service()); err != nil {
		return err
	}
	log.Printf("CodeGauge mDNS service advertised as %s.%s.%s", cfg.ServerName, discovery.ServiceType, discovery.ServiceDomain)

	streamHub := codestream.NewHub()
	notifyingStore := codestream.NewNotifyingStore(db, streamHub, codestream.Options{
		WarningThreshold:  cfg.WarningThreshold,
		CriticalThreshold: cfg.CriticalThreshold,
	})

	quotaCollector := collector.New(notifyingStore, collector.Options{
		CCUsagePath: cfg.CCUsagePath,
		PreciseSources: []collector.PreciseSource{
			collector.CodexAppServerSource{
				CodexPath: cfg.CodexPath,
			},
		},
	})
	go func() {
		if err := quotaCollector.RunWithInterval(
			appCtx,
			func() time.Duration {
				return collectInterval(notifyingStore, cfg.CollectIntervalSeconds)
			},
			notifyingStore.SettingsChanged(),
		); err != nil {
			log.Printf("collector stopped: %v", err)
		}
	}()

	processWatcher := watcher.New(notifyingStore, watcher.Options{})
	go func() {
		if err := processWatcher.Run(appCtx, time.Duration(cfg.WatchIntervalSeconds)*time.Second); err != nil {
			log.Printf("process watcher stopped: %v", err)
		}
	}()

	httpServer := &http.Server{
		Addr: cfg.Address(),
		Handler: server.NewRouter(server.Options{
			Version:             version,
			ServerName:          cfg.ServerName,
			PairCode:            pairCode,
			Store:               notifyingStore,
			StreamHub:           streamHub,
			PairCodeTTL:         time.Duration(cfg.PairCodeTTLSeconds) * time.Second,
			PairCodeMaxAttempts: cfg.PairCodeMaxAttempts,
			SettingsDefaults: server.SettingsDefaults{
				CollectIntervalSeconds: cfg.CollectIntervalSeconds,
				WarningThreshold:       cfg.WarningThreshold,
				CriticalThreshold:      cfg.CriticalThreshold,
			},
		}),
		ReadHeaderTimeout: 5 * time.Second,
	}

	errs := make(chan error, 1)
	go func() {
		log.Printf("CodeGauge companion listening on %s", cfg.Address())
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			errs <- err
			return
		}
		errs <- nil
	}()

	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	shutdownHTTP := func() error {
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		return httpServer.Shutdown(ctx)
	}

	if cfg.TrayEnabled {
		termination := make(chan error, 1)
		go func() {
			select {
			case err := <-errs:
				cancelApp()
				termination <- err
			case <-stop:
				log.Print("CodeGauge companion shutting down")
				cancelApp()
				termination <- nil
			case <-appCtx.Done():
				termination <- nil
			}
		}()

		tray.Run(appCtx, tray.Options{
			ServerName: cfg.ServerName,
			Address:    cfg.Address(),
			PairCode:   pairCode,
			Version:    version,
			OnQuit:     cancelApp,
		})

		err := <-termination
		if err != nil {
			return err
		}
		return shutdownHTTP()
	}

	select {
	case err := <-errs:
		cancelApp()
		return err
	case <-appCtx.Done():
		log.Print("CodeGauge companion shutting down")
		return shutdownHTTP()
	case <-stop:
		log.Print("CodeGauge companion shutting down")
		cancelApp()
		return shutdownHTTP()
	}
}

type settingsLister interface {
	ListSettings() ([]store.Setting, error)
}

func collectInterval(settings settingsLister, fallbackSeconds int) time.Duration {
	if fallbackSeconds <= 0 {
		fallbackSeconds = 60
	}

	list, err := settings.ListSettings()
	if err != nil {
		log.Printf("read collect interval setting: %v", err)
		return time.Duration(fallbackSeconds) * time.Second
	}
	for _, setting := range list {
		if setting.Key != settingCollectIntervalSeconds {
			continue
		}
		seconds, err := strconv.Atoi(setting.Value)
		if err != nil {
			log.Printf("ignore invalid collect interval setting %q: %v", setting.Value, err)
			return time.Duration(fallbackSeconds) * time.Second
		}
		if seconds <= 0 {
			log.Printf("ignore non-positive collect interval setting: %d", seconds)
			return time.Duration(fallbackSeconds) * time.Second
		}
		return time.Duration(seconds) * time.Second
	}
	return time.Duration(fallbackSeconds) * time.Second
}

func generatePairCode() (string, error) {
	value, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", fmt.Errorf("generate pair code: %w", err)
	}
	return fmt.Sprintf("%06d", value.Int64()), nil
}

const settingCollectIntervalSeconds = "collect_interval_seconds"
