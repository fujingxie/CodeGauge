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
	"syscall"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/collector"
	"github.com/xiexiansheng/codegauge/companion/internal/config"
	"github.com/xiexiansheng/codegauge/companion/internal/server"
	"github.com/xiexiansheng/codegauge/companion/internal/store"
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

	quotaCollector := collector.New(db, collector.Options{
		CCUsagePath: cfg.CCUsagePath,
	})
	go func() {
		if err := quotaCollector.Run(appCtx, time.Duration(cfg.CollectIntervalSeconds)*time.Second); err != nil {
			log.Printf("collector stopped: %v", err)
		}
	}()

	httpServer := &http.Server{
		Addr: cfg.Address(),
		Handler: server.NewRouter(server.Options{
			Version:    version,
			ServerName: cfg.ServerName,
			PairCode:   pairCode,
			Store:      db,
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

	select {
	case err := <-errs:
		cancelApp()
		return err
	case <-stop:
		log.Print("CodeGauge companion shutting down")
		cancelApp()
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		return httpServer.Shutdown(ctx)
	}
}

func generatePairCode() (string, error) {
	value, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", fmt.Errorf("generate pair code: %w", err)
	}
	return fmt.Sprintf("%06d", value.Int64()), nil
}
