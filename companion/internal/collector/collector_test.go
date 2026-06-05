package collector

import (
	"context"
	"errors"
	"path/filepath"
	"reflect"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

func TestParseClaudeBlocksUsesActiveBlock(t *testing.T) {
	now := time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC)
	output := []byte(`{
	  "blocks": [
	    {
	      "id": "old",
	      "isActive": false,
	      "isGap": false,
	      "startTime": "2026-06-05T00:00:00.000Z",
	      "endTime": "2026-06-05T05:00:00.000Z",
	      "totalTokens": 100
	    },
	    {
	      "id": "active",
	      "isActive": true,
	      "isGap": false,
	      "startTime": "2026-06-05T10:00:00.000Z",
	      "endTime": "2026-06-05T15:00:00.000Z",
	      "totalTokens": 4321
	    }
	  ]
	}`)

	window, ok, err := parseClaudeBlocks(output, now)
	if err != nil {
		t.Fatalf("parseClaudeBlocks: %v", err)
	}
	if !ok {
		t.Fatal("ok = false, want true")
	}
	if window.ProviderID != store.ProviderClaude || window.WindowType != store.WindowTypeFiveHours {
		t.Fatalf("window identity = %s/%s, want claude/5h", window.ProviderID, window.WindowType)
	}
	assertInt64Ptr(t, "Used", window.Used, 4321)
	if window.PercentLeft != nil {
		t.Fatalf("PercentLeft = %v, want nil because ccusage does not expose quota left", *window.PercentLeft)
	}
	if window.ResetsAt == nil || window.ResetsAt.Format(time.RFC3339) != "2026-06-05T15:00:00Z" {
		t.Fatalf("ResetsAt = %v, want 2026-06-05T15:00:00Z", window.ResetsAt)
	}
}

func TestParseDailyUsageSumsLastSevenDays(t *testing.T) {
	now := time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC)
	output := []byte(`{
	  "daily": [
	    {"date": "2026-05-28", "totalTokens": 999},
	    {"date": "2026-05-30", "totalTokens": 100},
	    {"date": "2026-06-01", "totalTokens": 200},
	    {"date": "2026-06-05", "totalTokens": 300}
	  ],
	  "totals": {"totalTokens": 1599}
	}`)

	window, ok, err := parseDailyUsage(output, store.ProviderCodex, now)
	if err != nil {
		t.Fatalf("parseDailyUsage: %v", err)
	}
	if !ok {
		t.Fatal("ok = false, want true")
	}
	if window.ProviderID != store.ProviderCodex || window.WindowType != store.WindowTypeWeekly {
		t.Fatalf("window identity = %s/%s, want codex/weekly", window.ProviderID, window.WindowType)
	}
	assertInt64Ptr(t, "Used", window.Used, 600)
	if window.ResetsAt != nil {
		t.Fatalf("ResetsAt = %v, want nil because ccusage daily does not expose reset time", window.ResetsAt)
	}
}

func TestCollectOnceWritesProviderWindows(t *testing.T) {
	db := openTestStore(t)
	now := time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC)
	runner := &fakeRunner{
		outputs: map[string][]byte{
			"ccusage claude blocks --json --recent --offline --token-limit max": []byte(`{
			  "blocks": [
			    {
			      "id": "active",
			      "isActive": true,
			      "isGap": false,
			      "endTime": "2026-06-05T15:00:00.000Z",
			      "totalTokens": 400
			    }
			  ]
			}`),
			"ccusage claude daily --json --offline": []byte(`{
			  "daily": [
			    {"date": "2026-06-04", "totalTokens": 100},
			    {"date": "2026-06-05", "totalTokens": 200}
			  ]
			}`),
			"ccusage codex daily --json --offline": []byte(`{
			  "daily": [
			    {"date": "2026-06-03", "totalTokens": 300},
			    {"date": "2026-06-05", "totalTokens": 500}
			  ]
			}`),
		},
	}
	collector := New(db, Options{
		CCUsagePath: "ccusage",
		Runner:      runner,
		Now:         func() time.Time { return now },
	})

	if err := collector.CollectOnce(context.Background()); err != nil {
		t.Fatalf("CollectOnce: %v", err)
	}

	claude, err := db.GetProvider(store.ProviderClaude)
	if err != nil {
		t.Fatalf("GetProvider claude: %v", err)
	}
	if !claude.Available {
		t.Fatal("claude.Available = false, want true")
	}
	claudeWindows, err := db.ListQuotaWindows(store.ProviderClaude)
	if err != nil {
		t.Fatalf("ListQuotaWindows claude: %v", err)
	}
	if len(claudeWindows) != 2 {
		t.Fatalf("claude windows length = %d, want 2", len(claudeWindows))
	}

	codex, err := db.GetProvider(store.ProviderCodex)
	if err != nil {
		t.Fatalf("GetProvider codex: %v", err)
	}
	if !codex.Available {
		t.Fatal("codex.Available = false, want true")
	}
	codexWindows, err := db.ListQuotaWindows(store.ProviderCodex)
	if err != nil {
		t.Fatalf("ListQuotaWindows codex: %v", err)
	}
	if len(codexWindows) != 1 {
		t.Fatalf("codex windows length = %d, want 1", len(codexWindows))
	}
	assertInt64Ptr(t, "codex weekly used", codexWindows[0].Used, 800)

	wantCommands := []string{
		"ccusage claude blocks --json --recent --offline --token-limit max",
		"ccusage claude daily --json --offline",
		"ccusage codex daily --json --offline",
	}
	if !reflect.DeepEqual(runner.commands, wantCommands) {
		t.Fatalf("commands = %#v, want %#v", runner.commands, wantCommands)
	}
}

func TestCollectOnceMarksProvidersUnavailableWhenCommandsFail(t *testing.T) {
	db := openTestStore(t)
	runner := &fakeRunner{err: errors.New("ccusage missing")}
	collector := New(db, Options{
		CCUsagePath: "ccusage",
		Runner:      runner,
		Now:         func() time.Time { return time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC) },
	})

	err := collector.CollectOnce(context.Background())
	if err == nil {
		t.Fatal("CollectOnce error = nil, want joined command errors for diagnostics")
	}

	claude, getErr := db.GetProvider(store.ProviderClaude)
	if getErr != nil {
		t.Fatalf("GetProvider claude: %v", getErr)
	}
	if claude.Available {
		t.Fatal("claude.Available = true, want false")
	}

	codex, getErr := db.GetProvider(store.ProviderCodex)
	if getErr != nil {
		t.Fatalf("GetProvider codex: %v", getErr)
	}
	if codex.Available {
		t.Fatal("codex.Available = true, want false")
	}
}

func TestRunCollectsImmediatelyAndStopsOnContextCancel(t *testing.T) {
	db := openTestStore(t)
	ctx, cancel := context.WithCancel(context.Background())
	runner := &fakeRunner{
		outputs: map[string][]byte{
			"ccusage claude blocks --json --recent --offline --token-limit max": []byte(`{"blocks":[]}`),
			"ccusage claude daily --json --offline":                             []byte(`{"daily":[{"date":"2026-06-05","totalTokens":100}]}`),
			"ccusage codex daily --json --offline":                              []byte(`{"daily":[{"date":"2026-06-05","totalTokens":200}]}`),
		},
		onCommand: func(count int) {
			if count == 3 {
				cancel()
			}
		},
	}
	collector := New(db, Options{
		CCUsagePath: "ccusage",
		Runner:      runner,
		Now:         func() time.Time { return time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC) },
	})

	if err := collector.Run(ctx, time.Hour); err != nil {
		t.Fatalf("Run: %v", err)
	}
	if len(runner.commands) != 3 {
		t.Fatalf("commands length = %d, want immediate collection with 3 commands", len(runner.commands))
	}
}

func openTestStore(t *testing.T) *store.Store {
	t.Helper()

	db, err := store.Open(filepath.Join(t.TempDir(), "codegauge.db"))
	if err != nil {
		t.Fatalf("Open store: %v", err)
	}
	t.Cleanup(func() {
		if err := db.Close(); err != nil {
			t.Fatalf("Close store: %v", err)
		}
	})

	return db
}

func assertInt64Ptr(t *testing.T, name string, got *int64, want int64) {
	t.Helper()
	if got == nil {
		t.Fatalf("%s = nil, want %d", name, want)
	}
	if *got != want {
		t.Fatalf("%s = %d, want %d", name, *got, want)
	}
}

type fakeRunner struct {
	outputs   map[string][]byte
	err       error
	commands  []string
	onCommand func(count int)
}

func (r *fakeRunner) Run(_ context.Context, name string, args ...string) ([]byte, error) {
	command := name
	for _, arg := range args {
		command += " " + arg
	}
	r.commands = append(r.commands, command)
	if r.onCommand != nil {
		r.onCommand(len(r.commands))
	}

	if r.err != nil {
		return nil, r.err
	}
	output, ok := r.outputs[command]
	if !ok {
		return nil, errors.New("unexpected command: " + command)
	}

	return output, nil
}
