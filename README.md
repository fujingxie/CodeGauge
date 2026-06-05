# CodeGauge

CodeGauge is a LAN-only Android dashboard and local companion for monitoring AI coding quota, reset windows, and coding-session events.

The implementation follows [CodeGauge-完整方案.md](CodeGauge-完整方案.md).

## Structure

```text
CodeGauge/
  android/app/    Android app
  companion/      Go companion
  hooks/          Claude hooks snippets and installer, pending T8
  docs/           Implementation notes and plans
```

## Build

```bash
./gradlew :android:app:assembleDebug
```

```bash
cd companion
go test ./...
go run ./cmd/codegauge
```
