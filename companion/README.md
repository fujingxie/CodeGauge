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
- `CODEGAUGE_DB_PATH=<user config dir>/CodeGauge/codegauge.db`
- `CODEGAUGE_PAIR_CODE=<generated at startup>`
- `CODEGAUGE_SERVER_NAME=CodeGauge Companion`

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

## LAN API

Unauthenticated:

- `GET /api/v1/health`
- `POST /api/v1/pair`

Authenticated with `Authorization: Bearer <token>`:

- `GET /api/v1/status`
- `GET /api/v1/quota`

Manual local verification:

```bash
CODEGAUGE_HOST=127.0.0.1 \
CODEGAUGE_PORT=18766 \
CODEGAUGE_DB_PATH=/private/tmp/codegauge-t4-manual.db \
CODEGAUGE_PAIR_CODE=123456 \
CODEGAUGE_CCUSAGE_PATH="$(command -v ccusage)" \
go run ./cmd/codegauge
```

```bash
curl -sS http://127.0.0.1:18766/api/v1/health
curl -i http://127.0.0.1:18766/api/v1/status

PAIR_RESPONSE=$(curl -sS -X POST http://127.0.0.1:18766/api/v1/pair \
  -H 'Content-Type: application/json' \
  -d '{"pair_code":"123456","device_name":"Manual Test"}')

TOKEN=$(node -e 'let s=""; process.stdin.on("data", d => s += d); process.stdin.on("end", () => console.log(JSON.parse(s).token))' <<< "$PAIR_RESPONSE")

curl -sS http://127.0.0.1:18766/api/v1/status -H "Authorization: Bearer $TOKEN"
curl -sS http://127.0.0.1:18766/api/v1/quota -H "Authorization: Bearer $TOKEN"
```

Next task: T5 - Claude HookReceiver and process Watcher.
