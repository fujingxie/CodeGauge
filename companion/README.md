# CodeGauge Companion

Local Go companion for CodeGauge.

## Run

```bash
go run ./cmd/codegauge
```

Defaults:

- `CODEGAUGE_HOST=0.0.0.0`
- `CODEGAUGE_PORT=8765`
- `CODEGAUGE_COLLECT_INTERVAL_SECONDS=60`
- `CODEGAUGE_WARNING_THRESHOLD=80`
- `CODEGAUGE_CRITICAL_THRESHOLD=95`
- `CODEGAUGE_CCUSAGE_PATH=ccusage`

## Verify

```bash
go test ./...
curl http://127.0.0.1:8765/api/v1/health
```

Run the real `ccusage` integration test when `ccusage` is installed:

```bash
CODEGAUGE_REAL_CCUSAGE_PATH="$(command -v ccusage)" go test ./internal/collector -run TestRealCCUsageCollectsAtLeastOneWindow -count=1
```

## Store

The Store uses pure Go SQLite via `modernc.org/sqlite` and applies embedded migrations on open.

Implemented entities:

- Provider
- QuotaWindow
- CodingSession
- Event
- DevicePairing
- Setting

## Collector

The collector reads local `ccusage 20.0.6` JSON output and writes quota windows to Store.

- Claude 5h window: `ccusage claude blocks --json --recent --offline --token-limit max`
- Claude weekly usage: `ccusage claude daily --json --offline`
- Codex weekly usage: `ccusage codex daily --json --offline`

Unknown quota fields stay empty instead of being guessed.

Next task: T4 - LAN server status/quota/pairing APIs.
