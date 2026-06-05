package main

import (
	"context"
	"errors"
	"log"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/config"
	"github.com/xiexiansheng/codegauge/companion/internal/server"
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

	httpServer := &http.Server{
		Addr:              cfg.Address(),
		Handler:           server.NewRouter(version),
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
		return err
	case <-stop:
		log.Print("CodeGauge companion shutting down")
		ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
		defer cancel()
		return httpServer.Shutdown(ctx)
	}
}
