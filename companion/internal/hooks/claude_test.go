package hooks

import (
	"context"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

func TestClaudeReceiverTracksSessionLifecycle(t *testing.T) {
	db := openHookTestStore(t)
	receiver := NewClaudeReceiver(db)
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)

	for _, item := range []struct {
		name      string
		body      string
		now       time.Time
		wantState string
		wantEvent string
	}{
		{
			name: "start",
			body: `{
				"session_id":"claude-session-1",
				"hook_event_name":"SessionStart",
				"cwd":"/work/codegauge",
				"source":"startup",
				"model":"claude-opus-4-5"
			}`,
			now:       now,
			wantState: store.SessionStateRunning,
			wantEvent: store.EventSessionStart,
		},
		{
			name: "notification",
			body: `{
				"session_id":"claude-session-1",
				"hook_event_name":"Notification",
				"cwd":"/work/codegauge",
				"message":"Claude needs permission",
				"title":"Permission required",
				"notification_type":"permission"
			}`,
			now:       now.Add(time.Minute),
			wantState: store.SessionStateWaiting,
			wantEvent: store.EventSessionWaiting,
		},
		{
			name: "stop",
			body: `{
				"session_id":"claude-session-1",
				"hook_event_name":"Stop",
				"cwd":"/work/codegauge",
				"transcript_path":"/tmp/claude-session-1.jsonl",
				"last_assistant_message":"done"
			}`,
			now:       now.Add(2 * time.Minute),
			wantState: store.SessionStateDone,
			wantEvent: store.EventSessionDone,
		},
	} {
		t.Run(item.name, func(t *testing.T) {
			if err := receiver.Handle(context.Background(), []byte(item.body), item.now); err != nil {
				t.Fatalf("Handle: %v", err)
			}

			session, err := db.GetCodingSession("claude-session-1")
			if err != nil {
				t.Fatalf("GetCodingSession: %v", err)
			}
			if session.ProviderID != store.ProviderClaude {
				t.Fatalf("ProviderID = %q, want claude", session.ProviderID)
			}
			if session.ProjectPath != "/work/codegauge" {
				t.Fatalf("ProjectPath = %q, want /work/codegauge", session.ProjectPath)
			}
			if session.State != item.wantState {
				t.Fatalf("State = %q, want %q", session.State, item.wantState)
			}
			if session.LastEventType != item.wantEvent {
				t.Fatalf("LastEventType = %q, want %q", session.LastEventType, item.wantEvent)
			}
			if !session.StartedAt.Equal(now) {
				t.Fatalf("StartedAt = %s, want original %s", session.StartedAt, now)
			}
		})
	}

	events, err := db.ListEvents(10)
	if err != nil {
		t.Fatalf("ListEvents: %v", err)
	}
	if len(events) != 3 {
		t.Fatalf("events length = %d, want 3", len(events))
	}
	if events[0].Type != store.EventSessionDone || !strings.Contains(events[0].Payload, `"hook_event_name":"Stop"`) {
		t.Fatalf("latest event = %+v, want Stop session_done with raw payload", events[0])
	}
}

func TestClaudeReceiverRejectsMissingSessionID(t *testing.T) {
	db := openHookTestStore(t)
	receiver := NewClaudeReceiver(db)

	err := receiver.Handle(
		context.Background(),
		[]byte(`{"hook_event_name":"Stop","cwd":"/work/codegauge"}`),
		time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC),
	)
	if err == nil {
		t.Fatal("Handle error = nil, want missing session_id error")
	}
}

func openHookTestStore(t *testing.T) *store.Store {
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
