PRAGMA foreign_keys = ON;

CREATE TABLE IF NOT EXISTS providers (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  plan_tier TEXT NOT NULL DEFAULT '',
  available INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS quota_windows (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  provider_id TEXT NOT NULL,
  window_type TEXT NOT NULL,
  percent_left INTEGER,
  used INTEGER,
  limit_value INTEGER,
  resets_at TEXT,
  source TEXT NOT NULL,
  updated_at TEXT NOT NULL,
  FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE,
  UNIQUE (provider_id, window_type)
);

CREATE TABLE IF NOT EXISTS coding_sessions (
  id TEXT PRIMARY KEY,
  provider_id TEXT NOT NULL,
  project_path TEXT NOT NULL,
  state TEXT NOT NULL,
  started_at TEXT NOT NULL,
  last_activity_at TEXT NOT NULL,
  last_event_type TEXT NOT NULL,
  FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS events (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  provider_id TEXT,
  type TEXT NOT NULL,
  payload TEXT NOT NULL,
  created_at TEXT NOT NULL,
  FOREIGN KEY (provider_id) REFERENCES providers(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_events_created_at ON events (created_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_events_provider_id ON events (provider_id);

CREATE TABLE IF NOT EXISTS device_pairings (
  device_id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  token TEXT NOT NULL,
  paired_at TEXT NOT NULL,
  last_seen_at TEXT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_device_pairings_token ON device_pairings (token);

CREATE TABLE IF NOT EXISTS settings (
  key TEXT PRIMARY KEY,
  value TEXT NOT NULL
);

PRAGMA user_version = 1;
