package store

import (
	"database/sql"
	"embed"
	"errors"
	"fmt"
	"time"

	_ "modernc.org/sqlite"
)

//go:embed migrations.sql
var migrationsFS embed.FS

type Store struct {
	db *sql.DB
}

func Open(path string) (*Store, error) {
	db, err := sql.Open("sqlite", path)
	if err != nil {
		return nil, fmt.Errorf("open sqlite database: %w", err)
	}

	store := &Store{db: db}
	if err := store.migrate(); err != nil {
		if closeErr := db.Close(); closeErr != nil {
			return nil, fmt.Errorf("migrate sqlite database: %w; close database: %w", err, closeErr)
		}
		return nil, fmt.Errorf("migrate sqlite database: %w", err)
	}

	return store, nil
}

func (s *Store) Close() error {
	if s == nil || s.db == nil {
		return nil
	}
	return s.db.Close()
}

func (s *Store) migrate() error {
	if _, err := s.db.Exec("PRAGMA foreign_keys = ON"); err != nil {
		return fmt.Errorf("enable foreign keys: %w", err)
	}

	migrations, err := migrationsFS.ReadFile("migrations.sql")
	if err != nil {
		return fmt.Errorf("read migrations: %w", err)
	}
	if _, err := s.db.Exec(string(migrations)); err != nil {
		return fmt.Errorf("apply migrations: %w", err)
	}

	return nil
}

func (s *Store) UpsertProvider(provider Provider) error {
	_, err := s.db.Exec(
		`INSERT INTO providers (id, name, plan_tier, available)
		 VALUES (?, ?, ?, ?)
		 ON CONFLICT(id) DO UPDATE SET
		   name = excluded.name,
		   plan_tier = excluded.plan_tier,
		   available = excluded.available`,
		provider.ID,
		provider.Name,
		provider.PlanTier,
		boolToInt(provider.Available),
	)
	if err != nil {
		return fmt.Errorf("upsert provider %q: %w", provider.ID, err)
	}

	return nil
}

func (s *Store) GetProvider(id string) (Provider, error) {
	var provider Provider
	var available int
	err := s.db.QueryRow(
		`SELECT id, name, plan_tier, available FROM providers WHERE id = ?`,
		id,
	).Scan(&provider.ID, &provider.Name, &provider.PlanTier, &available)
	if err != nil {
		return Provider{}, fmt.Errorf("get provider %q: %w", id, err)
	}

	provider.Available = available != 0
	return provider, nil
}

func (s *Store) ListProviders() ([]Provider, error) {
	rows, err := s.db.Query(
		`SELECT id, name, plan_tier, available
		 FROM providers
		 ORDER BY id ASC`,
	)
	if err != nil {
		return nil, fmt.Errorf("list providers: %w", err)
	}
	defer rows.Close()

	var providers []Provider
	for rows.Next() {
		var provider Provider
		var available int
		if err := rows.Scan(&provider.ID, &provider.Name, &provider.PlanTier, &available); err != nil {
			return nil, fmt.Errorf("scan provider: %w", err)
		}
		provider.Available = available != 0
		providers = append(providers, provider)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate providers: %w", err)
	}

	return providers, nil
}

func (s *Store) UpsertQuotaWindow(window QuotaWindow) error {
	_, err := s.db.Exec(
		`INSERT INTO quota_windows (
		   provider_id, window_type, percent_left, used, limit_value, resets_at, source, updated_at
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?, ?)
		 ON CONFLICT(provider_id, window_type) DO UPDATE SET
		   percent_left = excluded.percent_left,
		   used = excluded.used,
		   limit_value = excluded.limit_value,
		   resets_at = excluded.resets_at,
		   source = excluded.source,
		   updated_at = excluded.updated_at`,
		window.ProviderID,
		window.WindowType,
		nullableInt(window.PercentLeft),
		nullableInt64(window.Used),
		nullableInt64(window.Limit),
		nullableTime(window.ResetsAt),
		window.Source,
		formatTime(window.UpdatedAt),
	)
	if err != nil {
		return fmt.Errorf("upsert quota window %q/%q: %w", window.ProviderID, window.WindowType, err)
	}

	return nil
}

func (s *Store) ListQuotaWindows(providerID string) ([]QuotaWindow, error) {
	rows, err := s.db.Query(
		`SELECT id, provider_id, window_type, percent_left, used, limit_value, resets_at, source, updated_at
		 FROM quota_windows
		 WHERE provider_id = ?
		 ORDER BY window_type ASC`,
		providerID,
	)
	if err != nil {
		return nil, fmt.Errorf("list quota windows for %q: %w", providerID, err)
	}
	defer rows.Close()

	var windows []QuotaWindow
	for rows.Next() {
		window, err := scanQuotaWindow(rows)
		if err != nil {
			return nil, err
		}
		windows = append(windows, window)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate quota windows for %q: %w", providerID, err)
	}

	return windows, nil
}

func (s *Store) UpsertCodingSession(session CodingSession) error {
	_, err := s.db.Exec(
		`INSERT INTO coding_sessions (
		   id, provider_id, project_path, state, started_at, last_activity_at, last_event_type
		 )
		 VALUES (?, ?, ?, ?, ?, ?, ?)
		 ON CONFLICT(id) DO UPDATE SET
		   provider_id = excluded.provider_id,
		   project_path = excluded.project_path,
		   state = excluded.state,
		   started_at = excluded.started_at,
		   last_activity_at = excluded.last_activity_at,
		   last_event_type = excluded.last_event_type`,
		session.ID,
		session.ProviderID,
		session.ProjectPath,
		session.State,
		formatTime(session.StartedAt),
		formatTime(session.LastActivityAt),
		session.LastEventType,
	)
	if err != nil {
		return fmt.Errorf("upsert coding session %q: %w", session.ID, err)
	}

	return nil
}

func (s *Store) ListCodingSessions() ([]CodingSession, error) {
	rows, err := s.db.Query(
		`SELECT id, provider_id, project_path, state, started_at, last_activity_at, last_event_type
		 FROM coding_sessions
		 ORDER BY last_activity_at DESC, id DESC`,
	)
	if err != nil {
		return nil, fmt.Errorf("list coding sessions: %w", err)
	}
	defer rows.Close()

	var sessions []CodingSession
	for rows.Next() {
		session, err := scanCodingSession(rows)
		if err != nil {
			return nil, err
		}
		sessions = append(sessions, session)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate coding sessions: %w", err)
	}

	return sessions, nil
}

func (s *Store) AddEvent(event Event) (int64, error) {
	result, err := s.db.Exec(
		`INSERT INTO events (provider_id, type, payload, created_at)
		 VALUES (?, ?, ?, ?)`,
		nullableString(event.ProviderID),
		event.Type,
		event.Payload,
		formatTime(event.CreatedAt),
	)
	if err != nil {
		return 0, fmt.Errorf("add event %q: %w", event.Type, err)
	}

	id, err := result.LastInsertId()
	if err != nil {
		return 0, fmt.Errorf("read event id: %w", err)
	}

	return id, nil
}

func (s *Store) ListEvents(limit int) ([]Event, error) {
	if limit <= 0 {
		limit = 50
	}

	rows, err := s.db.Query(
		`SELECT id, provider_id, type, payload, created_at
		 FROM events
		 ORDER BY created_at DESC, id DESC
		 LIMIT ?`,
		limit,
	)
	if err != nil {
		return nil, fmt.Errorf("list events: %w", err)
	}
	defer rows.Close()

	var events []Event
	for rows.Next() {
		event, err := scanEvent(rows)
		if err != nil {
			return nil, err
		}
		events = append(events, event)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate events: %w", err)
	}

	return events, nil
}

func (s *Store) UpsertDevicePairing(device DevicePairing) error {
	_, err := s.db.Exec(
		`INSERT INTO device_pairings (device_id, name, token, paired_at, last_seen_at)
		 VALUES (?, ?, ?, ?, ?)
		 ON CONFLICT(device_id) DO UPDATE SET
		   name = excluded.name,
		   token = excluded.token,
		   paired_at = excluded.paired_at,
		   last_seen_at = excluded.last_seen_at`,
		device.DeviceID,
		device.Name,
		device.Token,
		formatTime(device.PairedAt),
		formatTime(device.LastSeenAt),
	)
	if err != nil {
		return fmt.Errorf("upsert device pairing %q: %w", device.DeviceID, err)
	}

	return nil
}

func (s *Store) GetDevicePairing(deviceID string) (DevicePairing, error) {
	row := s.db.QueryRow(
		`SELECT device_id, name, token, paired_at, last_seen_at
		 FROM device_pairings
		 WHERE device_id = ?`,
		deviceID,
	)

	device, err := scanDevicePairing(row)
	if err != nil {
		return DevicePairing{}, fmt.Errorf("get device pairing %q: %w", deviceID, err)
	}

	return device, nil
}

func (s *Store) GetDevicePairingByToken(token string) (DevicePairing, error) {
	row := s.db.QueryRow(
		`SELECT device_id, name, token, paired_at, last_seen_at
		 FROM device_pairings
		 WHERE token = ?`,
		token,
	)

	device, err := scanDevicePairing(row)
	if err != nil {
		return DevicePairing{}, fmt.Errorf("get device pairing by token: %w", err)
	}

	return device, nil
}

func (s *Store) SetSetting(key string, value string) error {
	_, err := s.db.Exec(
		`INSERT INTO settings (key, value)
		 VALUES (?, ?)
		 ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
		key,
		value,
	)
	if err != nil {
		return fmt.Errorf("set setting %q: %w", key, err)
	}

	return nil
}

func (s *Store) GetSetting(key string) (Setting, error) {
	var setting Setting
	err := s.db.QueryRow(
		`SELECT key, value FROM settings WHERE key = ?`,
		key,
	).Scan(&setting.Key, &setting.Value)
	if err != nil {
		return Setting{}, fmt.Errorf("get setting %q: %w", key, err)
	}

	return setting, nil
}

type scanner interface {
	Scan(dest ...any) error
}

func scanQuotaWindow(row scanner) (QuotaWindow, error) {
	var window QuotaWindow
	var percentLeft sql.NullInt64
	var used sql.NullInt64
	var limit sql.NullInt64
	var resetsAt sql.NullString
	var updatedAt string

	if err := row.Scan(
		&window.ID,
		&window.ProviderID,
		&window.WindowType,
		&percentLeft,
		&used,
		&limit,
		&resetsAt,
		&window.Source,
		&updatedAt,
	); err != nil {
		return QuotaWindow{}, fmt.Errorf("scan quota window: %w", err)
	}

	if percentLeft.Valid {
		value := int(percentLeft.Int64)
		window.PercentLeft = &value
	}
	if used.Valid {
		value := used.Int64
		window.Used = &value
	}
	if limit.Valid {
		value := limit.Int64
		window.Limit = &value
	}
	if resetsAt.Valid {
		value, err := parseTime(resetsAt.String)
		if err != nil {
			return QuotaWindow{}, fmt.Errorf("parse quota window resets_at: %w", err)
		}
		window.ResetsAt = &value
	}

	parsedUpdatedAt, err := parseTime(updatedAt)
	if err != nil {
		return QuotaWindow{}, fmt.Errorf("parse quota window updated_at: %w", err)
	}
	window.UpdatedAt = parsedUpdatedAt

	return window, nil
}

func scanCodingSession(row scanner) (CodingSession, error) {
	var session CodingSession
	var startedAt string
	var lastActivityAt string

	if err := row.Scan(
		&session.ID,
		&session.ProviderID,
		&session.ProjectPath,
		&session.State,
		&startedAt,
		&lastActivityAt,
		&session.LastEventType,
	); err != nil {
		return CodingSession{}, fmt.Errorf("scan coding session: %w", err)
	}

	parsedStartedAt, err := parseTime(startedAt)
	if err != nil {
		return CodingSession{}, fmt.Errorf("parse coding session started_at: %w", err)
	}
	parsedLastActivityAt, err := parseTime(lastActivityAt)
	if err != nil {
		return CodingSession{}, fmt.Errorf("parse coding session last_activity_at: %w", err)
	}
	session.StartedAt = parsedStartedAt
	session.LastActivityAt = parsedLastActivityAt

	return session, nil
}

func scanEvent(row scanner) (Event, error) {
	var event Event
	var providerID sql.NullString
	var createdAt string

	if err := row.Scan(
		&event.ID,
		&providerID,
		&event.Type,
		&event.Payload,
		&createdAt,
	); err != nil {
		return Event{}, fmt.Errorf("scan event: %w", err)
	}

	if providerID.Valid {
		value := providerID.String
		event.ProviderID = &value
	}

	parsedCreatedAt, err := parseTime(createdAt)
	if err != nil {
		return Event{}, fmt.Errorf("parse event created_at: %w", err)
	}
	event.CreatedAt = parsedCreatedAt

	return event, nil
}

func scanDevicePairing(row scanner) (DevicePairing, error) {
	var device DevicePairing
	var pairedAt string
	var lastSeenAt string

	if err := row.Scan(
		&device.DeviceID,
		&device.Name,
		&device.Token,
		&pairedAt,
		&lastSeenAt,
	); err != nil {
		return DevicePairing{}, fmt.Errorf("scan device pairing: %w", err)
	}

	parsedPairedAt, err := parseTime(pairedAt)
	if err != nil {
		return DevicePairing{}, fmt.Errorf("parse device pairing paired_at: %w", err)
	}
	parsedLastSeenAt, err := parseTime(lastSeenAt)
	if err != nil {
		return DevicePairing{}, fmt.Errorf("parse device pairing last_seen_at: %w", err)
	}
	device.PairedAt = parsedPairedAt
	device.LastSeenAt = parsedLastSeenAt

	return device, nil
}

func boolToInt(value bool) int {
	if value {
		return 1
	}
	return 0
}

func nullableInt(value *int) any {
	if value == nil {
		return nil
	}
	return *value
}

func nullableInt64(value *int64) any {
	if value == nil {
		return nil
	}
	return *value
}

func nullableString(value *string) any {
	if value == nil {
		return nil
	}
	return *value
}

func nullableTime(value *time.Time) any {
	if value == nil {
		return nil
	}
	return formatTime(*value)
}

func formatTime(value time.Time) string {
	return value.UTC().Format(time.RFC3339Nano)
}

func parseTime(value string) (time.Time, error) {
	parsed, err := time.Parse(time.RFC3339Nano, value)
	if err == nil {
		return parsed, nil
	}

	fallback, fallbackErr := time.Parse(time.RFC3339, value)
	if fallbackErr != nil {
		return time.Time{}, errors.Join(err, fallbackErr)
	}
	return fallback, nil
}
