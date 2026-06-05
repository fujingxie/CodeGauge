package server

import (
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestHealthReturnsStatus(t *testing.T) {
	router := NewRouter("test-version")
	req := httptest.NewRequest(http.MethodGet, "/api/v1/health", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusOK)
	}

	var body HealthResponse
	if err := json.NewDecoder(rec.Body).Decode(&body); err != nil {
		t.Fatalf("decode health response: %v", err)
	}

	if !body.OK {
		t.Fatal("OK = false, want true")
	}
	if body.Version != "test-version" {
		t.Fatalf("Version = %q, want test-version", body.Version)
	}
}

func TestUnknownRouteReturnsNotFound(t *testing.T) {
	router := NewRouter("test-version")
	req := httptest.NewRequest(http.MethodGet, "/api/v1/missing", nil)
	rec := httptest.NewRecorder()

	router.ServeHTTP(rec, req)

	if rec.Code != http.StatusNotFound {
		t.Fatalf("status = %d, want %d", rec.Code, http.StatusNotFound)
	}
}
