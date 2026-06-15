package store

import (
	"crypto/sha256"
	"database/sql"
	"embed"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
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
	if err := s.migrateDevicePairingTokenHash(); err != nil {
		return err
	}

	return nil
}

func (s *Store) migrateDevicePairingTokenHash() error {
	hasTokenHash, err := s.hasColumn("device_pairings", "token_hash")
	if err != nil {
		return err
	}
	if !hasTokenHash {
		if _, err := s.db.Exec(`ALTER TABLE device_pairings ADD COLUMN token_hash TEXT`); err != nil {
			return fmt.Errorf("add device_pairings token_hash column: %w", err)
		}
	}
	if _, err := s.db.Exec(`CREATE UNIQUE INDEX IF NOT EXISTS idx_device_pairings_token_hash ON device_pairings (token_hash)`); err != nil {
		return fmt.Errorf("create device pairings token hash index: %w", err)
	}

	rows, err := s.db.Query(`SELECT device_id, token, COALESCE(token_hash, '') FROM device_pairings`)
	if err != nil {
		return fmt.Errorf("list device pairing tokens for migration: %w", err)
	}
	defer rows.Close()

	type tokenMigration struct {
		deviceID  string
		tokenHash string
	}
	var migrations []tokenMigration
	for rows.Next() {
		var deviceID string
		var token string
		var tokenHash string
		if err := rows.Scan(&deviceID, &token, &tokenHash); err != nil {
			return fmt.Errorf("scan device pairing token for migration: %w", err)
		}
		hash := tokenHash
		if hash == "" {
			hash = HashToken(token)
		}
		if !isStoredTokenHash(token) {
			migrations = append(migrations, tokenMigration{deviceID: deviceID, tokenHash: hash})
		} else if tokenHash == "" {
			migrations = append(migrations, tokenMigration{deviceID: deviceID, tokenHash: token})
		}
	}
	if err := rows.Err(); err != nil {
		return fmt.Errorf("iterate device pairing tokens for migration: %w", err)
	}

	for _, migration := range migrations {
		if _, err := s.db.Exec(
			`UPDATE device_pairings SET token = ?, token_hash = ? WHERE device_id = ?`,
			migration.tokenHash,
			migration.tokenHash,
			migration.deviceID,
		); err != nil {
			return fmt.Errorf("migrate device pairing token %q: %w", migration.deviceID, err)
		}
	}

	return nil
}

func (s *Store) hasColumn(table string, column string) (bool, error) {
	rows, err := s.db.Query(`PRAGMA table_info(` + table + `)`)
	if err != nil {
		return false, fmt.Errorf("read table info %q: %w", table, err)
	}
	defer rows.Close()

	for rows.Next() {
		var cid int
		var name string
		var columnType string
		var notNull int
		var defaultValue any
		var pk int
		if err := rows.Scan(&cid, &name, &columnType, &notNull, &defaultValue, &pk); err != nil {
			return false, fmt.Errorf("scan table info %q: %w", table, err)
		}
		if name == column {
			return true, nil
		}
	}
	if err := rows.Err(); err != nil {
		return false, fmt.Errorf("iterate table info %q: %w", table, err)
	}
	return false, nil
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

func (s *Store) GetCodingSession(id string) (CodingSession, error) {
	row := s.db.QueryRow(
		`SELECT id, provider_id, project_path, state, started_at, last_activity_at, last_event_type
		 FROM coding_sessions
		 WHERE id = ?`,
		id,
	)

	session, err := scanCodingSession(row)
	if err != nil {
		return CodingSession{}, fmt.Errorf("get coding session %q: %w", id, err)
	}

	return session, nil
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
	tokenHash := HashToken(device.Token)
	_, err := s.db.Exec(
		`INSERT INTO device_pairings (device_id, name, token, token_hash, paired_at, last_seen_at)
		 VALUES (?, ?, ?, ?, ?, ?)
		 ON CONFLICT(device_id) DO UPDATE SET
		   name = excluded.name,
		   token = excluded.token,
		   token_hash = excluded.token_hash,
		   paired_at = excluded.paired_at,
		   last_seen_at = excluded.last_seen_at`,
		device.DeviceID,
		device.Name,
		tokenHash,
		tokenHash,
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

func (s *Store) ListDevicePairings() ([]DevicePairing, error) {
	rows, err := s.db.Query(
		`SELECT device_id, name, token, paired_at, last_seen_at
		 FROM device_pairings
		 ORDER BY last_seen_at DESC, paired_at DESC, device_id ASC`,
	)
	if err != nil {
		return nil, fmt.Errorf("list device pairings: %w", err)
	}
	defer rows.Close()

	var devices []DevicePairing
	for rows.Next() {
		device, err := scanDevicePairing(rows)
		if err != nil {
			return nil, err
		}
		devices = append(devices, device)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate device pairings: %w", err)
	}

	return devices, nil
}

func (s *Store) GetDevicePairingByToken(token string) (DevicePairing, error) {
	tokenHash := HashToken(token)
	row := s.db.QueryRow(
		`SELECT device_id, name, token, paired_at, last_seen_at
		 FROM device_pairings
		 WHERE token_hash = ?`,
		tokenHash,
	)

	device, err := scanDevicePairing(row)
	if err != nil {
		return DevicePairing{}, fmt.Errorf("get device pairing by token: %w", err)
	}

	return device, nil
}

func HashToken(token string) string {
	if isStoredTokenHash(token) {
		return token
	}
	sum := sha256.Sum256([]byte(token))
	return tokenHashPrefix + hex.EncodeToString(sum[:])
}

func isStoredTokenHash(token string) bool {
	return strings.HasPrefix(token, tokenHashPrefix)
}

const tokenHashPrefix = "sha256:"

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

func (s *Store) ListSettings() ([]Setting, error) {
	rows, err := s.db.Query(
		`SELECT key, value
		 FROM settings
		 ORDER BY key ASC`,
	)
	if err != nil {
		return nil, fmt.Errorf("list settings: %w", err)
	}
	defer rows.Close()

	var settings []Setting
	for rows.Next() {
		setting, err := scanSetting(rows)
		if err != nil {
			return nil, err
		}
		settings = append(settings, setting)
	}
	if err := rows.Err(); err != nil {
		return nil, fmt.Errorf("iterate settings: %w", err)
	}

	return settings, nil
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

func scanSetting(row scanner) (Setting, error) {
	var setting Setting
	if err := row.Scan(&setting.Key, &setting.Value); err != nil {
		return Setting{}, fmt.Errorf("scan setting: %w", err)
	}
	return setting, nil
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
