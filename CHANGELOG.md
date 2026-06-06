# CodeGauge Changelog

## 2026-06-06 - T6 WebSocket stream

- Added authenticated `GET /api/v1/stream` WebSocket endpoint.
- Added `internal/stream` Hub for broadcasting incremental messages.
- Added `NotifyingStore` wrapper to publish `quota_update`, `session_update`, and `alert` messages from Store writes.
- Wired Collector, Watcher, and Claude Hook handling through the shared notifying Store.
- Added warning/critical threshold crossing detection for quota window updates.
- Added `github.com/gorilla/websocket` dependency.
- Added tests for WebSocket auth, real WS handshake, hook-driven `session_update`, quota updates, and threshold alerts.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.

## 2026-06-06 - T5 Claude hooks and process watcher

- Added `POST /api/v1/hooks/claude` for local Claude Code hook payloads.
- Restricted the Claude hook endpoint to loopback requests.
- Added HookReceiver handling for Claude `SessionStart`, `Notification`, and `Stop`.
- Stored hook-driven session state and raw hook payload events.
- Added Store `GetCodingSession` for state-preserving session updates.
- Added process Watcher for `claude` and `codex` processes with inferred session start/done events.
- Added `CODEGAUGE_WATCH_INTERVAL_SECONDS` config with a 10-second default.
- Wired the Watcher into Companion startup.
- Added unit tests for hook lifecycle handling, loopback endpoint protection, Store session lookup, and watcher inference.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified manually with temporary port `18768`: Claude Stop hook returned `{"ok":true}`, authenticated `/status` showed `claude` session `done`, and Watcher showed current Claude/Codex processes `running`.

## 2026-06-05 - T4 Companion LAN APIs

- Added authenticated LAN API router for `/api/v1/status` and `/api/v1/quota`.
- Kept `/api/v1/health` unauthenticated.
- Added `/api/v1/pair` with pair-code validation and token issuance.
- Added random 6-digit pair code generation when `CODEGAUGE_PAIR_CODE` is not set.
- Added `CODEGAUGE_DB_PATH`, `CODEGAUGE_PAIR_CODE`, and `CODEGAUGE_SERVER_NAME` config values.
- Wired Companion startup to open SQLite Store, start Collector, and serve API routes.
- Added Store queries for listing providers and looking up device pairings by token.
- Added server tests for auth, pairing, status, quota, and health behavior.
- Verified `go test ./...` in `companion/`.
- Verified manually with temporary port `18766`: health, unauthorized status, wrong pair code, correct pairing, authenticated status, and authenticated quota.

## 2026-06-05 - T3 Companion collector

- Added `ccusage` Collector with `CollectOnce` and periodic `Run(ctx, interval)`.
- Added `CODEGAUGE_CCUSAGE_PATH` config support for environments where nvm-installed binaries are not on PATH.
- Parsed Claude `ccusage claude blocks --json --recent --offline --token-limit max` into the 5h quota window.
- Parsed Claude/Codex `ccusage <provider> daily --json --offline` into weekly usage windows.
- Kept unknown quota fields (`percent_left`, `limit`, `resets_at`) null when `ccusage` does not expose them.
- Added unit tests for JSON parsing, Store writes, unavailable providers, and collector scheduling.
- Added a skipped-by-default real `ccusage` integration test enabled by `CODEGAUGE_REAL_CCUSAGE_PATH`.
- Verified `go test ./...` in `companion/`.
- Verified real `ccusage` collection with the locally installed `ccusage 20.0.6`.

## 2026-06-05 - T2 Companion SQLite store

- Added SQLite migrations for providers, quota windows, coding sessions, events, device pairings, and settings.
- Added Store models and constants matching the implementation plan.
- Added Store read/write APIs for all T2 entities.
- Added event listing with newest-first ordering and limit support.
- Added quota window upsert keyed by provider and window type.
- Added persistence tests covering reopen behavior.
- Added `modernc.org/sqlite` as the pure Go SQLite driver.
- Verified `go test ./...` in `companion/`.

## 2026-06-05 - T1 Companion skeleton

- Added `companion/go.mod`.
- Added Companion config loader with defaults for host, port, collect interval, and thresholds.
- Added `GET /api/v1/health` router returning `{ok:true,version}`.
- Added `cmd/codegauge` entrypoint with HTTP server startup and graceful shutdown.
- Added config and health endpoint unit tests.
- Verified `go test ./...` in `companion/`.
- Verified health endpoint using `CODEGAUGE_HOST=127.0.0.1 CODEGAUGE_PORT=18765 go run ./cmd/codegauge`.

## 2026-06-05 - T0 monorepo and Android foundation

- Moved the Android app module from `app/` to `android/app/`.
- Updated Gradle settings to include `:android:app`.
- Migrated the Android entry point from AppCompat/XML to a minimal Jetpack Compose shell.
- Renamed the Android namespace and application id to `com.codegauge`.
- Raised Android compile/target SDK to 36 and added the current AGP suppress flag.
- Added baseline Android permissions for LAN networking, NSD, foreground service, and notifications.
- Added cleartext LAN network security config for the Companion HTTP API.
- Added `STATUS.md` and `CHANGELOG.md` tracking files.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified `./gradlew :android:app:testDebugUnitTest`.
