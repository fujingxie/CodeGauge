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
