# CodeGauge Changelog

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
