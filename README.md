# CodeGauge

CodeGauge is a LAN-only Android dashboard and local companion for monitoring AI coding quota, reset windows, and coding-session events.

The implementation follows [CodeGauge-完整方案.md](CodeGauge-完整方案.md).

## Structure

```text
CodeGauge/
  android/app/    Android app
  companion/      Go companion
  hooks/          Claude hooks snippets and installer
  scripts/        macOS install and service management scripts
  docs/           Implementation notes and plans
```

## Build

```bash
./gradlew :android:app:assembleDebug
```

Build a signed Android release APK after creating local-only
`keystore.properties`:

```bash
scripts/build-android-release.sh
```

See [docs/android-release.md](docs/android-release.md) for keystore setup and
the release verification checklist.

```bash
cd companion
go test ./...
go run ./cmd/codegauge
```

## macOS Install

Install the Companion as a user LaunchAgent and merge Claude hooks:

```bash
scripts/install-macos.sh
```

Useful service commands after install:

```bash
~/.codegauge/bin/codegaugectl status
~/.codegauge/bin/codegaugectl logs
~/.codegauge/bin/codegaugectl pair-code
~/.codegauge/bin/codegaugectl restart
```
