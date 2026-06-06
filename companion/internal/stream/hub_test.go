package stream

import (
	"path/filepath"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

func TestHubPublishesToSubscriber(t *testing.T) {
	hub := NewHub()
	subscription := hub.Subscribe()
	defer subscription.Close()

	hub.Publish(Message{EventType: EventTypeSessionUpdate, Data: SessionUpdate{
		ProviderID:  store.ProviderClaude,
		ProjectPath: "/work/codegauge",
		State:       store.SessionStateRunning,
	}})

	message := receiveMessage(t, subscription.Messages())
	if message.EventType != EventTypeSessionUpdate {
		t.Fatalf("EventType = %q, want %q", message.EventType, EventTypeSessionUpdate)
	}
}

func TestNotifyingStorePublishesQuotaUpdateAndThresholdAlert(t *testing.T) {
	db := openStreamTestStore(t)
	hub := NewHub()
	subscription := hub.Subscribe()
	defer subscription.Close()
	notifyingStore := NewNotifyingStore(db, hub, Options{
		WarningThreshold:  80,
		CriticalThreshold: 95,
	})

	if err := notifyingStore.UpsertProvider(store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}

	used := int64(50)
	limit := int64(100)
	if err := notifyingStore.UpsertQuotaWindow(store.QuotaWindow{
		ProviderID: store.ProviderClaude,
		WindowType: store.WindowTypeFiveHours,
		Used:       &used,
		Limit:      &limit,
		Source:     store.SourceCCUsage,
		UpdatedAt:  testStreamNow(),
	}); err != nil {
		t.Fatalf("first UpsertQuotaWindow: %v", err)
	}

	firstMessage := receiveMessage(t, subscription.Messages())
	if firstMessage.EventType != EventTypeQuotaUpdate {
		t.Fatalf("EventType = %q, want %q", firstMessage.EventType, EventTypeQuotaUpdate)
	}
	assertNoMessage(t, subscription.Messages())

	used = 85
	if err := notifyingStore.UpsertQuotaWindow(store.QuotaWindow{
		ProviderID: store.ProviderClaude,
		WindowType: store.WindowTypeFiveHours,
		Used:       &used,
		Limit:      &limit,
		Source:     store.SourceCCUsage,
		UpdatedAt:  testStreamNow().Add(time.Minute),
	}); err != nil {
		t.Fatalf("second UpsertQuotaWindow: %v", err)
	}

	quotaMessage := receiveMessage(t, subscription.Messages())
	if quotaMessage.EventType != EventTypeQuotaUpdate {
		t.Fatalf("EventType = %q, want %q", quotaMessage.EventType, EventTypeQuotaUpdate)
	}
	alertMessage := receiveMessage(t, subscription.Messages())
	if alertMessage.EventType != EventTypeAlert {
		t.Fatalf("EventType = %q, want %q", alertMessage.EventType, EventTypeAlert)
	}
	alert, ok := alertMessage.Data.(Alert)
	if !ok {
		t.Fatalf("alert data type = %T, want Alert", alertMessage.Data)
	}
	if alert.Severity != AlertSeverityWarning || alert.UsagePercent != 85 || alert.Threshold != 80 {
		t.Fatalf("alert = %+v, want warning at 85/80", alert)
	}
}

func TestNotifyingStorePublishesSessionUpdate(t *testing.T) {
	db := openStreamTestStore(t)
	hub := NewHub()
	subscription := hub.Subscribe()
	defer subscription.Close()
	notifyingStore := NewNotifyingStore(db, hub, Options{})

	if err := notifyingStore.UpsertProvider(store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}
	if err := notifyingStore.UpsertCodingSession(store.CodingSession{
		ID:             "session-1",
		ProviderID:     store.ProviderClaude,
		ProjectPath:    "/work/codegauge",
		State:          store.SessionStateDone,
		StartedAt:      testStreamNow().Add(-time.Minute),
		LastActivityAt: testStreamNow(),
		LastEventType:  store.EventSessionDone,
	}); err != nil {
		t.Fatalf("UpsertCodingSession: %v", err)
	}

	message := receiveMessage(t, subscription.Messages())
	if message.EventType != EventTypeSessionUpdate {
		t.Fatalf("EventType = %q, want %q", message.EventType, EventTypeSessionUpdate)
	}
	update, ok := message.Data.(SessionUpdate)
	if !ok {
		t.Fatalf("session update data type = %T, want SessionUpdate", message.Data)
	}
	if update.ProviderID != store.ProviderClaude || update.State != store.SessionStateDone || update.ProjectPath != "/work/codegauge" {
		t.Fatalf("session update = %+v, want claude done /work/codegauge", update)
	}
}

func openStreamTestStore(t *testing.T) *store.Store {
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

func receiveMessage(t *testing.T, messages <-chan Message) Message {
	t.Helper()
	select {
	case message := <-messages:
		return message
	case <-time.After(time.Second):
		t.Fatal("timed out waiting for stream message")
		return Message{}
	}
}

func assertNoMessage(t *testing.T, messages <-chan Message) {
	t.Helper()
	select {
	case message := <-messages:
		t.Fatalf("unexpected message: %+v", message)
	case <-time.After(50 * time.Millisecond):
	}
}

func testStreamNow() time.Time {
	return time.Date(2026, 6, 6, 12, 0, 0, 0, time.UTC)
}
