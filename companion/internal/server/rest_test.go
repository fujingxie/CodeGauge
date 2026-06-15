package server

import (
	"bytes"
	"encoding/json"
	"fmt"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gorilla/websocket"
	"github.com/xiexiansheng/codegauge/companion/internal/store"
	"github.com/xiexiansheng/codegauge/companion/internal/stream"
)

func TestHealthReturnsStatusWithoutAuth(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	var body HealthResponse
	decodeJSON(t, rec, &body)

	if !body.OK {
		t.Fatal("OK = false, want true")
	}
	if body.Version != "test-version" {
		t.Fatalf("Version = %q, want test-version", body.Version)
	}
}

func TestStatusRequiresBearerToken(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/status", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestPairIssuesTokenAndStatusUsesIt(t *testing.T) {
	db := openServerTestStore(t)
	seedStatusData(t, db)
	router := newTestRouterWithStore(t, db)

	pairReq := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/pair",
		bytes.NewBufferString(`{"pair_code":"123456","device_name":"Pixel"}`),
	)
	pairReq.Header.Set("Content-Type", "application/json")
	pairRec := httptest.NewRecorder()

	router.ServeHTTP(pairRec, pairReq)

	if pairRec.Code != http.StatusOK {
		t.Fatalf("pair status = %d, want %d; body=%s", pairRec.Code, http.StatusOK, pairRec.Body.String())
	}

	var pairBody PairResponse
	decodeJSON(t, pairRec, &pairBody)
	if pairBody.Token != "token-test" {
		t.Fatalf("Token = %q, want token-test", pairBody.Token)
	}
	if pairBody.ServerName != "CodeGauge Test" {
		t.Fatalf("ServerName = %q, want CodeGauge Test", pairBody.ServerName)
	}

	device, err := db.GetDevicePairingByToken("token-test")
	if err != nil {
		t.Fatalf("GetDevicePairingByToken: %v", err)
	}
	if device.Name != "Pixel" {
		t.Fatalf("device.Name = %q, want Pixel", device.Name)
	}

	statusReq := httptest.NewRequest(http.MethodGet, "/api/v1/status", nil)
	statusReq.Header.Set("Authorization", "Bearer token-test")
	statusRec := httptest.NewRecorder()

	router.ServeHTTP(statusRec, statusReq)

	if statusRec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", statusRec.Code, http.StatusOK, statusRec.Body.String())
	}

	var statusBody StatusResponse
	decodeJSON(t, statusRec, &statusBody)
	if len(statusBody.Providers) != 1 {
		t.Fatalf("providers length = %d, want 1", len(statusBody.Providers))
	}
	if statusBody.Providers[0].ID != store.ProviderClaude {
		t.Fatalf("provider id = %q, want claude", statusBody.Providers[0].ID)
	}
	if len(statusBody.Providers[0].Windows) != 1 {
		t.Fatalf("windows length = %d, want 1", len(statusBody.Providers[0].Windows))
	}
	if statusBody.Providers[0].Windows[0].WindowType != store.WindowTypeFiveHours {
		t.Fatalf("window_type = %q, want 5h", statusBody.Providers[0].Windows[0].WindowType)
	}
	if len(statusBody.Sessions) != 1 {
		t.Fatalf("sessions length = %d, want 1", len(statusBody.Sessions))
	}
}

func TestQuotaUsesBearerToken(t *testing.T) {
	db := openServerTestStore(t)
	seedStatusData(t, db)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/quota", nil)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}

	var body QuotaResponse
	decodeJSON(t, rec, &body)
	if len(body.Providers) != 1 {
		t.Fatalf("providers length = %d, want 1", len(body.Providers))
	}
	if len(body.Providers[0].Windows) != 1 {
		t.Fatalf("windows length = %d, want 1", len(body.Providers[0].Windows))
	}
}

func TestEventsUsesBearerTokenAndHonorsLimit(t *testing.T) {
	db := openServerTestStore(t)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	if err := db.UpsertProvider(store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}
	providerID := store.ProviderClaude
	for _, event := range []store.Event{
		{ProviderID: &providerID, Type: store.EventSessionStart, Payload: `{"session_id":"first"}`, CreatedAt: testNow().Add(-2 * time.Minute)},
		{ProviderID: &providerID, Type: store.EventSessionWaiting, Payload: `{"session_id":"second"}`, CreatedAt: testNow().Add(-time.Minute)},
		{ProviderID: &providerID, Type: store.EventSessionDone, Payload: `{"session_id":"third"}`, CreatedAt: testNow()},
	} {
		if _, err := db.AddEvent(event); err != nil {
			t.Fatalf("AddEvent: %v", err)
		}
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/events?limit=2", nil)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}

	var body EventsResponse
	decodeJSON(t, rec, &body)
	if len(body.Events) != 2 {
		t.Fatalf("events length = %d, want 2", len(body.Events))
	}
	if body.Events[0].Type != store.EventSessionDone || body.Events[1].Type != store.EventSessionWaiting {
		t.Fatalf("event order = %q, %q; want newest first", body.Events[0].Type, body.Events[1].Type)
	}
	if body.Events[0].ProviderID == nil || *body.Events[0].ProviderID != store.ProviderClaude {
		t.Fatalf("provider_id = %v, want claude", body.Events[0].ProviderID)
	}
}

func TestEventsRequiresBearerToken(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/events", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestSettingsReadsDefaultsAndPersistsUpdates(t *testing.T) {
	db := openServerTestStore(t)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	router := newTestRouterWithOptions(t, Options{
		Version:    "test-version",
		ServerName: "CodeGauge Test",
		PairCode:   "123456",
		Store:      db,
		Now:        testNow,
		SettingsDefaults: SettingsDefaults{
			CollectIntervalSeconds: 30,
			WarningThreshold:       75,
			CriticalThreshold:      90,
		},
		TokenGenerator: func() (string, error) { return "token-test", nil },
		DeviceIDGenerator: func() (string, error) {
			return "device-test", nil
		},
	})

	getReq := httptest.NewRequest(http.MethodGet, "/api/v1/settings", nil)
	getReq.Header.Set("Authorization", "Bearer token-test")
	getRec := httptest.NewRecorder()
	router.ServeHTTP(getRec, getReq)

	if getRec.Code != http.StatusOK {
		t.Fatalf("get status = %d, want %d; body=%s", getRec.Code, http.StatusOK, getRec.Body.String())
	}
	var defaults SettingsResponse
	decodeJSON(t, getRec, &defaults)
	if defaults.Settings.WarningThreshold != 75 || defaults.Settings.CriticalThreshold != 90 {
		t.Fatalf("default thresholds = %d/%d, want 75/90", defaults.Settings.WarningThreshold, defaults.Settings.CriticalThreshold)
	}
	if defaults.Settings.CollectIntervalSeconds != 30 {
		t.Fatalf("default collect interval = %d, want 30", defaults.Settings.CollectIntervalSeconds)
	}
	if defaults.Settings.DashboardPrimaryWindow != "5h" {
		t.Fatalf("default dashboard primary window = %q, want 5h", defaults.Settings.DashboardPrimaryWindow)
	}
	if !defaults.Settings.NotificationsEnabled || !defaults.Settings.TaskDoneNotifications {
		t.Fatalf("default notifications = %+v, want enabled", defaults.Settings)
	}

	patchReq := httptest.NewRequest(
		http.MethodPatch,
		"/api/v1/settings",
		bytes.NewBufferString(`{
			"settings":{
				"warning_threshold":70,
				"critical_threshold":92,
				"notifications_enabled":false,
				"collect_interval_seconds":45,
				"dashboard_primary_window":"weekly"
			}
		}`),
	)
	patchReq.Header.Set("Authorization", "Bearer token-test")
	patchRec := httptest.NewRecorder()
	router.ServeHTTP(patchRec, patchReq)

	if patchRec.Code != http.StatusOK {
		t.Fatalf("patch status = %d, want %d; body=%s", patchRec.Code, http.StatusOK, patchRec.Body.String())
	}
	var updated SettingsResponse
	decodeJSON(t, patchRec, &updated)
	if updated.Settings.WarningThreshold != 70 || updated.Settings.CriticalThreshold != 92 {
		t.Fatalf("updated thresholds = %d/%d, want 70/92", updated.Settings.WarningThreshold, updated.Settings.CriticalThreshold)
	}
	if updated.Settings.NotificationsEnabled {
		t.Fatal("NotificationsEnabled = true, want false")
	}
	if updated.Settings.CollectIntervalSeconds != 45 {
		t.Fatalf("CollectIntervalSeconds = %d, want 45", updated.Settings.CollectIntervalSeconds)
	}
	if updated.Settings.DashboardPrimaryWindow != "weekly" {
		t.Fatalf("DashboardPrimaryWindow = %q, want weekly", updated.Settings.DashboardPrimaryWindow)
	}

	setting, err := db.GetSetting("warning_threshold")
	if err != nil {
		t.Fatalf("GetSetting warning_threshold: %v", err)
	}
	if setting.Value != "70" {
		t.Fatalf("stored warning_threshold = %q, want 70", setting.Value)
	}
	setting, err = db.GetSetting("dashboard_primary_window")
	if err != nil {
		t.Fatalf("GetSetting dashboard_primary_window: %v", err)
	}
	if setting.Value != "weekly" {
		t.Fatalf("stored dashboard_primary_window = %q, want weekly", setting.Value)
	}
}

func TestSettingsRejectsInvalidThresholds(t *testing.T) {
	db := openServerTestStore(t)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(
		http.MethodPatch,
		"/api/v1/settings",
		bytes.NewBufferString(`{"settings":{"warning_threshold":95,"critical_threshold":90}}`),
	)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusBadRequest)
	}
}

func TestSettingsRejectsInvalidDashboardPrimaryWindow(t *testing.T) {
	db := openServerTestStore(t)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(
		http.MethodPatch,
		"/api/v1/settings",
		bytes.NewBufferString(`{"settings":{"dashboard_primary_window":"daily"}}`),
	)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusBadRequest {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusBadRequest, rec.Body.String())
	}
}

func TestDevicesUsesBearerTokenAndDoesNotExposeTokens(t *testing.T) {
	db := openServerTestStore(t)
	now := testNow()
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", now.Add(-time.Hour))); err != nil {
		t.Fatalf("UpsertDevicePairing phone-1: %v", err)
	}
	if err := db.UpsertDevicePairing(DevicePairing("phone-2", "Tablet", "token-tablet", now)); err != nil {
		t.Fatalf("UpsertDevicePairing phone-2: %v", err)
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/devices", nil)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}
	if strings.Contains(rec.Body.String(), "token-test") || strings.Contains(rec.Body.String(), "token-tablet") {
		t.Fatalf("response exposes token: %s", rec.Body.String())
	}

	var body DevicesResponse
	decodeJSON(t, rec, &body)
	if len(body.Devices) != 2 {
		t.Fatalf("devices length = %d, want 2", len(body.Devices))
	}
	if body.Devices[0].DeviceID != "phone-2" || body.Devices[1].DeviceID != "phone-1" {
		t.Fatalf("device order = %q, %q; want latest first", body.Devices[0].DeviceID, body.Devices[1].DeviceID)
	}
}

func TestDiagnosticsSummarizesCompanionState(t *testing.T) {
	db := openServerTestStore(t)
	seedStatusData(t, db)
	if err := db.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}
	providerID := store.ProviderClaude
	if _, err := db.AddEvent(store.Event{
		ProviderID: &providerID,
		Type:       store.EventSessionStart,
		Payload:    `{"session_id":"session-1"}`,
		CreatedAt:  testNow().Add(time.Minute),
	}); err != nil {
		t.Fatalf("AddEvent: %v", err)
	}
	router := newTestRouterWithStore(t, db)

	req := httptest.NewRequest(http.MethodGet, "/api/v1/diagnostics", nil)
	req.Header.Set("Authorization", "Bearer token-test")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}

	var body DiagnosticsResponse
	decodeJSON(t, rec, &body)
	if !body.OK {
		t.Fatal("OK = false, want true")
	}
	if body.ServerName != "CodeGauge Test" || body.Version != "test-version" {
		t.Fatalf("server identity = %q/%q, want CodeGauge Test/test-version", body.ServerName, body.Version)
	}
	if body.ProviderCount != 1 || body.AvailableProviderCount != 1 {
		t.Fatalf("provider counts = %d/%d, want 1/1", body.ProviderCount, body.AvailableProviderCount)
	}
	if body.RunningSessionCount != 1 {
		t.Fatalf("RunningSessionCount = %d, want 1", body.RunningSessionCount)
	}
	if body.PairedDeviceCount != 1 {
		t.Fatalf("PairedDeviceCount = %d, want 1", body.PairedDeviceCount)
	}
	if body.LatestEventAt == nil || !body.LatestEventAt.Equal(testNow().Add(time.Minute)) {
		t.Fatalf("LatestEventAt = %v, want latest event time", body.LatestEventAt)
	}
}

func TestPairRejectsWrongCode(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/pair",
		bytes.NewBufferString(`{"pair_code":"000000","device_name":"Pixel"}`),
	)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestPairRejectsExpiredPairCode(t *testing.T) {
	now := testNow()
	router := newTestRouterWithOptions(t, Options{
		Version:             "test-version",
		ServerName:          "CodeGauge Test",
		PairCode:            "123456",
		Store:               openServerTestStore(t),
		Now:                 func() time.Time { return now },
		PairCodeTTL:         time.Minute,
		PairCodeMaxAttempts: 5,
		TokenGenerator:      func() (string, error) { return "token-test", nil },
		DeviceIDGenerator: func() (string, error) {
			return "device-test", nil
		},
	})
	now = now.Add(2 * time.Minute)

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/pair",
		bytes.NewBufferString(`{"pair_code":"123456","device_name":"Pixel"}`),
	)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusUnauthorized)
	}
}

func TestPairLimitsWrongCodeAttempts(t *testing.T) {
	router := newTestRouterWithOptions(t, Options{
		Version:             "test-version",
		ServerName:          "CodeGauge Test",
		PairCode:            "123456",
		Store:               openServerTestStore(t),
		Now:                 testNow,
		PairCodeTTL:         10 * time.Minute,
		PairCodeMaxAttempts: 2,
		TokenGenerator:      func() (string, error) { return "token-test", nil },
		DeviceIDGenerator: func() (string, error) {
			return "device-test", nil
		},
	})

	for i := 0; i < 2; i++ {
		req := httptest.NewRequest(
			http.MethodPost,
			"/api/v1/pair",
			bytes.NewBufferString(`{"pair_code":"000000","device_name":"Pixel"}`),
		)
		rec := httptest.NewRecorder()
		router.ServeHTTP(rec, req)
		if rec.Code != http.StatusUnauthorized && rec.Code != http.StatusTooManyRequests {
			t.Fatalf("wrong code attempt %d status = %d, want unauthorized or too many requests", i+1, rec.Code)
		}
	}

	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/pair",
		bytes.NewBufferString(`{"pair_code":"123456","device_name":"Pixel"}`),
	)
	rec := httptest.NewRecorder()
	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusTooManyRequests {
		t.Fatalf("status = %d, want %d after too many wrong attempts", rec.Code, http.StatusTooManyRequests)
	}
}

func TestClaudeHookUpdatesSessionFromLoopback(t *testing.T) {
	db := openServerTestStore(t)
	router := newTestRouterWithStore(t, db)
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/hooks/claude",
		bytes.NewBufferString(`{
			"session_id":"manual-stop",
			"hook_event_name":"Stop",
			"cwd":"/work/codegauge",
			"transcript_path":"/tmp/manual-stop.jsonl",
			"last_assistant_message":"done"
		}`),
	)
	req.RemoteAddr = "127.0.0.1:51234"
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d; body=%s", rec.Code, http.StatusOK, rec.Body.String())
	}

	session, err := db.GetCodingSession("manual-stop")
	if err != nil {
		t.Fatalf("GetCodingSession: %v", err)
	}
	if session.ProviderID != store.ProviderClaude || session.State != store.SessionStateDone {
		t.Fatalf("session = %+v, want claude done", session)
	}

	events, err := db.ListEvents(10)
	if err != nil {
		t.Fatalf("ListEvents: %v", err)
	}
	if len(events) != 1 || events[0].Type != store.EventSessionDone {
		t.Fatalf("events = %+v, want one session_done event", events)
	}
}

func TestClaudeHookRejectsNonLoopback(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(
		http.MethodPost,
		"/api/v1/hooks/claude",
		bytes.NewBufferString(`{"session_id":"remote","hook_event_name":"Stop"}`),
	)
	req.RemoteAddr = "192.168.1.20:51234"
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusForbidden {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusForbidden)
	}
}

func TestStreamRequiresBearerToken(t *testing.T) {
	server := httptest.NewServer(newTestRouter(t))
	defer server.Close()

	_, response, err := websocket.DefaultDialer.Dial(webSocketURL(server.URL, "/api/v1/stream"), nil)
	if err == nil {
		t.Fatal("Dial error = nil, want unauthorized handshake failure")
	}
	if response == nil {
		t.Fatal("response = nil, want HTTP response")
	}
	if response.StatusCode != http.StatusUnauthorized {
		t.Fatalf("status = %d, want %d", response.StatusCode, http.StatusUnauthorized)
	}
}

func TestStreamReceivesSessionUpdateFromClaudeHook(t *testing.T) {
	db := openServerTestStore(t)
	hub := stream.NewHub()
	notifyingStore := stream.NewNotifyingStore(db, hub, stream.Options{
		WarningThreshold:  80,
		CriticalThreshold: 95,
	})
	if err := notifyingStore.UpsertDevicePairing(DevicePairing("phone-1", "Pixel", "token-test", testNow())); err != nil {
		t.Fatalf("UpsertDevicePairing: %v", err)
	}

	server := httptest.NewServer(newTestRouterWithOptions(t, Options{
		Version:        "test-version",
		ServerName:     "CodeGauge Test",
		PairCode:       "123456",
		Store:          notifyingStore,
		Now:            testNow,
		StreamHub:      hub,
		TokenGenerator: func() (string, error) { return "token-test", nil },
		DeviceIDGenerator: func() (string, error) {
			return "device-test", nil
		},
	}))
	defer server.Close()

	header := http.Header{}
	header.Set("Authorization", "Bearer token-test")
	conn, response, err := websocket.DefaultDialer.Dial(webSocketURL(server.URL, "/api/v1/stream"), header)
	if err != nil {
		status := 0
		if response != nil {
			status = response.StatusCode
		}
		t.Fatalf("Dial status=%d error=%v", status, err)
	}
	defer conn.Close()

	hookResponse, err := http.Post(
		server.URL+"/api/v1/hooks/claude",
		"application/json",
		bytes.NewBufferString(`{
			"session_id":"manual-stop",
			"hook_event_name":"Stop",
			"cwd":"/work/codegauge",
			"transcript_path":"/tmp/manual-stop.jsonl",
			"last_assistant_message":"done"
		}`),
	)
	if err != nil {
		t.Fatalf("POST hook: %v", err)
	}
	defer hookResponse.Body.Close()
	if hookResponse.StatusCode != http.StatusOK {
		t.Fatalf("hook status = %d, want %d", hookResponse.StatusCode, http.StatusOK)
	}

	if err := conn.SetReadDeadline(time.Now().Add(time.Second)); err != nil {
		t.Fatalf("SetReadDeadline: %v", err)
	}
	var message struct {
		EventType string         `json:"event_type"`
		Data      map[string]any `json:"data"`
	}
	readStreamMessage(t, conn, &message)
	if message.EventType != stream.EventTypeSessionUpdate {
		t.Fatalf("event_type = %q, want %q", message.EventType, stream.EventTypeSessionUpdate)
	}
	if message.Data["provider_id"] != store.ProviderClaude {
		t.Fatalf("provider_id = %v, want claude", message.Data["provider_id"])
	}
	if message.Data["project_path"] != "/work/codegauge" || message.Data["state"] != store.SessionStateDone {
		t.Fatalf("data = %+v, want /work/codegauge done", message.Data)
	}

	var eventMessage struct {
		EventType string         `json:"event_type"`
		Data      map[string]any `json:"data"`
	}
	readStreamMessage(t, conn, &eventMessage)
	if eventMessage.EventType != stream.EventTypeEventUpdate {
		t.Fatalf("event_type = %q, want %q", eventMessage.EventType, stream.EventTypeEventUpdate)
	}
	if eventMessage.Data["type"] != store.EventSessionDone || eventMessage.Data["provider_id"] != store.ProviderClaude {
		t.Fatalf("event data = %+v, want claude session_done", eventMessage.Data)
	}
	if !strings.Contains(fmt.Sprint(eventMessage.Data["payload"]), `"hook_event_name":"Stop"`) {
		t.Fatalf("payload = %v, want Stop hook payload", eventMessage.Data["payload"])
	}
}

func TestUnknownRouteReturnsNotFound(t *testing.T) {
	router := newTestRouter(t)
	req := httptest.NewRequest(http.MethodGet, "/api/v1/missing", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}

func newTestRouter(t *testing.T) http.Handler {
	t.Helper()
	return newTestRouterWithStore(t, openServerTestStore(t))
}

func newTestRouterWithStore(t *testing.T, db *store.Store) http.Handler {
	t.Helper()
	return newTestRouterWithOptions(t, Options{
		Version:        "test-version",
		ServerName:     "CodeGauge Test",
		PairCode:       "123456",
		Store:          db,
		Now:            testNow,
		TokenGenerator: func() (string, error) { return "token-test", nil },
		DeviceIDGenerator: func() (string, error) {
			return "device-test", nil
		},
	})
}

func newTestRouterWithOptions(t *testing.T, options Options) http.Handler {
	t.Helper()
	return NewRouter(options)
}

func openServerTestStore(t *testing.T) *store.Store {
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

func seedStatusData(t *testing.T, db *store.Store) {
	t.Helper()
	now := testNow()
	used := int64(1234)
	resetAt := now.Add(2 * time.Hour)

	if err := db.UpsertProvider(store.Provider{
		ID:        store.ProviderClaude,
		Name:      "Claude",
		PlanTier:  "Max",
		Available: true,
	}); err != nil {
		t.Fatalf("UpsertProvider: %v", err)
	}
	if err := db.UpsertQuotaWindow(store.QuotaWindow{
		ProviderID: store.ProviderClaude,
		WindowType: store.WindowTypeFiveHours,
		Used:       &used,
		ResetsAt:   &resetAt,
		Source:     store.SourceCCUsage,
		UpdatedAt:  now,
	}); err != nil {
		t.Fatalf("UpsertQuotaWindow: %v", err)
	}
	if err := db.UpsertCodingSession(store.CodingSession{
		ID:             "session-1",
		ProviderID:     store.ProviderClaude,
		ProjectPath:    "/work/codegauge",
		State:          store.SessionStateRunning,
		StartedAt:      now.Add(-10 * time.Minute),
		LastActivityAt: now,
		LastEventType:  store.EventSessionStart,
	}); err != nil {
		t.Fatalf("UpsertCodingSession: %v", err)
	}
}

func DevicePairing(deviceID string, name string, token string, now time.Time) store.DevicePairing {
	return store.DevicePairing{
		DeviceID:   deviceID,
		Name:       name,
		Token:      token,
		PairedAt:   now,
		LastSeenAt: now,
	}
}

func testNow() time.Time {
	return time.Date(2026, 6, 5, 12, 0, 0, 0, time.UTC)
}

func decodeJSON(t *testing.T, rec *httptest.ResponseRecorder, target any) {
	t.Helper()
	if err := json.NewDecoder(rec.Body).Decode(target); err != nil {
		t.Fatalf("decode response %q: %v", rec.Body.String(), err)
	}
}

func webSocketURL(serverURL string, path string) string {
	return fmt.Sprintf("ws%s%s", strings.TrimPrefix(serverURL, "http"), path)
}

func readStreamMessage(t *testing.T, conn *websocket.Conn, target any) {
	t.Helper()
	if err := conn.ReadJSON(target); err != nil {
		t.Fatalf("ReadJSON: %v", err)
	}
}
