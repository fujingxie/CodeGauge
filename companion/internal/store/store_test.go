package store

import (
	"path/filepath"
	"testing"
	"time"
)

func TestStorePersistsEntitiesAcrossReopen(t *testing.T) {
	dbPath := filepath.Join(t.TempDir(), "codegauge.db")
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)
	resetAt := now.Add(2 * time.Hour)
	percentLeft := 42
	used := int64(580)
	limit := int64(1000)
	providerID := "claude"

	db, err := Open(dbPath)
	if err != nil {
		t.Fatalf("Open: %v", err)
	}

	if err := db.UpsertProvider(Provider{
		ID:        providerID,
		Name:      "Claude",
		PlanTier:  "Max",
		Available: true,
	}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}

	if err := db.UpsertQuotaWindow(QuotaWindow{
		ProviderID:  providerID,
		WindowType:  WindowTypeFiveHours,
		PercentLeft: &percentLeft,
		Used:        &used,
		Limit:       &limit,
		ResetsAt:    &resetAt,
		Source:      SourceCCUsage,
		UpdatedAt:   now,
	}); err != nil {
		t.Fatalf("UpsertQuotaWindow: %v", err)
	}

	if err := db.UpsertCodingSession(CodingSession{
		ID:             "session-1",
		ProviderID:     providerID,
		ProjectPath:    "/work/codegauge",
		State:          SessionStateRunning,
		StartedAt:      now.Add(-10 * time.Minute),
		LastActivityAt: now,
		LastEventType:  EventSessionStart,
	}); err != nil {
		t.Fatalf("UpsertCodingSession: %v", err)
	}

	eventID, err := db.AddEvent(Event{
		ProviderID: &providerID,
		Type:       EventSessionStart,
		Payload:    `{"project_path":"/work/codegauge"}`,
		CreatedAt:  now,
	})
	if err != nil {
		t.Fatalf("AddEvent: %v", err)
	}
	if eventID <= 0 {
		t.Fatalf("eventID = %d, want positive", eventID)
	}

	if err := db.UpsertDevicePairing(DevicePairing{
		DeviceID:   "phone-1",
		Name:       "Pixel",
		Token:      "token-1",
		PairedAt:   now,
		LastSeenAt: now,
	}); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}

	if err := db.SetSetting("collect_interval_seconds", "60"); err != nil {
		t.Fatalf("SetSetting: %v", err)
	}

	if err := db.Close(); err != nil {
		t.Fatalf("Close: %v", err)
	}

	reopened, err := Open(dbPath)
	if err != nil {
		t.Fatalf("reopen: %v", err)
	}
	defer reopened.Close()

	provider, err := reopened.GetProvider(providerID)
	if err != nil {
		t.Fatalf("GetProvider: %v", err)
	}
	if provider.Name != "Claude" || provider.PlanTier != "Max" || !provider.Available {
		t.Fatalf("provider = %+v, want Claude Max available", provider)
	}

	windows, err := reopened.ListQuotaWindows(providerID)
	if err != nil {
		t.Fatalf("ListQuotaWindows: %v", err)
	}
	if len(windows) != 1 {
		t.Fatalf("windows length = %d, want 1", len(windows))
	}
	assertIntPtr(t, "PercentLeft", windows[0].PercentLeft, percentLeft)
	assertInt64Ptr(t, "Used", windows[0].Used, used)
	assertInt64Ptr(t, "Limit", windows[0].Limit, limit)
	assertTimePtr(t, "ResetsAt", windows[0].ResetsAt, resetAt)
	if windows[0].Source != SourceCCUsage {
		t.Fatalf("window Source = %q, want %q", windows[0].Source, SourceCCUsage)
	}

	sessions, err := reopened.ListCodingSessions()
	if err != nil {
		t.Fatalf("ListCodingSessions: %v", err)
	}
	if len(sessions) != 1 {
		t.Fatalf("sessions length = %d, want 1", len(sessions))
	}
	if sessions[0].State != SessionStateRunning || sessions[0].ProjectPath != "/work/codegauge" {
		t.Fatalf("session = %+v, want running /work/codegauge", sessions[0])
	}

	events, err := reopened.ListEvents(10)
	if err != nil {
		t.Fatalf("ListEvents: %v", err)
	}
	if len(events) != 1 {
		t.Fatalf("events length = %d, want 1", len(events))
	}
	if events[0].ID != eventID || events[0].Type != EventSessionStart || events[0].Payload == "" {
		t.Fatalf("event = %+v, want persisted event", events[0])
	}

	device, err := reopened.GetDevicePairing("phone-1")
	if err != nil {
		t.Fatalf("GetDevicePairing: %v", err)
	}
	if device.Name != "Pixel" || device.Token != "token-1" {
		t.Fatalf("device = %+v, want Pixel token-1", device)
	}

	setting, err := reopened.GetSetting("collect_interval_seconds")
	if err != nil {
		t.Fatalf("GetSetting: %v", err)
	}
	if setting.Value != "60" {
		t.Fatalf("setting.Value = %q, want 60", setting.Value)
	}
}

func TestListEventsReturnsNewestFirstAndHonorsLimit(t *testing.T) {
	db := openTestStore(t)
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)

	for _, item := range []Event{
		{Type: EventSessionStart, Payload: `{}`, CreatedAt: now.Add(-2 * time.Minute)},
		{Type: EventLimitWarn, Payload: `{}`, CreatedAt: now.Add(-1 * time.Minute)},
		{Type: EventQuotaReset, Payload: `{}`, CreatedAt: now},
	} {
		if _, err := db.AddEvent(item); err != nil {
			t.Fatalf("AddEvent: %v", err)
		}
	}

	events, err := db.ListEvents(2)
	if err != nil {
		t.Fatalf("ListEvents: %v", err)
	}
	if len(events) != 2 {
		t.Fatalf("events length = %d, want 2", len(events))
	}
	if events[0].Type != EventQuotaReset || events[1].Type != EventLimitWarn {
		t.Fatalf("events order = %q, %q; want newest first", events[0].Type, events[1].Type)
	}
}

func TestQuotaWindowUpsertReplacesExistingWindow(t *testing.T) {
	db := openTestStore(t)
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)
	first := 40
	second := 55

	if err := db.UpsertProvider(Provider{ID: "codex", Name: "Codex", Available: true}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}
	if err := db.UpsertQuotaWindow(QuotaWindow{
		ProviderID:  "codex",
		WindowType:  WindowTypeWeekly,
		PercentLeft: &first,
		Source:      SourceCLI,
		UpdatedAt:   now,
	}); err != nil {
		t.Fatalf("first UpsertQuotaWindow: %v", err)
	}
	if err := db.UpsertQuotaWindow(QuotaWindow{
		ProviderID:  "codex",
		WindowType:  WindowTypeWeekly,
		PercentLeft: &second,
		Source:      SourceEndpoint,
		UpdatedAt:   now.Add(time.Minute),
	}); err != nil {
		t.Fatalf("second UpsertQuotaWindow: %v", err)
	}

	windows, err := db.ListQuotaWindows("codex")
	if err != nil {
		t.Fatalf("ListQuotaWindows: %v", err)
	}
	if len(windows) != 1 {
		t.Fatalf("windows length = %d, want 1", len(windows))
	}
	assertIntPtr(t, "PercentLeft", windows[0].PercentLeft, second)
	if windows[0].Source != SourceEndpoint {
		t.Fatalf("Source = %q, want %q", windows[0].Source, SourceEndpoint)
	}
}

func TestListProvidersReturnsStableOrder(t *testing.T) {
	db := openTestStore(t)

	if err := db.UpsertProvider(Provider{ID: ProviderCodex, Name: "Codex", Available: true}); err != nil {
		t.Fatalf("UpsertProvider codex: %v", err)
	}
	if err := db.UpsertProvider(Provider{ID: ProviderClaude, Name: "Claude", Available: true}); err != nil {
		t.Fatalf("UpsertProvider claude: %v", err)
	}

	providers, err := db.ListProviders()
	if err != nil {
		t.Fatalf("ListProviders: %v", err)
	}
	if len(providers) != 2 {
		t.Fatalf("providers length = %d, want 2", len(providers))
	}
	if providers[0].ID != ProviderClaude || providers[1].ID != ProviderCodex {
		t.Fatalf("provider order = %q, %q; want claude, codex", providers[0].ID, providers[1].ID)
	}
}

func TestGetCodingSessionReturnsStoredSession(t *testing.T) {
	db := openTestStore(t)
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)

	if err := db.UpsertProvider(Provider{ID: ProviderClaude, Name: "Claude", Available: true}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}
	if err := db.UpsertCodingSession(CodingSession{
		ID:             "session-1",
		ProviderID:     ProviderClaude,
		ProjectPath:    "/work/codegauge",
		State:          SessionStateWaiting,
		StartedAt:      now.Add(-10 * time.Minute),
		LastActivityAt: now,
		LastEventType:  EventSessionWaiting,
	}); err != nil {
		t.Fatalf("UpsertCodingSession: %v", err)
	}

	session, err := db.GetCodingSession("session-1")
	if err != nil {
		t.Fatalf("GetCodingSession: %v", err)
	}
	if session.ID != "session-1" || session.State != SessionStateWaiting || session.ProjectPath != "/work/codegauge" {
		t.Fatalf("session = %+v, want stored waiting session", session)
	}
	if !session.StartedAt.Equal(now.Add(-10 * time.Minute)) {
		t.Fatalf("StartedAt = %s, want preserved start", session.StartedAt.Format(time.RFC3339))
	}
}

func TestGetDevicePairingByToken(t *testing.T) {
	db := openTestStore(t)
	now := time.Date(2026, 6, 5, 12, 30, 0, 0, time.UTC)

	if err := db.UpsertDevicePairing(DevicePairing{
		DeviceID:   "phone-1",
		Name:       "Pixel",
		Token:      "token-1",
		PairedAt:   now,
		LastSeenAt: now,
	}); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}

	device, err := db.GetDevicePairingByToken("token-1")
	if err != nil {
		t.Fatalf("GetDevicePairingByToken: %v", err)
	}
	if device.DeviceID != "phone-1" || device.Name != "Pixel" {
		t.Fatalf("device = %+v, want phone-1 Pixel", device)
	}
}

func openTestStore(t *testing.T) *Store {
	t.Helper()

	db, err := Open(filepath.Join(t.TempDir(), "codegauge.db"))
	if err != nil {
		t.Fatalf("Open: %v", err)
	}
	t.Cleanup(func() {
		if err := db.Close(); err != nil {
			t.Fatalf("Close: %v", err)
		}
	})

	return db
}

func assertIntPtr(t *testing.T, name string, got *int, want int) {
	t.Helper()
	if got == nil {
		t.Fatalf("%s = nil, want %d", name, want)
	}
	if *got != want {
		t.Fatalf("%s = %d, want %d", name, *got, want)
	}
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

func assertTimePtr(t *testing.T, name string, got *time.Time, want time.Time) {
	t.Helper()
	if got == nil {
		t.Fatalf("%s = nil, want %s", name, want.Format(time.RFC3339))
	}
	if !got.Equal(want) {
		t.Fatalf("%s = %s, want %s", name, got.Format(time.RFC3339), want.Format(time.RFC3339))
	}
}
