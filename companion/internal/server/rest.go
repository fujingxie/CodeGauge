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
	"strconv"
	"strings"
	"sync"
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
	ListEvents(limit int) ([]store.Event, error)
	UpsertDevicePairing(device store.DevicePairing) error
	ListDevicePairings() ([]store.DevicePairing, error)
	GetDevicePairingByToken(token string) (store.DevicePairing, error)
	SetSetting(key string, value string) error
	ListSettings() ([]store.Setting, error)
}

type TokenGenerator func() (string, error)

type Options struct {
	Version             string
	ServerName          string
	PairCode            string
	Store               Store
	Now                 func() time.Time
	StreamHub           *codestream.Hub
	SettingsDefaults    SettingsDefaults
	PairCodeTTL         time.Duration
	PairCodeMaxAttempts int
	TokenGenerator      TokenGenerator
	DeviceIDGenerator   TokenGenerator
}

type Router struct {
	version             string
	serverName          string
	pairCode            string
	store               Store
	now                 func() time.Time
	streamHub           *codestream.Hub
	settingsDefaults    SettingsDefaults
	pairCodeIssuedAt    time.Time
	pairCodeTTL         time.Duration
	pairCodeMaxAttempts int
	pairCodeMu          sync.Mutex
	failedPairAttempts  int
	tokenGenerator      TokenGenerator
	deviceIDGenerator   TokenGenerator
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

type EventsResponse struct {
	Events []EventResponse `json:"events"`
}

type SettingsDefaults struct {
	CollectIntervalSeconds int
	WarningThreshold       int
	CriticalThreshold      int
}

type SettingsResponse struct {
	Settings AppSettings `json:"settings"`
}

type AppSettings struct {
	NotificationsEnabled    bool `json:"notifications_enabled"`
	WarningThreshold        int  `json:"warning_threshold"`
	CriticalThreshold       int  `json:"critical_threshold"`
	QuotaResetNotifications bool `json:"quota_reset_notifications"`
	TaskDoneNotifications   bool `json:"task_done_notifications"`
	CollectIntervalSeconds  int  `json:"collect_interval_seconds"`
}

type SettingsPatchRequest struct {
	Settings SettingsPatch `json:"settings"`
}

type SettingsPatch struct {
	NotificationsEnabled    *bool `json:"notifications_enabled"`
	WarningThreshold        *int  `json:"warning_threshold"`
	CriticalThreshold       *int  `json:"critical_threshold"`
	QuotaResetNotifications *bool `json:"quota_reset_notifications"`
	TaskDoneNotifications   *bool `json:"task_done_notifications"`
	CollectIntervalSeconds  *int  `json:"collect_interval_seconds"`
}

type DevicesResponse struct {
	Devices []DeviceResponse `json:"devices"`
}

type DeviceResponse struct {
	DeviceID   string    `json:"device_id"`
	Name       string    `json:"name"`
	PairedAt   time.Time `json:"paired_at"`
	LastSeenAt time.Time `json:"last_seen_at"`
}

type DiagnosticsResponse struct {
	OK                     bool       `json:"ok"`
	ServerName             string     `json:"server_name"`
	Version                string     `json:"version"`
	ServerTime             time.Time  `json:"server_time"`
	ProviderCount          int        `json:"provider_count"`
	AvailableProviderCount int        `json:"available_provider_count"`
	RunningSessionCount    int        `json:"running_session_count"`
	WaitingSessionCount    int        `json:"waiting_session_count"`
	PairedDeviceCount      int        `json:"paired_device_count"`
	LatestEventAt          *time.Time `json:"latest_event_at"`
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

type EventResponse struct {
	ID         int64     `json:"id"`
	Type       string    `json:"type"`
	ProviderID *string   `json:"provider_id"`
	Payload    string    `json:"payload"`
	CreatedAt  time.Time `json:"created_at"`
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

const (
	settingNotificationsEnabled    = "notifications_enabled"
	settingWarningThreshold        = "warning_threshold"
	settingCriticalThreshold       = "critical_threshold"
	settingQuotaResetNotifications = "quota_reset_notifications"
	settingTaskDoneNotifications   = "task_done_notifications"
	settingCollectIntervalSeconds  = "collect_interval_seconds"
)

func NewRouter(options Options) http.Handler {
	router := &Router{
		version:             withDefault(options.Version, "dev"),
		serverName:          withDefault(options.ServerName, "CodeGauge"),
		pairCode:            options.PairCode,
		store:               options.Store,
		now:                 options.Now,
		streamHub:           options.StreamHub,
		settingsDefaults:    settingsDefaults(options.SettingsDefaults),
		pairCodeTTL:         options.PairCodeTTL,
		pairCodeMaxAttempts: options.PairCodeMaxAttempts,
		tokenGenerator:      options.TokenGenerator,
		deviceIDGenerator:   options.DeviceIDGenerator,
	}
	if router.now == nil {
		router.now = time.Now
	}
	if router.pairCodeTTL <= 0 {
		router.pairCodeTTL = 10 * time.Minute
	}
	if router.pairCodeMaxAttempts <= 0 {
		router.pairCodeMaxAttempts = 5
	}
	router.pairCodeIssuedAt = router.now().UTC()
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
	mux.HandleFunc("/api/v1/events", router.withAuth(router.events))
	mux.HandleFunc("/api/v1/settings", router.withAuth(router.settings))
	mux.HandleFunc("/api/v1/devices", router.withAuth(router.devices))
	mux.HandleFunc("/api/v1/diagnostics", router.withAuth(router.diagnostics))
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
	if status, message, ok := r.acceptPairCode(body.PairCode); !ok {
		writeError(w, status, message)
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

func (r *Router) acceptPairCode(pairCode string) (int, string, bool) {
	r.pairCodeMu.Lock()
	defer r.pairCodeMu.Unlock()

	if r.failedPairAttempts >= r.pairCodeMaxAttempts {
		return http.StatusTooManyRequests, "too many pair attempts", false
	}
	if r.pairCodeTTL > 0 && r.now().UTC().After(r.pairCodeIssuedAt.Add(r.pairCodeTTL)) {
		return http.StatusUnauthorized, "invalid pair code", false
	}
	if !secureEqual(pairCode, r.pairCode) {
		r.failedPairAttempts++
		if r.failedPairAttempts >= r.pairCodeMaxAttempts {
			return http.StatusTooManyRequests, "too many pair attempts", false
		}
		return http.StatusUnauthorized, "invalid pair code", false
	}

	r.failedPairAttempts = 0
	return 0, "", true
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

func (r *Router) events(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	events, err := r.store.ListEvents(eventLimit(req))
	if err != nil {
		log.Printf("build events response: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read events")
		return
	}

	writeJSON(w, http.StatusOK, EventsResponse{
		Events: eventResponses(events),
	})
}

func (r *Router) settings(w http.ResponseWriter, req *http.Request) {
	switch req.Method {
	case http.MethodGet:
		r.getSettings(w, req)
	case http.MethodPatch:
		r.patchSettings(w, req)
	default:
		w.Header().Set("Allow", "GET, PATCH")
		writeError(w, http.StatusMethodNotAllowed, "method not allowed")
	}
}

func (r *Router) getSettings(w http.ResponseWriter, _ *http.Request) {
	settings, err := r.currentSettings()
	if err != nil {
		log.Printf("read settings: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read settings")
		return
	}

	writeJSON(w, http.StatusOK, SettingsResponse{Settings: settings})
}

func (r *Router) patchSettings(w http.ResponseWriter, req *http.Request) {
	var body SettingsPatchRequest
	if err := json.NewDecoder(req.Body).Decode(&body); err != nil {
		writeError(w, http.StatusBadRequest, "invalid JSON body")
		return
	}

	settings, err := r.currentSettings()
	if err != nil {
		log.Printf("read settings before patch: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read settings")
		return
	}
	applySettingsPatch(&settings, body.Settings)
	if err := validateSettings(settings); err != nil {
		writeError(w, http.StatusBadRequest, err.Error())
		return
	}
	if err := r.storeSettingsPatch(body.Settings); err != nil {
		log.Printf("store settings patch: %v", err)
		writeError(w, http.StatusInternalServerError, "could not store settings")
		return
	}

	writeJSON(w, http.StatusOK, SettingsResponse{Settings: settings})
}

func (r *Router) devices(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	devices, err := r.store.ListDevicePairings()
	if err != nil {
		log.Printf("list devices: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read devices")
		return
	}

	writeJSON(w, http.StatusOK, DevicesResponse{
		Devices: deviceResponses(devices),
	})
}

func (r *Router) diagnostics(w http.ResponseWriter, req *http.Request) {
	if !allowMethod(w, req, http.MethodGet) {
		return
	}

	providers, err := r.store.ListProviders()
	if err != nil {
		log.Printf("read providers for diagnostics: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read diagnostics")
		return
	}
	sessions, err := r.store.ListCodingSessions()
	if err != nil {
		log.Printf("read sessions for diagnostics: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read diagnostics")
		return
	}
	devices, err := r.store.ListDevicePairings()
	if err != nil {
		log.Printf("read devices for diagnostics: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read diagnostics")
		return
	}
	events, err := r.store.ListEvents(1)
	if err != nil {
		log.Printf("read events for diagnostics: %v", err)
		writeError(w, http.StatusInternalServerError, "could not read diagnostics")
		return
	}

	writeJSON(w, http.StatusOK, diagnosticsResponse(
		r.serverName,
		r.version,
		r.now().UTC(),
		providers,
		sessions,
		devices,
		events,
	))
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

func (r *Router) currentSettings() (AppSettings, error) {
	settings, err := r.store.ListSettings()
	if err != nil {
		return AppSettings{}, err
	}
	values := settingValues(settings)
	return AppSettings{
		NotificationsEnabled:    settingBool(values, settingNotificationsEnabled, true),
		WarningThreshold:        settingInt(values, settingWarningThreshold, r.settingsDefaults.WarningThreshold),
		CriticalThreshold:       settingInt(values, settingCriticalThreshold, r.settingsDefaults.CriticalThreshold),
		QuotaResetNotifications: settingBool(values, settingQuotaResetNotifications, true),
		TaskDoneNotifications:   settingBool(values, settingTaskDoneNotifications, true),
		CollectIntervalSeconds:  settingInt(values, settingCollectIntervalSeconds, r.settingsDefaults.CollectIntervalSeconds),
	}, nil
}

func (r *Router) storeSettingsPatch(patch SettingsPatch) error {
	if patch.NotificationsEnabled != nil {
		if err := r.store.SetSetting(settingNotificationsEnabled, strconv.FormatBool(*patch.NotificationsEnabled)); err != nil {
			return err
		}
	}
	if patch.WarningThreshold != nil {
		if err := r.store.SetSetting(settingWarningThreshold, strconv.Itoa(*patch.WarningThreshold)); err != nil {
			return err
		}
	}
	if patch.CriticalThreshold != nil {
		if err := r.store.SetSetting(settingCriticalThreshold, strconv.Itoa(*patch.CriticalThreshold)); err != nil {
			return err
		}
	}
	if patch.QuotaResetNotifications != nil {
		if err := r.store.SetSetting(settingQuotaResetNotifications, strconv.FormatBool(*patch.QuotaResetNotifications)); err != nil {
			return err
		}
	}
	if patch.TaskDoneNotifications != nil {
		if err := r.store.SetSetting(settingTaskDoneNotifications, strconv.FormatBool(*patch.TaskDoneNotifications)); err != nil {
			return err
		}
	}
	if patch.CollectIntervalSeconds != nil {
		if err := r.store.SetSetting(settingCollectIntervalSeconds, strconv.Itoa(*patch.CollectIntervalSeconds)); err != nil {
			return err
		}
	}
	return nil
}

func applySettingsPatch(settings *AppSettings, patch SettingsPatch) {
	if patch.NotificationsEnabled != nil {
		settings.NotificationsEnabled = *patch.NotificationsEnabled
	}
	if patch.WarningThreshold != nil {
		settings.WarningThreshold = *patch.WarningThreshold
	}
	if patch.CriticalThreshold != nil {
		settings.CriticalThreshold = *patch.CriticalThreshold
	}
	if patch.QuotaResetNotifications != nil {
		settings.QuotaResetNotifications = *patch.QuotaResetNotifications
	}
	if patch.TaskDoneNotifications != nil {
		settings.TaskDoneNotifications = *patch.TaskDoneNotifications
	}
	if patch.CollectIntervalSeconds != nil {
		settings.CollectIntervalSeconds = *patch.CollectIntervalSeconds
	}
}

func validateSettings(settings AppSettings) error {
	if settings.WarningThreshold < 0 || settings.WarningThreshold > 100 {
		return fmt.Errorf("warning_threshold must be between 0 and 100")
	}
	if settings.CriticalThreshold < 0 || settings.CriticalThreshold > 100 {
		return fmt.Errorf("critical_threshold must be between 0 and 100")
	}
	if settings.WarningThreshold >= settings.CriticalThreshold {
		return fmt.Errorf("warning_threshold must be less than critical_threshold")
	}
	if settings.CollectIntervalSeconds <= 0 {
		return fmt.Errorf("collect_interval_seconds must be positive")
	}
	return nil
}

func deviceResponses(devices []store.DevicePairing) []DeviceResponse {
	response := make([]DeviceResponse, 0, len(devices))
	for _, device := range devices {
		response = append(response, DeviceResponse{
			DeviceID:   device.DeviceID,
			Name:       device.Name,
			PairedAt:   device.PairedAt,
			LastSeenAt: device.LastSeenAt,
		})
	}
	return response
}

func diagnosticsResponse(
	serverName string,
	version string,
	serverTime time.Time,
	providers []store.Provider,
	sessions []store.CodingSession,
	devices []store.DevicePairing,
	events []store.Event,
) DiagnosticsResponse {
	var availableProviderCount int
	for _, provider := range providers {
		if provider.Available {
			availableProviderCount++
		}
	}

	var runningSessionCount int
	var waitingSessionCount int
	for _, session := range sessions {
		switch session.State {
		case store.SessionStateRunning:
			runningSessionCount++
		case store.SessionStateWaiting:
			waitingSessionCount++
		}
	}

	var latestEventAt *time.Time
	if len(events) > 0 {
		value := events[0].CreatedAt
		latestEventAt = &value
	}

	return DiagnosticsResponse{
		OK:                     true,
		ServerName:             serverName,
		Version:                version,
		ServerTime:             serverTime,
		ProviderCount:          len(providers),
		AvailableProviderCount: availableProviderCount,
		RunningSessionCount:    runningSessionCount,
		WaitingSessionCount:    waitingSessionCount,
		PairedDeviceCount:      len(devices),
		LatestEventAt:          latestEventAt,
	}
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

func eventResponses(events []store.Event) []EventResponse {
	response := make([]EventResponse, 0, len(events))
	for _, event := range events {
		response = append(response, EventResponse{
			ID:         event.ID,
			Type:       event.Type,
			ProviderID: event.ProviderID,
			Payload:    event.Payload,
			CreatedAt:  event.CreatedAt,
		})
	}

	return response
}

func settingValues(settings []store.Setting) map[string]string {
	values := make(map[string]string, len(settings))
	for _, setting := range settings {
		values[setting.Key] = setting.Value
	}
	return values
}

func settingBool(values map[string]string, key string, fallback bool) bool {
	value, ok := values[key]
	if !ok {
		return fallback
	}
	parsed, err := strconv.ParseBool(value)
	if err != nil {
		log.Printf("ignore invalid bool setting %q=%q: %v", key, value, err)
		return fallback
	}
	return parsed
}

func settingInt(values map[string]string, key string, fallback int) int {
	value, ok := values[key]
	if !ok {
		return fallback
	}
	parsed, err := strconv.Atoi(value)
	if err != nil {
		log.Printf("ignore invalid int setting %q=%q: %v", key, value, err)
		return fallback
	}
	return parsed
}

func settingsDefaults(defaults SettingsDefaults) SettingsDefaults {
	if defaults.CollectIntervalSeconds <= 0 {
		defaults.CollectIntervalSeconds = 60
	}
	if defaults.WarningThreshold <= 0 {
		defaults.WarningThreshold = 80
	}
	if defaults.CriticalThreshold <= 0 {
		defaults.CriticalThreshold = 95
	}
	return defaults
}

func eventLimit(req *http.Request) int {
	value := req.URL.Query().Get("limit")
	if value == "" {
		return 50
	}
	limit, err := strconv.Atoi(value)
	if err != nil || limit <= 0 {
		return 50
	}
	if limit > 200 {
		return 200
	}
	return limit
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
