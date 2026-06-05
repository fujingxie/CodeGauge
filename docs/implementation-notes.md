# Implementation Notes

## API gaps found before development

- Settings UI needs `GET /settings` and `PUT /settings`.
- Connection diagnostics UI needs `GET /diagnostics`.
- Pairing management needs `GET /devices` and `DELETE /devices/{device_id}`.

These endpoints should be added before implementing the Android settings screen.

## Android baseline

- App id: `com.codegauge`
- minSdk: 26
- targetSdk: 36
- UI: Jetpack Compose
- LAN API transport: HTTP cleartext for MVP
