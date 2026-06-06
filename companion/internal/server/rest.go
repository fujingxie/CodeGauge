package server

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"strings"
	"time"

	"github.com/gorilla/websocket"
	"github.com/xiexiansheng/codegauge/companion/internal/hooks"
	"github.com/xiexiansheng/codegauge/companion/internal/store"
	codestream "github.com/xiexiansheng/codegauge/companion/internal/stream"
)

type Store interface {
	UpsertProvider(provider store.Provider) error
	ListProviders() ([]store.Provider, error)
	ListQuotaWindows(providerID string) ([]store.QuotaWindow, error)
	UpsertCodingSession(session store.CodingSession) error
	GetCodingSession(id string) (store.CodingSession, error)
	ListCodingSessions() ([]store.CodingSession, error)
	AddEvent(event store.Event) (int64, error)
	UpsertDevicePairing(device store.DevicePairing) error
	GetDevicePairingByToken(token string) (store.DevicePairing, error)
}

type TokenGenerator func() (string, error)

type Options struct {
	Version           string
	ServerName        string
	PairCode          string
	Store             Store
	Now               func() time.Time
	StreamHub         *codestream.Hub
	TokenGenerator    TokenGenerator
	DeviceIDGenerator TokenGenerator
}

type Router struct {
	version           string
	serverName        string
	pairCode          string
	store             Store
	now               func() time.Time
	streamHub         *codestream.Hub
	tokenGenerator    TokenGenerator
	deviceIDGenerator TokenGenerator
}

type HealthResponse struct {
	OK      bool   `json:"ok"`
	Version string `json:"version"`
}

type StatusResponse struct {
	Providers  []ProviderResponse `json:"providers"`
	Sessions   []SessionResponse  `json:"sessions"`
	ServerTime time.Time          `json:"server_time"`
}

type QuotaResponse struct {
	Providers []ProviderResponse `json:"providers"`
}

type ProviderResponse struct {
	ID        string                `json:"id"`
	Name      string                `json:"name"`
	PlanTier  string                `json:"plan_tier"`
	Available bool                  `json:"available"`
	Windows   []QuotaWindowResponse `json:"windows"`
}

type QuotaWindowResponse struct {
	WindowType  string     `json:"window_type"`
	PercentLeft *int       `json:"percent_left"`
	Used        *int64     `json:"used"`
	Limit       *int64     `json:"limit"`
	ResetsAt    *time.Time `json:"resets_at"`
	Source      string     `json:"source"`
	UpdatedAt   time.Time  `json:"updated_at"`
}

type SessionResponse struct {
	ProviderID     string    `json:"provider_id"`
	ProjectPath    string    `json:"project_path"`
	State          string    `json:"state"`
	LastActivityAt time.Time `json:"last_activity_at"`
}

type PairRequest struct {
	PairCode   string `json:"pair_code"`
	DeviceName string `json:"device_name"`
}

type PairResponse struct {
	Token      string `json:"token"`
	ServerName string `json:"server_name"`
}

type errorResponse struct {
	Error string `json:"error"`
}

func NewRouter(options Options) http.Handler {
	router := &Router{
		version:           withDefault(options.Version, "dev"),
		serverName:        withDefault(options.ServerName, "CodeGauge"),
		pairCode:          options.PairCode,
		store:             options.Store,
		now:               options.Now,
		streamHub:         options.StreamHub,
		tokenGenerator:    options.TokenGenerator,
		deviceIDGenerator: options.DeviceIDGenerator,
	}
	if router.now == nil {
		router.now = time.Now
	}
	if router.streamHub == nil {
		router.streamHub = codestream.NewHub()
	}
	if router.tokenGenerator == nil {
		router.tokenGenerator = func() (string, error) {
			return randomURLToken(32)
		}
	}
	if router.deviceIDGenerator == nil {
		router.deviceIDGenerator = func() (string, error) {
			token, err := randomURLToken(16)
			if err != nil {
				return "", err
			}
			return "device_" + token, nil
		}
	}

	mux := http.NewServeMux()
	mux.HandleFunc("/api/v1/health", router.health)
	mux.HandleFunc("/api/v1/pair", router.pair)
	mux.HandleFunc("/api/v1/status", router.withAuth(router.status))
	mux.HandleFunc("/api/v1/quota", router.withAuth(router.quota))
	mux.HandleFunc("/api/v1/stream", router.withAuth(router.stream))
	mux.HandleFunc("/api/v1/hooks/claude", router.claudeHook)
	return mux
}

func (r *Router) health(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	writeJSON(w, http.StatusOK, HealthResponse{
		OK:      true,
		Version: r.version,
	})
}

func (r *Router) pair(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodPost) {
		return
	}
	if r.store == nil {
		writeError(w, http.StatusInternalServerError, "store is not configured")
		return
	}

	var body PairRequest
	if err := json.NewDecoder(req.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}
	if body.DeviceName == "" {
		writeError(w, http.StatusBadRequest, "device_name is required")
		return
	}
	if !secureEqual(body.PairCode, r.pairCode) {
		writeError(w, http.StatusUnauthorized, "invalid pair code")
		return
	}

	token, err := r.tokenGenerator()
	if err != nil {
		log.Printf("generate pairing token: %v", err)
		writeError(w, http.StatusInternalServerError, "could not generate token")
		return
	}
	deviceID, err := r.deviceIDGenerator()
	if err != nil {
		log.Printf("generate device id: %v", err)
		writeError(w, http.StatusInternalServerError, "could not generate device id")
		return
	}

	now := r.now().UTC()
	if err := r.store.UpsertDevicePairing(store.DevicePairing{
		DeviceID:   deviceID,
		Name:       body.DeviceName,
		Token:      token,
		PairedAt:   now,
		LastSeenAt: now,
	}); err != nil {
		log.Printf("store device pairing: %v", err)
		writeError(w, http.StatusInternalServerError, "could not store pairing")
		return
	}

	writeJSON(w, http.StatusOK, PairResponse{
		Token:      token,
		ServerName: r.serverName,
	})
}

func (r *Router) status(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	providers, err := r.providerResponses()
	if err != nil {
		log.Printf("build providers status: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read providers")
		return
	}
	sessions, err := r.sessionResponses()
	if err != nil {
		log.Printf("build sessions status: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read sessions")
		return
	}

	writeJSON(w, http.StatusOK, StatusResponse{
		Providers:  providers,
		Sessions:   sessions,
		ServerTime: r.now().UTC(),
	})
}

func (r *Router) quota(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	providers, err := r.providerResponses()
	if err != nil {
		log.Printf("build quota status: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read quota")
		return
	}

	writeJSON(w, http.StatusOK, QuotaResponse{
		Providers: providers,
	})
}

func (r *Router) claudeHook(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodPost) {
		return
	}
	if !isLoopbackRequest(req) {
		writeError(w, http.StatusForbidden, "hook endpoint only accepts loopback requests")
		return
	}
	if r.store == nil {
		writeError(w, http.StatusInternalServerError, "store is not configured")
		return
	}

	body, err := io.ReadAll(http.MaxBytesReader(w, req.Body, 1<<20))
	if err != nil {
		writeError(w, http.StatusBadRequest, "invalid hook body")
		return
	}

	receiver := hooks.NewClaudeReceiver(r.store)
	if err := receiver.Handle(req.Context(), body, r.now().UTC()); err != nil {
		log.Printf("handle Claude hook: %v", err)
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}

	writeJSON(w, http.StatusOK, map[string]bool{"ok": true})
}

func (r *Router) stream(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	upgrader := websocket.Upgrader{
		CheckOrigin: func(*http.Request) bool {
			return true
		},
	}
	conn, err := upgrader.Upgrade(w, req, nil)
	if err != nil {
		log.Printf("upgrade stream websocket: %v", err)
		return
	}
	defer conn.Close()

	subscription := r.streamHub.Subscribe()
	defer subscription.Close()

	for {
		select {
		case <-req.Context().Done():
			return
		case message := <-subscription.Messages():
			if err := conn.WriteJSON(message); err != nil {
				log.Printf("write stream message: %v", err)
				return
			}
		}
	}
}

func (r *Router) withAuth(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, req *http.Request) {
		if r.store == nil {
			writeError(w, http.StatusInternalServerError, "store is not configured")
			return
		}

		token, ok := bearerToken(req.Header.Get("Authorization"))
		if !ok {
			writeError(w, http.StatusUnauthorized, "missing bearer token")
			return
		}
		if _, err := r.store.GetDevicePairingByToken(token); err != nil {
			writeError(w, http.StatusUnauthorized, "invalid bearer token")
			return
		}

		next(w, req)
	}
}

func (r *Router) providerResponses() ([]ProviderResponse, error) {
	providers, err := r.store.ListProviders()
	if err != nil {
		return nil, err
	}

	response := make([]ProviderResponse, 0, len(providers))
	for _, provider := range providers {
		windows, err := r.store.ListQuotaWindows(provider.ID)
		if err != nil {
			return nil, err
		}
		response = append(response, ProviderResponse{
			ID:        provider.ID,
			Name:      provider.Name,
			PlanTier:  provider.PlanTier,
			Available: provider.Available,
			Windows:   quotaWindowResponses(windows),
		})
	}

	return response, nil
}

func (r *Router) sessionResponses() ([]SessionResponse, error) {
	sessions, err := r.store.ListCodingSessions()
	if err != nil {
		return nil, err
	}

	response := make([]SessionResponse, 0, len(sessions))
	for _, session := range sessions {
		response = append(response, SessionResponse{
			ProviderID:     session.ProviderID,
			ProjectPath:    session.ProjectPath,
			State:          session.State,
			LastActivityAt: session.LastActivityAt,
		})
	}

	return response, nil
}

func quotaWindowResponses(windows []store.QuotaWindow) []QuotaWindowResponse {
	response := make([]QuotaWindowResponse, 0, len(windows))
	for _, window := range windows {
		response = append(response, QuotaWindowResponse{
			WindowType:  window.WindowType,
			PercentLeft: window.PercentLeft,
			Used:        window.Used,
			Limit:       window.Limit,
			ResetsAt:    window.ResetsAt,
			Source:      window.Source,
			UpdatedAt:   window.UpdatedAt,
		})
	}

	return response
}

func bearerToken(value string) (string, bool) {
	token, ok := strings.CutPrefix(value, "Bearer ")
	if !ok || token == "" {
		return "", false
	}
	return token, true
}

func isLoopbackRequest(req *http.Request) bool {
	host, _, err := net.SplitHostPort(req.RemoteAddr)
	if err != nil {
		host = req.RemoteAddr
	}

	ip := net.ParseIP(host)
	return ip != nil && ip.IsLoopback()
}

func allowMethod(w http.ResponseWriter, req *http.Request, method string) bool {
	if req.Method == method {
		return true
	}
	w.Header().Set("Allow", method)
	writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	return false
}

func writeJSON(w http.ResponseWriter, status int, value any) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(status)
	if err := json.NewEncoder(w).Encode(value); err != nil {
		log.Printf("write json response: %v", err)
	}
}

func writeError(w http.ResponseWriter, status int, message string) {
	writeJSON(w, status, errorResponse{Error: message})
}

func withDefault(value string, fallback string) string {
	if value == "" {
		return fallback
	}
	return value
}

func secureEqual(a string, b string) bool {
	if a == "" || b == "" {
		return false
	}
	return subtle.ConstantTimeCompare([]byte(a), []byte(b)) == 1
}

func randomURLToken(byteCount int) (string, error) {
	if byteCount <= 0 {
		return "", fmt.Errorf("byte count must be positive, got %d", byteCount)
	}

	bytes := make([]byte, byteCount)
	if _, err := rand.Read(bytes); err != nil {
		return "", err
	}

	return base64.RawURLEncoding.EncodeToString(bytes), nil
}
