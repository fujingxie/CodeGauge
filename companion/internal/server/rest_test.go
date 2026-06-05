package server

import (
	"bytes"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"testing"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
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
	return NewRouter(Options{
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
