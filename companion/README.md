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

## Verify

```bash
go test ./...
curl http://127.0.0.1:8765/api/v1/health
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

Next task: T3 - Collector using local `ccusage` / CLI output.
