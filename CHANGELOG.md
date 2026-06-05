# CodeGauge Changelog

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
