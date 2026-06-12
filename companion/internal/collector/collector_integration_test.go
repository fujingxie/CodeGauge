package collector

import (
	"context"
	"os"
	"path/filepath"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

func TestRealCCUsageCollectsAtLeastOneWindow(t *testing.T) {
	ccusagePath := os.Getenv("CODEGAUGE_REAL_CCUSAGE_PATH")
	if ccusagePath == "" {
		t.Skip("set CODEGAUGE_REAL_CCUSAGE_PATH to run real ccusage integration test")
	}

	db, err := store.Open(filepath.Join(t.TempDir(), "codegauge.db"))
	if err != nil {
		t.Fatalf("Open store: %v", err)
	}
	defer db.Close()

	collector := New(db, Options{
		CCUsagePath: ccusagePath,
		Now:         time.Now,
	})
	if err := collector.CollectOnce(context.Background()); err != nil {
		t.Fatalf("CollectOnce with real ccusage: %v", err)
	}

	claudeWindows, err := db.ListQuotaWindows(store.ProviderClaude)
	if err != nil {
		t.Fatalf("ListQuotaWindows claude: %v", err)
	}
	codexWindows, err := db.ListQuotaWindows(store.ProviderCodex)
	if err != nil {
		t.Fatalf("ListQuotaWindows codex: %v", err)
	}
	if len(claudeWindows)+len(codexWindows) == 0 {
		t.Fatal("real ccusage collection wrote no quota windows")
	}
}

func TestRealCodexAppServerCollectsRateLimits(t *testing.T) {
	codexPath := os.Getenv("CODEGAUGE_REAL_CODEX_PATH")
	if codexPath == "" {
		t.Skip("set CODEGAUGE_REAL_CODEX_PATH to run real Codex app-server integration test")
	}

	windows, err := (CodexAppServerSource{
		CodexPath: codexPath,
		Timeout:   15 * time.Second,
	}).Collect(context.Background(), time.Now())
	if err != nil {
		t.Fatalf("Collect with real Codex app-server: %v", err)
	}
	if len(windows) == 0 {
		t.Fatal("real Codex app-server returned no quota windows")
	}
	for _, window := range windows {
		if window.ProviderID != store.ProviderCodex {
			t.Fatalf("ProviderID = %q, want codex", window.ProviderID)
		}
		if window.Source != store.SourceEndpoint {
			t.Fatalf("Source = %q, want endpoint", window.Source)
		}
		if window.PercentLeft == nil {
			t.Fatalf("%s PercentLeft = nil, want precise percent", window.WindowType)
		}
	}
}
