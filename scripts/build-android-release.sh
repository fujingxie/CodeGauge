#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
KEYSTORE_PROPERTIES="$REPO_ROOT/keystore.properties"
APK_PATH="$REPO_ROOT/android/app/build/outputs/apk/release/app-release.apk"

main() {
  cd "$REPO_ROOT"
  require_keystore_properties

  ./gradlew :android:app:testDebugUnitTest
  ./gradlew :android:app:assembleRelease

  if [[ ! -f "$APK_PATH" ]]; then
    echo "Release APK not found: $APK_PATH" >&2
    exit 1
  fi

  echo "Release APK:"
  echo "$APK_PATH"
}

require_keystore_properties() {
  if [[ -f "$KEYSTORE_PROPERTIES" ]]; then
    return
  fi

  cat >&2 <<'MESSAGE'
keystore.properties not found.

Create a local release keystore first:

  mkdir -p release
  keytool -genkeypair \
    -v \
    -keystore release/codegauge-release.jks \
    -alias codegauge \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000

Then copy the template and fill in passwords:

  cp keystore.properties.example keystore.properties
  vim keystore.properties

keystore.properties and *.jks are ignored by git.
MESSAGE
  exit 1
}

main "$@"
