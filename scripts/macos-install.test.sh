#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/macos-install.sh"

TMP_DIR="$(mktemp -d "${TMPDIR:-/tmp}/codegauge-install-test.XXXXXX")"
trap 'rm -rf "$TMP_DIR"' EXIT

test_env_file_round_trip() {
  local env_file="$TMP_DIR/codegauge.env"
  cg_write_env_file "$env_file" \
    "CODEGAUGE_SERVER_NAME=CodeGauge Companion" \
    "CODEGAUGE_DB_PATH=$TMP_DIR/path with spaces/codegauge's.db"

  # shellcheck disable=SC1090
  source "$env_file"

  assert_equals "CodeGauge Companion" "$CODEGAUGE_SERVER_NAME"
  assert_equals "$TMP_DIR/path with spaces/codegauge's.db" "$CODEGAUGE_DB_PATH"
}

test_launchd_plist_escapes_xml() {
  local plist="$TMP_DIR/service.plist"
  cg_write_launchd_plist \
    "$plist" \
    "com.codegauge.test" \
    "$TMP_DIR/bin/codegauge & runner" \
    "$TMP_DIR/work <dir>" \
    "$TMP_DIR/log/out.log" \
    "$TMP_DIR/log/err.log"

  assert_file_contains "$plist" "com.codegauge.test"
  assert_file_contains "$plist" "codegauge &amp; runner"
  assert_file_contains "$plist" "work &lt;dir&gt;"
  assert_file_contains "$plist" "<key>RunAtLoad</key>"
  assert_file_contains "$plist" "<key>KeepAlive</key>"
}

test_runner_sources_env_and_execs_binary() {
  local env_file="$TMP_DIR/runner.env"
  local runner="$TMP_DIR/codegauge-runner"
  local binary="$TMP_DIR/fake-codegauge"
  local output="$TMP_DIR/runner-output.txt"

  cg_write_env_file "$env_file" \
    "CODEGAUGE_TEST_VALUE=ready" \
    "CODEGAUGE_TEST_OUTPUT=$output"

  cat >"$binary" <<'BINARY'
#!/usr/bin/env bash
set -euo pipefail
printf '%s' "$CODEGAUGE_TEST_VALUE" >"$CODEGAUGE_TEST_OUTPUT"
BINARY
  chmod 0755 "$binary"

  cg_write_runner "$runner" "$env_file" "$binary"
  "$runner"

  assert_equals "ready" "$(cat "$output")"
}

test_control_help() {
  bash "$SCRIPT_DIR/codegaugectl.sh" --help >/dev/null
}

test_service_path_includes_tool_dirs_once() {
  local service_path
  service_path="$(
    PATH="/usr/bin:/bin:/opt/tools/bin" \
      cg_service_path "" \
        "$TMP_DIR/node/bin/ccusage" \
        "/Applications/Codex.app/Contents/Resources/codex"
  )"

  assert_path_contains "$service_path" "$TMP_DIR/node/bin"
  assert_path_contains "$service_path" "/Applications/Codex.app/Contents/Resources"
  assert_path_contains "$service_path" "/usr/local/bin"
  assert_path_contains "$service_path" "/usr/bin"
  assert_equals "1" "$(path_count "$service_path" "/usr/bin")"
}

test_service_path_respects_override() {
  assert_equals "/custom/bin:/usr/bin" "$(cg_service_path "/custom/bin:/usr/bin" "/ignored/tool")"
}

assert_equals() {
  local expected="$1"
  local actual="$2"
  if [[ "$expected" != "$actual" ]]; then
    echo "assertion failed: expected [$expected], got [$actual]" >&2
    exit 1
  fi
}

assert_file_contains() {
  local file="$1"
  local expected="$2"
  if ! grep -Fq "$expected" "$file"; then
    echo "assertion failed: $file does not contain [$expected]" >&2
    echo "--- $file ---" >&2
    cat "$file" >&2
    exit 1
  fi
}

assert_path_contains() {
  local path_value="$1"
  local expected="$2"
  case ":$path_value:" in
    *":$expected:"*) ;;
    *)
      echo "assertion failed: PATH does not contain [$expected]" >&2
      echo "$path_value" >&2
      exit 1
      ;;
  esac
}

path_count() {
  local path_value="$1"
  local expected="$2"
  local count=0
  local part

  IFS=":" read -r -a parts <<<"$path_value"
  for part in "${parts[@]}"; do
    if [[ "$part" == "$expected" ]]; then
      count=$((count + 1))
    fi
  done
  printf '%s' "$count"
}

test_env_file_round_trip
test_launchd_plist_escapes_xml
test_runner_sources_env_and_execs_binary
test_control_help
test_service_path_includes_tool_dirs_once
test_service_path_respects_override

echo "macOS install script tests passed"
