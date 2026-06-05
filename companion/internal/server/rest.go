package server

import (
	"encoding/json"
	"log"
	"net/http"
)

type HealthResponse struct {
	OK      bool   `json:"ok"`
	Version string `json:"version"`
}

func NewRouter(version string) http.Handler {
	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/health", healthHandler(version))
	return mux
}

func healthHandler(version string) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Method != http.MethodGet {
			w.Header().Set("Allow", http.MethodGet)
			http.Error(w, "method not allowed", http.StatusMethodNotAllowed)
			return
		}

		writeJSON(w, HealthResponse{
			OK:      true,
			Version: version,
		})
	}
}

func writeJSON(w http.ResponseWriter, value any) {
	w.Header().Set("Content-Type", "application/json")
	if err := json.NewEncoder(w).Encode(value); err != nil {
		log.Printf("write json response: %v", err)
	}
}
