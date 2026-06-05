package hooks

import (
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"fmt"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

type Store interface {
	UpsertProvider(provider store.Provider) error
	UpsertCodingSession(session store.CodingSession) error
	GetCodingSession(id string) (store.CodingSession, error)
	AddEvent(event store.Event) (int64, error)
}

type ClaudeReceiver struct {
	store Store
}

type ClaudeHookPayload struct {
	SessionID            string `json:"session_id"`
	TranscriptPath       string `json:"transcript_path"`
	CWD                  string `json:"cwd"`
	HookEventName        string `json:"hook_event_name"`
	Source               string `json:"source"`
	Model                string `json:"model"`
	Message              string `json:"message"`
	Title                string `json:"title"`
	NotificationType     string `json:"notification_type"`
	StopHookActive       bool   `json:"stop_hook_active"`
	LastAssistantMessage string `json:"last_assistant_message"`
}

func NewClaudeReceiver(store Store) *ClaudeReceiver {
	return &ClaudeReceiver{store: store}
}

func (r *ClaudeReceiver) Handle(ctx context.Context, body []byte, now time.Time) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if r.store == nil {
		return errors.New("store is not configured")
	}

	var payload ClaudeHookPayload
	if err := json.Unmarshal(body, &payload); err != nil {
		return fmt.Errorf("parse Claude hook payload: %w", err)
	}

	state, eventType, ok := claudeEventMapping(payload.HookEventName)
	if !ok {
		return nil
	}
	if payload.SessionID == "" {
		return errors.New("session_id is required")
	}

	now = now.UTC()
	if err := r.store.UpsertProvider(store.Provider{
		ID:        store.ProviderClaude,
		Name:      "Claude",
		Available: true,
	}); err != nil {
		return err
	}

	startedAt := now
	existing, err := r.store.GetCodingSession(payload.SessionID)
	if err == nil && !existing.StartedAt.IsZero() {
		startedAt = existing.StartedAt
	} else if err != nil && !errors.Is(err, sql.ErrNoRows) {
		return err
	}

	if err := r.store.UpsertCodingSession(store.CodingSession{
		ID:             payload.SessionID,
		ProviderID:     store.ProviderClaude,
		ProjectPath:    payload.CWD,
		State:          state,
		StartedAt:      startedAt,
		LastActivityAt: now,
		LastEventType:  eventType,
	}); err != nil {
		return err
	}

	providerID := store.ProviderClaude
	if _, err := r.store.AddEvent(store.Event{
		ProviderID: &providerID,
		Type:       eventType,
		Payload:    string(body),
		CreatedAt:  now,
	}); err != nil {
		return err
	}

	return nil
}

func claudeEventMapping(hookEventName string) (string, string, bool) {
	switch hookEventName {
	case "SessionStart":
		return store.SessionStateRunning, store.EventSessionStart, true
	case "Notification":
		return store.SessionStateWaiting, store.EventSessionWaiting, true
	case "Stop":
		return store.SessionStateDone, store.EventSessionDone, true
	default:
		return "", "", false
	}
}
