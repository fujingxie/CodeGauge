package watcher

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/csv"
	"encoding/json"
	"errors"
	"fmt"
	"os/exec"
	"path/filepath"
	"runtime"
	"strings"
	"time"

	"github.com/xiexiansheng/codegauge/companion/internal/store"
)

type Store interface {
	UpsertProvider(provider store.Provider) error
	UpsertCodingSession(session store.CodingSession) error
	GetCodingSession(id string) (store.CodingSession, error)
	AddEvent(event store.Event) (int64, error)
}

type Process struct {
	Name string
}

type ProcessLister interface {
	List(ctx context.Context) ([]Process, error)
}

type Options struct {
	Lister ProcessLister
	Now    func() time.Time
}

type Watcher struct {
	store  Store
	lister ProcessLister
	now    func() time.Time
	active map[string]bool
}

func New(store Store, options Options) *Watcher {
	w := &Watcher{
		store:  store,
		lister: options.Lister,
		now:    options.Now,
		active: map[string]bool{},
	}
	if w.lister == nil {
		w.lister = commandProcessLister{}
	}
	if w.now == nil {
		w.now = time.Now
	}
	return w
}

func (w *Watcher) Run(ctx context.Context, interval time.Duration) error {
	if interval <= 0 {
		return fmt.Errorf("watch interval must be positive, got %s", interval)
	}
	if err := w.ScanOnce(ctx); err != nil {
		return err
	}

	ticker := time.NewTicker(interval)
	defer ticker.Stop()

	for {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-ticker.C:
			if err := w.ScanOnce(ctx); err != nil {
				return err
			}
		}
	}
}

func (w *Watcher) ScanOnce(ctx context.Context) error {
	if err := ctx.Err(); err != nil {
		return err
	}
	if w.store == nil {
		return errors.New("store is not configured")
	}

	processes, err := w.lister.List(ctx)
	if err != nil {
		return fmt.Errorf("list processes: %w", err)
	}

	detected := detectedProviders(processes)
	now := w.now().UTC()
	for _, providerID := range []string{store.ProviderClaude, store.ProviderCodex} {
		processName, ok := detected[providerID]
		if ok {
			if err := w.markActive(providerID, processName, now); err != nil {
				return err
			}
			continue
		}
		if w.active[providerID] {
			if err := w.markDone(providerID, now); err != nil {
				return err
			}
		}
	}

	return nil
}

func (w *Watcher) markActive(providerID string, processName string, now time.Time) error {
	if err := w.store.UpsertProvider(provider(providerID)); err != nil {
		return err
	}

	sessionID := watcherSessionID(providerID)
	startedAt, lastEventType, err := w.sessionFields(sessionID, now, store.EventSessionStart)
	if err != nil {
		return err
	}
	if w.active[providerID] {
		return w.store.UpsertCodingSession(store.CodingSession{
			ID:             sessionID,
			ProviderID:     providerID,
			State:          store.SessionStateRunning,
			StartedAt:      startedAt,
			LastActivityAt: now,
			LastEventType:  lastEventType,
		})
	}

	if err := w.store.UpsertCodingSession(store.CodingSession{
		ID:             sessionID,
		ProviderID:     providerID,
		State:          store.SessionStateRunning,
		StartedAt:      startedAt,
		LastActivityAt: now,
		LastEventType:  store.EventSessionStart,
	}); err != nil {
		return err
	}
	if err := w.addEvent(providerID, store.EventSessionStart, map[string]any{
		"source":  "process_watcher",
		"process": processName,
	}); err != nil {
		return err
	}

	w.active[providerID] = true
	return nil
}

func (w *Watcher) markDone(providerID string, now time.Time) error {
	sessionID := watcherSessionID(providerID)
	startedAt, _, err := w.sessionFields(sessionID, now, store.EventSessionDone)
	if err != nil {
		return err
	}

	if err := w.store.UpsertCodingSession(store.CodingSession{
		ID:             sessionID,
		ProviderID:     providerID,
		State:          store.SessionStateDone,
		StartedAt:      startedAt,
		LastActivityAt: now,
		LastEventType:  store.EventSessionDone,
	}); err != nil {
		return err
	}
	if err := w.addEvent(providerID, store.EventSessionDone, map[string]any{
		"source":   "process_watcher",
		"inferred": true,
	}); err != nil {
		return err
	}

	w.active[providerID] = false
	return nil
}

func (w *Watcher) sessionFields(sessionID string, fallbackStartedAt time.Time, fallbackEventType string) (time.Time, string, error) {
	session, err := w.store.GetCodingSession(sessionID)
	if err == nil {
		return session.StartedAt, session.LastEventType, nil
	}
	if errors.Is(err, sql.ErrNoRows) {
		return fallbackStartedAt, fallbackEventType, nil
	}
	return time.Time{}, "", err
}

func (w *Watcher) addEvent(providerID string, eventType string, payload map[string]any) error {
	body, err := json.Marshal(payload)
	if err != nil {
		return fmt.Errorf("marshal watcher event payload: %w", err)
	}
	if _, err := w.store.AddEvent(store.Event{
		ProviderID: &providerID,
		Type:       eventType,
		Payload:    string(body),
		CreatedAt:  w.now().UTC(),
	}); err != nil {
		return err
	}
	return nil
}

func detectedProviders(processes []Process) map[string]string {
	detected := map[string]string{}
	for _, process := range processes {
		name := normalizedProcessName(process.Name)
		switch name {
		case "claude":
			detected[store.ProviderClaude] = name
		case "codex":
			detected[store.ProviderCodex] = name
		}
	}
	return detected
}

func normalizedProcessName(name string) string {
	base := filepath.Base(strings.TrimSpace(name))
	base = strings.TrimSuffix(base, ".exe")
	return strings.ToLower(base)
}

func watcherSessionID(providerID string) string {
	return "watcher:" + providerID
}

func provider(providerID string) store.Provider {
	switch providerID {
	case store.ProviderClaude:
		return store.Provider{ID: store.ProviderClaude, Name: "Claude", Available: true}
	case store.ProviderCodex:
		return store.Provider{ID: store.ProviderCodex, Name: "Codex", Available: true}
	default:
		return store.Provider{ID: providerID, Name: providerID, Available: true}
	}
}

type commandProcessLister struct{}

func (commandProcessLister) List(ctx context.Context) ([]Process, error) {
	if runtime.GOOS == "windows" {
		return listWindowsProcesses(ctx)
	}
	return listUnixProcesses(ctx)
}

func listUnixProcesses(ctx context.Context) ([]Process, error) {
	cmd := exec.CommandContext(ctx, "ps", "-axo", "comm=")
	output, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	lines := bytes.Split(output, []byte{'\n'})
	processes := make([]Process, 0, len(lines))
	for _, line := range lines {
		name := strings.TrimSpace(string(line))
		if name == "" {
			continue
		}
		processes = append(processes, Process{Name: name})
	}
	return processes, nil
}

func listWindowsProcesses(ctx context.Context) ([]Process, error) {
	cmd := exec.CommandContext(ctx, "tasklist", "/fo", "csv", "/nh")
	output, err := cmd.Output()
	if err != nil {
		return nil, err
	}

	reader := csv.NewReader(bytes.NewReader(output))
	records, err := reader.ReadAll()
	if err != nil {
		return nil, err
	}

	processes := make([]Process, 0, len(records))
	for _, record := range records {
		if len(record) == 0 || strings.TrimSpace(record[0]) == "" {
			continue
		}
		processes = append(processes, Process{Name: record[0]})
	}
	return processes, nil
}
