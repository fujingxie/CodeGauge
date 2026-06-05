package watcher

import (
	"context"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

func TestWatcherMarksProcessLifecycle(t *testing.T) {
	db := openWatcherTestStore(t)
	lister := &fakeProcessLister{}
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)
	currentNow := now
	w := New(db, Options{
		Lister: lister,
		Now: func() time.Time {
			return currentNow
		},
	})

	lister.processes = []Process{{Name: "/Users/xiexiansheng/.local/bin/claude"}}
	if err := w.ScanOnce(context.Background()); err != nil {
		t.Fatalf("first ScanOnce: %v", err)
	}

	session, err := db.GetCodingSession("watcher:claude")
	if err != nil {
		t.Fatalf("GetCodingSession running: %v", err)
	}
	if session.ProviderID != store.ProviderClaude || session.State != store.SessionStateRunning {
		t.Fatalf("session = %+v, want claude running", session)
	}
	if session.ProjectPath != "" {
		t.Fatalf("ProjectPath = %q, want empty watcher path", session.ProjectPath)
	}
	if session.LastEventType != store.EventSessionStart {
		t.Fatalf("LastEventType = %q, want session_start", session.LastEventType)
	}

	currentNow = now.Add(time.Minute)
	if err := w.ScanOnce(context.Background()); err != nil {
		t.Fatalf("second ScanOnce: %v", err)
	}
	events, err := db.ListEvents(10)
	if err != nil {
		t.Fatalf("ListEvents after duplicate scan: %v", err)
	}
	if len(events) != 1 {
		t.Fatalf("events length = %d, want no duplicate start event", len(events))
	}

	currentNow = now.Add(2 * time.Minute)
	lister.processes = nil
	if err := w.ScanOnce(context.Background()); err != nil {
		t.Fatalf("third ScanOnce: %v", err)
	}

	session, err = db.GetCodingSession("watcher:claude")
	if err != nil {
		t.Fatalf("GetCodingSession done: %v", err)
	}
	if session.State != store.SessionStateDone {
		t.Fatalf("State = %q, want done", session.State)
	}
	if session.LastEventType != store.EventSessionDone {
		t.Fatalf("LastEventType = %q, want session_done", session.LastEventType)
	}
	if !session.StartedAt.Equal(now) {
		t.Fatalf("StartedAt = %s, want first seen %s", session.StartedAt, now)
	}

	events, err = db.ListEvents(10)
	if err != nil {
		t.Fatalf("ListEvents: %v", err)
	}
	if len(events) != 2 {
		t.Fatalf("events length = %d, want start and inferred done", len(events))
	}
	if events[0].Type != store.EventSessionDone || !strings.Contains(events[0].Payload, `"inferred":true`) {
		t.Fatalf("latest event = %+v, want inferred session_done", events[0])
	}
}

func TestWatcherDetectsCodexProcess(t *testing.T) {
	db := openWatcherTestStore(t)
	lister := &fakeProcessLister{processes: []Process{{Name: "codex"}}}
	w := New(db, Options{
		Lister: lister,
		Now: func() time.Time {
			return time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)
		},
	})

	if err := w.ScanOnce(context.Background()); err != nil {
		t.Fatalf("ScanOnce: %v", err)
	}

	session, err := db.GetCodingSession("watcher:codex")
	if err != nil {
		t.Fatalf("GetCodingSession: %v", err)
	}
	if session.ProviderID != store.ProviderCodex || session.State != store.SessionStateRunning {
		t.Fatalf("session = %+v, want codex running", session)
	}
}

type fakeProcessLister struct {
	processes []Process
}

func (l *fakeProcessLister) List(context.Context) ([]Process, error) {
	return l.processes, nil
}

func openWatcherTestStore(t *testing.T) *store.Store {
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
