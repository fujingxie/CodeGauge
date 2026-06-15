package config

import (
	"fmt"
	"os"
	"path/filepath"
	"strconv"
)

type Config struct {
	Host                   string
	Port                   int
	CollectIntervalSeconds int
	WatchIntervalSeconds   int
	WarningThreshold       int
	CriticalThreshold      int
	CCUsagePath            string
	CodexPath              string
	DatabasePath           string
	PairCode               string
	PairCodeTTLSeconds     int
	PairCodeMaxAttempts    int
	ServerName             string
	TrayEnabled            bool
}

func Load() (Config, error) {
	cfg := Config{
		Host:                   stringWithDefault("CODEGAUGE_HOST", "0.0.0.0"),
		Port:                   8765,
		CollectIntervalSeconds: 60,
		WatchIntervalSeconds:   10,
		WarningThreshold:       80,
		CriticalThreshold:      95,
		CCUsagePath:            stringWithDefault("CODEGAUGE_CCUSAGE_PATH", "ccusage"),
		CodexPath:              stringWithDefault("CODEGAUGE_CODEX_PATH", defaultCodexPath()),
		DatabasePath:           stringWithDefault("CODEGAUGE_DB_PATH", defaultDatabasePath()),
		PairCode:               os.Getenv("CODEGAUGE_PAIR_CODE"),
		PairCodeTTLSeconds:     600,
		PairCodeMaxAttempts:    5,
		ServerName:             stringWithDefault("CODEGAUGE_SERVER_NAME", "CodeGauge Companion"),
		TrayEnabled:            true,
	}

	if err := readInt("CODEGAUGE_PORT", &cfg.Port); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_COLLECT_INTERVAL_SECONDS", &cfg.CollectIntervalSeconds); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_WATCH_INTERVAL_SECONDS", &cfg.WatchIntervalSeconds); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_WARNING_THRESHOLD", &cfg.WarningThreshold); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_CRITICAL_THRESHOLD", &cfg.CriticalThreshold); err != nil {
		return Config{}, err
	}
	if err := readBool("CODEGAUGE_TRAY_ENABLED", &cfg.TrayEnabled); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_PAIR_CODE_TTL_SECONDS", &cfg.PairCodeTTLSeconds); err != nil {
		return Config{}, err
	}
	if err := readInt("CODEGAUGE_PAIR_CODE_MAX_ATTEMPTS", &cfg.PairCodeMaxAttempts); err != nil {
		return Config{}, err
	}
	if err := cfg.Validate(); err != nil {
		return Config{}, err
	}

	return cfg, nil
}

func (cfg Config) Validate() error {
	if cfg.Port < 1 || cfg.Port > 65535 {
		return fmt.Errorf("CODEGAUGE_PORT must be between 1 and 65535, got %d", cfg.Port)
	}
	if cfg.CollectIntervalSeconds < 1 {
		return fmt.Errorf("CODEGAUGE_COLLECT_INTERVAL_SECONDS must be positive, got %d", cfg.CollectIntervalSeconds)
	}
	if cfg.WatchIntervalSeconds < 1 {
		return fmt.Errorf("CODEGAUGE_WATCH_INTERVAL_SECONDS must be positive, got %d", cfg.WatchIntervalSeconds)
	}
	if cfg.WarningThreshold < 0 || cfg.WarningThreshold > 100 {
		return fmt.Errorf("CODEGAUGE_WARNING_THRESHOLD must be between 0 and 100, got %d", cfg.WarningThreshold)
	}
	if cfg.CriticalThreshold < 0 || cfg.CriticalThreshold > 100 {
		return fmt.Errorf("CODEGAUGE_CRITICAL_THRESHOLD must be between 0 and 100, got %d", cfg.CriticalThreshold)
	}
	if cfg.WarningThreshold >= cfg.CriticalThreshold {
		return fmt.Errorf("CODEGAUGE_WARNING_THRESHOLD must be lower than CODEGAUGE_CRITICAL_THRESHOLD")
	}
	if cfg.PairCode != "" && !isSixDigitCode(cfg.PairCode) {
		return fmt.Errorf("CODEGAUGE_PAIR_CODE must be 6 digits")
	}
	if cfg.PairCodeTTLSeconds < 1 {
		return fmt.Errorf("CODEGAUGE_PAIR_CODE_TTL_SECONDS must be positive, got %d", cfg.PairCodeTTLSeconds)
	}
	if cfg.PairCodeMaxAttempts < 1 {
		return fmt.Errorf("CODEGAUGE_PAIR_CODE_MAX_ATTEMPTS must be positive, got %d", cfg.PairCodeMaxAttempts)
	}

	return nil
}

func (cfg Config) Address() string {
	return fmt.Sprintf("%s:%d", cfg.Host, cfg.Port)
}

func stringWithDefault(name string, defaultValue string) string {
	value := os.Getenv(name)
	if value == "" {
		return defaultValue
	}

	return value
}

func readInt(name string, target *int) error {
	value := os.Getenv(name)
	if value == "" {
		return nil
	}

	parsed, err := strconv.Atoi(value)
	if err != nil {
		return fmt.Errorf("%s must be an integer, got %q: %w", name, value, err)
	}

	*target = parsed
	return nil
}

func readBool(name string, target *bool) error {
	value := os.Getenv(name)
	if value == "" {
		return nil
	}

	parsed, err := strconv.ParseBool(value)
	if err != nil {
		return fmt.Errorf("%s must be a boolean, got %q: %w", name, value, err)
	}

	*target = parsed
	return nil
}

func defaultDatabasePath() string {
	configDir, err := os.UserConfigDir()
	if err != nil || configDir == "" {
		return "codegauge.db"
	}
	return filepath.Join(configDir, "CodeGauge", "codegauge.db")
}

func defaultCodexPath() string {
	const macAppPath = "/Applications/Codex.app/Contents/Resources/codex"
	if _, err := os.Stat(macAppPath); err == nil {
		return macAppPath
	}
	return "codex"
}

func isSixDigitCode(value string) bool {
	if len(value) != 6 {
		return false
	}
	for _, char := range value {
		if char < '0' || char > '9' {
			return false
		}
	}
	return true
}
