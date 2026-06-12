package stream

import (
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

type Store interface {
	UpsertProvider(provider store.Provider) error
	ListProviders() ([]store.Provider, error)
	UpsertQuotaWindow(window store.QuotaWindow) error
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

type Options struct {
	WarningThreshold  int
	CriticalThreshold int
}

type NotifyingStore struct {
	inner             Store
	hub               *Hub
	warningThreshold  int
	criticalThreshold int
}

type QuotaUpdate struct {
	ProviderID  string     `json:"provider_id"`
	WindowType  string     `json:"window_type"`
	PercentLeft *int       `json:"percent_left"`
	Used        *int64     `json:"used"`
	Limit       *int64     `json:"limit"`
	ResetsAt    *time.Time `json:"resets_at"`
	Source      string     `json:"source"`
	UpdatedAt   time.Time  `json:"updated_at"`
}

type SessionUpdate struct {
	ProviderID     string    `json:"provider_id"`
	ProjectPath    string    `json:"project_path"`
	State          string    `json:"state"`
	LastActivityAt time.Time `json:"last_activity_at"`
	LastEventType  string    `json:"last_event_type"`
}

type Alert struct {
	ProviderID    string `json:"provider_id"`
	WindowType    string `json:"window_type"`
	Severity      string `json:"severity"`
	Threshold     int    `json:"threshold"`
	UsagePercent  int    `json:"usage_percent"`
	QuotaEventKey string `json:"quota_event_key"`
}

type EventUpdate struct {
	ID         int64     `json:"id"`
	ProviderID *string   `json:"provider_id"`
	Type       string    `json:"type"`
	Payload    string    `json:"payload"`
	CreatedAt  time.Time `json:"created_at"`
}

func NewNotifyingStore(inner Store, hub *Hub, options Options) *NotifyingStore {
	if hub == nil {
		hub = NewHub()
	}
	warningThreshold := options.WarningThreshold
	if warningThreshold <= 0 {
		warningThreshold = 80
	}
	criticalThreshold := options.CriticalThreshold
	if criticalThreshold <= 0 {
		criticalThreshold = 95
	}

	return &NotifyingStore{
		inner:             inner,
		hub:               hub,
		warningThreshold:  warningThreshold,
		criticalThreshold: criticalThreshold,
	}
}

func (s *NotifyingStore) UpsertProvider(provider store.Provider) error {
	return s.inner.UpsertProvider(provider)
}

func (s *NotifyingStore) ListProviders() ([]store.Provider, error) {
	return s.inner.ListProviders()
}

func (s *NotifyingStore) UpsertQuotaWindow(window store.QuotaWindow) error {
	previous, err := s.previousQuotaWindow(window.ProviderID, window.WindowType)
	if err != nil {
		return err
	}
	if err := s.inner.UpsertQuotaWindow(window); err != nil {
		return err
	}

	s.hub.Publish(Message{EventType: EventTypeQuotaUpdate, Data: quotaUpdate(window)})
	if alert, ok := s.thresholdAlert(previous, window); ok {
		s.hub.Publish(Message{EventType: EventTypeAlert, Data: alert})
	}
	return nil
}

func (s *NotifyingStore) ListQuotaWindows(providerID string) ([]store.QuotaWindow, error) {
	return s.inner.ListQuotaWindows(providerID)
}

func (s *NotifyingStore) UpsertCodingSession(session store.CodingSession) error {
	if err := s.inner.UpsertCodingSession(session); err != nil {
		return err
	}

	s.hub.Publish(Message{EventType: EventTypeSessionUpdate, Data: sessionUpdate(session)})
	return nil
}

func (s *NotifyingStore) GetCodingSession(id string) (store.CodingSession, error) {
	return s.inner.GetCodingSession(id)
}

func (s *NotifyingStore) ListCodingSessions() ([]store.CodingSession, error) {
	return s.inner.ListCodingSessions()
}

func (s *NotifyingStore) AddEvent(event store.Event) (int64, error) {
	id, err := s.inner.AddEvent(event)
	if err != nil {
		return 0, err
	}

	event.ID = id
	s.hub.Publish(Message{EventType: EventTypeEventUpdate, Data: eventUpdate(event)})
	return id, nil
}

func (s *NotifyingStore) ListEvents(limit int) ([]store.Event, error) {
	return s.inner.ListEvents(limit)
}

func (s *NotifyingStore) UpsertDevicePairing(device store.DevicePairing) error {
	return s.inner.UpsertDevicePairing(device)
}

func (s *NotifyingStore) ListDevicePairings() ([]store.DevicePairing, error) {
	return s.inner.ListDevicePairings()
}

func (s *NotifyingStore) GetDevicePairingByToken(token string) (store.DevicePairing, error) {
	return s.inner.GetDevicePairingByToken(token)
}

func (s *NotifyingStore) SetSetting(key string, value string) error {
	return s.inner.SetSetting(key, value)
}

func (s *NotifyingStore) ListSettings() ([]store.Setting, error) {
	return s.inner.ListSettings()
}

func (s *NotifyingStore) previousQuotaWindow(providerID string, windowType string) (*store.QuotaWindow, error) {
	windows, err := s.inner.ListQuotaWindows(providerID)
	if err != nil {
		return nil, err
	}
	for _, window := range windows {
		if window.WindowType == windowType {
			return &window, nil
		}
	}
	return nil, nil
}

func (s *NotifyingStore) thresholdAlert(previous *store.QuotaWindow, current store.QuotaWindow) (Alert, bool) {
	currentUsage, ok := usagePercent(current)
	if !ok {
		return Alert{}, false
	}
	previousUsage := 0
	if previous != nil {
		if value, ok := usagePercent(*previous); ok {
			previousUsage = value
		}
	}

	if previous != nil && previousUsage >= s.warningThreshold && currentUsage < s.warningThreshold {
		return alert(current, AlertSeverityReset, s.warningThreshold, currentUsage), true
	}
	if previousUsage < s.criticalThreshold && currentUsage >= s.criticalThreshold {
		return alert(current, AlertSeverityCritical, s.criticalThreshold, currentUsage), true
	}
	if previousUsage < s.warningThreshold && currentUsage >= s.warningThreshold {
		return alert(current, AlertSeverityWarning, s.warningThreshold, currentUsage), true
	}
	return Alert{}, false
}

func usagePercent(window store.QuotaWindow) (int, bool) {
	if window.PercentLeft != nil {
		return clampPercent(100 - *window.PercentLeft), true
	}
	if window.Used == nil || window.Limit == nil || *window.Limit <= 0 {
		return 0, false
	}
	return clampPercent(int((*window.Used * 100) / *window.Limit)), true
}

func quotaUpdate(window store.QuotaWindow) QuotaUpdate {
	return QuotaUpdate{
		ProviderID:  window.ProviderID,
		WindowType:  window.WindowType,
		PercentLeft: window.PercentLeft,
		Used:        window.Used,
		Limit:       window.Limit,
		ResetsAt:    window.ResetsAt,
		Source:      window.Source,
		UpdatedAt:   window.UpdatedAt,
	}
}

func sessionUpdate(session store.CodingSession) SessionUpdate {
	return SessionUpdate{
		ProviderID:     session.ProviderID,
		ProjectPath:    session.ProjectPath,
		State:          session.State,
		LastActivityAt: session.LastActivityAt,
		LastEventType:  session.LastEventType,
	}
}

func alert(window store.QuotaWindow, severity string, threshold int, usagePercent int) Alert {
	return Alert{
		ProviderID:    window.ProviderID,
		WindowType:    window.WindowType,
		Severity:      severity,
		Threshold:     threshold,
		UsagePercent:  usagePercent,
		QuotaEventKey: window.ProviderID + ":" + window.WindowType + ":" + severity,
	}
}

func eventUpdate(event store.Event) EventUpdate {
	return EventUpdate{
		ID:         event.ID,
		ProviderID: event.ProviderID,
		Type:       event.Type,
		Payload:    event.Payload,
		CreatedAt:  event.CreatedAt,
	}
}

func clampPercent(value int) int {
	if value < 0 {
		return 0
	}
	if value > 100 {
		return 100
	}
	return value
}
