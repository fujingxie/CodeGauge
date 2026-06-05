# CodeGauge Changelog

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
