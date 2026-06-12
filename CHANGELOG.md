# CodeGauge Changelog

## 2026-06-12 - Fix Android settings crash

- Fixed a Settings screen Compose recomposition crash caused by returning early from the `Column` content lambda after the initial loading state.
- Replaced the early return with a normal loading/content branch so the composition tree remains stable when settings data arrives.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified on the connected phone with adb: Dashboard to Settings switching no longer logs `AndroidRuntime` crashes.

## 2026-06-12 - Android settings screen

- Added Android settings models, parser, API client, repository, and exception type.
- Added `Settings` bottom tab alongside Dashboard and Activity.
- Added a Compose settings screen for notification switches, warning/critical thresholds, collect interval, save/refresh actions, and pair-again.
- Added Companion diagnostics and paired-device panels to the settings screen.
- Wired `GET /settings`, `PATCH /settings`, `GET /devices`, and `GET /diagnostics` into Android.
- Added tests for settings JSON parsing, PATCH request body creation, and repository behavior.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.

## 2026-06-12 - Settings prerequisite APIs

- Added Companion `GET`/`PATCH /api/v1/settings` with Bearer auth.
- Added typed settings defaults for notification toggles, warning/critical thresholds, quota reset/task-done notifications, and collect interval.
- Persisted settings updates in the existing SQLite `settings` key/value table.
- Added Companion `GET /api/v1/devices` with newest-first paired devices and no token exposure.
- Added Companion `GET /api/v1/diagnostics` summarizing server identity, provider/session/device counts, and latest event time.
- Added Store list APIs for device pairings and settings.
- Passed config defaults into the REST router from Companion startup.
- Added Store and Server tests for settings, devices, diagnostics, validation, ordering, and token redaction.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified a temporary Companion smoke test for pairing, settings default read, settings PATCH, devices, and diagnostics.

## 2026-06-12 - T14 precise Codex quota source

- Added optional Collector `PreciseSource` support that runs after `ccusage` and does not break fallback collection when it fails.
- Added Codex app-server precise source using `codex app-server --stdio` and `account/rateLimits/read`.
- Added parsing for Codex primary 5h and secondary weekly windows, mapping used percent to `percent_left` and reset epoch to `resets_at`.
- Preserved existing `ccusage` used/limit values when precise endpoint windows omit them.
- Added `CODEGAUGE_CODEX_PATH`, defaulting to `/Applications/Codex.app/Contents/Resources/codex` when available.
- Added unit tests for precise window merging, endpoint failure fallback, and Codex rate-limit parsing.
- Added skipped-by-default real Codex app-server integration test.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified real Codex rate-limit collection with `CODEGAUGE_REAL_CODEX_PATH=/Applications/Codex.app/Contents/Resources/codex`.
- Verified with a temporary Companion that `/status` reports Codex `5h` and `weekly` windows as `source=endpoint`.

## 2026-06-12 - T13 Glance widget

- Added a Glance home-screen widget for CodeGauge quota status.
- Added widget state formatting for Claude/Codex 5h and weekly windows, reset countdowns, empty state, and error state.
- Added SharedPreferences widget cache so Glance rendering reads local state only.
- Added WorkManager periodic refresh every 15 minutes.
- Wired widget refresh on pairing changes and foreground `/stream` messages.
- Added widget receiver metadata and Glance/WorkManager dependencies.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified manually on phone that the widget displays real quota data, opens the App when tapped, and refreshes after a stream-triggering hook.

## 2026-06-12 - T12 foreground listener and notifications

- Added Android foreground listener service that reads the encrypted pairing record and keeps `/api/v1/stream` connected.
- Added listener and event notification channels.
- Added Chinese notifications for quota alerts, quota recovery, task completion, and waiting-for-confirmation session updates.
- Added reconnect loop with backoff after WebSocket disconnects.
- Added notification mapper tests for waiting, done, warning/critical, reset, and ignored messages.
- Added quota recovery alert publishing when usage drops back below the warning threshold.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified manually on phone that foreground listening and hook notifications work.

## 2026-06-12 - T11 Android activity

- Added authenticated Companion `GET /api/v1/events?limit=...`.
- Added WebSocket `event_update` messages from `NotifyingStore.AddEvent`.
- Added Android activity models, `/events` client, stream parser, and stream client.
- Added Dashboard / Activity bottom tabs.
- Added Activity screen with current sessions, waiting-session highlight, event stream, pull refresh, and live WebSocket updates.
- Added tests for event history parsing, stream `event_update`, stream `session_update`, and stream `alert` parsing.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified manually on phone with Claude `Stop` and `Notification` hook payloads.

## 2026-06-11 - T10 Android dashboard

- Added Android dashboard models, `/status` parser, authenticated OkHttp client, and dashboard repository.
- Added the paired dashboard screen with connection status, pull-to-refresh, manual refresh, pair-again, Claude/Codex quota cards, source labels, reset text, and current session summary.
- Preserved nullable quota fields without inventing remaining percentages or reset times.
- Added tests for `/status` JSON parsing and dashboard formatting.
- Added Compose Material pull-refresh and JVM `org.json` test dependency.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified manually on phone: dashboard showed Claude/Codex cards, `Source: ccusage`, token usage, Claude 5h reset countdown, and Codex running session.

## 2026-06-11 - T9 Android pairing

- Replaced the placeholder Compose shell with an Android pairing screen.
- Added Android NSD discovery for `_codegauge._tcp.` and retained manual `IP:Port` pairing fallback.
- Added OkHttp pairing client for `POST /api/v1/pair`.
- Added encrypted pairing storage so the Companion token survives app restarts.
- Added pairing repository and parser tests for endpoint parsing, successful pairing persistence, and invalid pair code rejection.
- Added Android dependencies for Security Crypto, OkHttp, and coroutines.
- Verified `./gradlew :android:app:testDebugUnitTest`.
- Verified `./gradlew :android:app:assembleDebug`.
- Verified manually on phone: App discovered `CodeGauge Companion`, manual `192.168.1.4:18770` pairing worked, and reopening the App still showed `Paired`.

## 2026-06-11 - T8 Claude hooks installer

- Added `hooks/claude-settings.snippet.json` for Claude Code `SessionStart`, `Notification`, and `Stop` events.
- Added `hooks/install-hooks.sh` to install CodeGauge hooks into Claude settings.
- Added `hooks/merge-claude-settings.mjs` for safe JSON merging, backup creation, custom hook URL support, and idempotent installs.
- Used a command hook for `SessionStart` because Claude Code currently limits `SessionStart` to command/MCP hooks.
- Added Node tests covering new settings creation, existing settings preservation, idempotency, and custom hook URLs.
- Verified `node --test hooks/merge-claude-settings.test.mjs`.
- Verified `hooks/install-hooks.sh` against a temporary settings file.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.

## 2026-06-11 - T7 mDNS and tray

- Added mDNS advertising for `_codegauge._tcp.local.` with version, host, port, and server name TXT records.
- Added `internal/discovery` with testable advertiser lifecycle.
- Added desktop tray controller showing status, listening address, pairing code, version, and Quit.
- Added `internal/tray` systray adapter and lifecycle tests.
- Wired mDNS and tray startup into Companion main.
- Added `CODEGAUGE_TRAY_ENABLED=false` for CLI/manual test runs without a GUI tray.
- Fixed tray Quit so clicking it also exits the systray event loop, not only background workers.
- Added `fyne.io/systray` and `github.com/grandcat/zeroconf` dependencies; upgraded transitive DNS/net dependencies for Go 1.25 compatibility.
- Verified `GOCACHE=/private/tmp/codegauge-go-cache go test ./...` in `companion/`.
- Verified manually with `dns-sd -B _codegauge._tcp local`.
- Verified manually that the tray menu shows the pairing code and Quit exits the process.

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

## 2026-06-12 - Android UI 中文化与设计基线

- Localized Android visible UI copy across pairing, dashboard, activity, settings, bottom navigation, error messages, and notification fallbacks.
- Updated quota formatting to show Chinese reset/usage text and `令牌` units instead of English quota text.
- Normalized provider display names for Claude/Codex session rows and event details.
- Enlarged dashboard quota percentage text to better match the glanceable card design.
- Updated dashboard and notification formatter tests for the Chinese copy.
- Verified `./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug`.
- Verified on the connected phone with adb screenshots for Dashboard / Activity / Settings and no `AndroidRuntime` / `FATAL EXCEPTION` crashes in `logcat`.

## 2026-06-12 - Android dashboard 高精度还原

- Added Dashboard visual primitives for the high-fidelity design baseline, including dark surface tokens, compact pills, status dots, and a custom Canvas quota ring gauge.
- Reworked the Android Dashboard screen to match the design deck structure: connection sync strip, Claude/Codex circular quota cards, compact quota metrics, reset pill, and current session strip.
- Updated the app dark theme and bottom navigation colors/icons to align with the dashboard design direction.
- Kept the existing `/status` data flow and pull-to-refresh behavior while replacing the dashboard presentation layer.
- Verified `./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug`.
- Verified on the connected phone with adb after reinstalling and re-pairing Companion; checked the new Dashboard screenshot and confirmed no `AndroidRuntime` / `FATAL EXCEPTION` crashes in `logcat`.

## 2026-06-12 - Android Activity / Settings 高精度还原

- 将 Dashboard 高精度视觉 primitives 抽到共享 `ui/design` 包，供 Dashboard、Activity 和 Settings 复用。
- 重构 Activity 页为设计稿风格的连接状态条、当前会话卡片和事件流列表，保留原有 `/events` 与 `/stream` 数据流。
- 重构 Settings 页为设计稿风格的连接卡、通知设置卡、阈值滑杆、采集间隔分段控件、诊断和设备列表。
- 过滤 Activity 页面主动销毁 WebSocket 时的 `Socket closed` 误报，避免切换页面后污染崩溃排查日志。
- Verified `./gradlew :android:app:testDebugUnitTest :android:app:assembleDebug`.
- Verified on the connected phone with adb after reinstalling; checked Activity / Settings screenshots and confirmed no `AndroidRuntime` / `FATAL EXCEPTION` / `CodeGaugeActivity` crashes in `logcat`.
