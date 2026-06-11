package config

import "testing"

func TestLoadUsesDefaults(t *testing.T) {
	t.Setenv("CODEGAUGE_HOST", "")
	t.Setenv("CODEGAUGE_PORT", "")
	t.Setenv("CODEGAUGE_COLLECT_INTERVAL_SECONDS", "")
	t.Setenv("CODEGAUGE_WATCH_INTERVAL_SECONDS", "")
	t.Setenv("CODEGAUGE_WARNING_THRESHOLD", "")
	t.Setenv("CODEGAUGE_CRITICAL_THRESHOLD", "")
	t.Setenv("CODEGAUGE_CCUSAGE_PATH", "")
	t.Setenv("CODEGAUGE_DB_PATH", "")
	t.Setenv("CODEGAUGE_PAIR_CODE", "")
	t.Setenv("CODEGAUGE_SERVER_NAME", "")
	t.Setenv("CODEGAUGE_TRAY_ENABLED", "")

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
	if cfg.WatchIntervalSeconds != 10 {
		t.Fatalf("WatchIntervalSeconds = %d, want 10", cfg.WatchIntervalSeconds)
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
	if cfg.DatabasePath == "" {
		t.Fatal("DatabasePath is empty, want default path")
	}
	if cfg.PairCode != "" {
		t.Fatalf("PairCode = %q, want empty default", cfg.PairCode)
	}
	if cfg.ServerName != "CodeGauge Companion" {
		t.Fatalf("ServerName = %q, want CodeGauge Companion", cfg.ServerName)
	}
	if !cfg.TrayEnabled {
		t.Fatal("TrayEnabled = false, want true")
	}
}

func TestLoadReadsEnvironment(t *testing.T) {
	t.Setenv("CODEGAUGE_HOST", "127.0.0.1")
	t.Setenv("CODEGAUGE_PORT", "9001")
	t.Setenv("CODEGAUGE_COLLECT_INTERVAL_SECONDS", "30")
	t.Setenv("CODEGAUGE_WATCH_INTERVAL_SECONDS", "5")
	t.Setenv("CODEGAUGE_WARNING_THRESHOLD", "70")
	t.Setenv("CODEGAUGE_CRITICAL_THRESHOLD", "90")
	t.Setenv("CODEGAUGE_CCUSAGE_PATH", "/opt/bin/ccusage")
	t.Setenv("CODEGAUGE_DB_PATH", "/tmp/codegauge.db")
	t.Setenv("CODEGAUGE_PAIR_CODE", "481920")
	t.Setenv("CODEGAUGE_SERVER_NAME", "Dev Mac")
	t.Setenv("CODEGAUGE_TRAY_ENABLED", "false")

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
	if cfg.WatchIntervalSeconds != 5 {
		t.Fatalf("WatchIntervalSeconds = %d, want 5", cfg.WatchIntervalSeconds)
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
	if cfg.DatabasePath != "/tmp/codegauge.db" {
		t.Fatalf("DatabasePath = %q, want /tmp/codegauge.db", cfg.DatabasePath)
	}
	if cfg.PairCode != "481920" {
		t.Fatalf("PairCode = %q, want 481920", cfg.PairCode)
	}
	if cfg.ServerName != "Dev Mac" {
		t.Fatalf("ServerName = %q, want Dev Mac", cfg.ServerName)
	}
	if cfg.TrayEnabled {
		t.Fatal("TrayEnabled = true, want false")
	}
}

func TestLoadRejectsInvalidPort(t *testing.T) {
	t.Setenv("CODEGAUGE_PORT", "nope")

	_, err := Load()
	if err == nil {
		t.Fatal("Load returned nil error for invalid port")
	}
}

func TestLoadRejectsInvalidPairCode(t *testing.T) {
	t.Setenv("CODEGAUGE_PAIR_CODE", "abc123")

	_, err := Load()
	if err == nil {
		t.Fatal("Load returned nil error for invalid pair code")
	}
}

func TestLoadRejectsInvalidTrayEnabled(t *testing.T) {
	t.Setenv("CODEGAUGE_TRAY_ENABLED", "sometimes")

	_, err := Load()
	if err == nil {
		t.Fatal("Load returned nil error for invalid tray setting")
	}
}
