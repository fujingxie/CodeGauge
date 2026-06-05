package config

import "testing"

func TestLoadUsesDefaults(t *testing.T) {
	t.Setenv("CODEGAUGE_HOST", "")
	t.Setenv("CODEGAUGE_PORT", "")
	t.Setenv("CODEGAUGE_COLLECT_INTERVAL_SECONDS", "")
	t.Setenv("CODEGAUGE_WARNING_THRESHOLD", "")
	t.Setenv("CODEGAUGE_CRITICAL_THRESHOLD", "")
	t.Setenv("CODEGAUGE_CCUSAGE_PATH", "")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load returned error: %v", err)
	}

	if cfg.Host != "0.0.0.0" {
		t.Fatalf("Host = %q, want 0.0.0.0", cfg.Host)
	}
	if cfg.Port != 8765 {
		t.Fatalf("Port = %d, want 8765", cfg.Port)
	}
	if cfg.CollectIntervalSeconds != 60 {
		t.Fatalf("CollectIntervalSeconds = %d, want 60", cfg.CollectIntervalSeconds)
	}
	if cfg.WarningThreshold != 80 {
		t.Fatalf("WarningThreshold = %d, want 80", cfg.WarningThreshold)
	}
	if cfg.CriticalThreshold != 95 {
		t.Fatalf("CriticalThreshold = %d, want 95", cfg.CriticalThreshold)
	}
	if cfg.CCUsagePath != "ccusage" {
		t.Fatalf("CCUsagePath = %q, want ccusage", cfg.CCUsagePath)
	}
}

func TestLoadReadsEnvironment(t *testing.T) {
	t.Setenv("CODEGAUGE_HOST", "127.0.0.1")
	t.Setenv("CODEGAUGE_PORT", "9001")
	t.Setenv("CODEGAUGE_COLLECT_INTERVAL_SECONDS", "30")
	t.Setenv("CODEGAUGE_WARNING_THRESHOLD", "70")
	t.Setenv("CODEGAUGE_CRITICAL_THRESHOLD", "90")
	t.Setenv("CODEGAUGE_CCUSAGE_PATH", "/opt/bin/ccusage")

	cfg, err := Load()
	if err != nil {
		t.Fatalf("Load returned error: %v", err)
	}

	if cfg.Host != "127.0.0.1" {
		t.Fatalf("Host = %q, want 127.0.0.1", cfg.Host)
	}
	if cfg.Port != 9001 {
		t.Fatalf("Port = %d, want 9001", cfg.Port)
	}
	if cfg.CollectIntervalSeconds != 30 {
		t.Fatalf("CollectIntervalSeconds = %d, want 30", cfg.CollectIntervalSeconds)
	}
	if cfg.WarningThreshold != 70 {
		t.Fatalf("WarningThreshold = %d, want 70", cfg.WarningThreshold)
	}
	if cfg.CriticalThreshold != 90 {
		t.Fatalf("CriticalThreshold = %d, want 90", cfg.CriticalThreshold)
	}
	if cfg.CCUsagePath != "/opt/bin/ccusage" {
		t.Fatalf("CCUsagePath = %q, want /opt/bin/ccusage", cfg.CCUsagePath)
	}
}

func TestLoadRejectsInvalidPort(t *testing.T) {
	t.Setenv("CODEGAUGE_PORT", "nope")

	_, err := Load()
	if err == nil {
		t.Fatal("Load returned nil error for invalid port")
	}
}
