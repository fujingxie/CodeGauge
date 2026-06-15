#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# shellcheck disable=SC1091
source "$SCRIPT_DIR/lib/macos-install.sh"

INSTALL_HOME="${CODEGAUGE_INSTALL_HOME:-"$HOME/.codegauge"}"
LABEL="${CODEGAUGE_LABEL:-com.codegauge.companion}"
BIN_DIR="$INSTALL_HOME/bin"
LOG_DIR="$INSTALL_HOME/log"
VAR_DIR="$INSTALL_HOME/var"
ENV_FILE="${CODEGAUGE_ENV_FILE:-"$INSTALL_HOME/codegauge.env"}"
RUNNER_PATH="$BIN_DIR/codegauge-runner"
BINARY_PATH="$BIN_DIR/codegauge"
CONTROL_PATH="$BIN_DIR/codegaugectl"
PLIST_DIR="${CODEGAUGE_LAUNCH_AGENTS_DIR:-"$HOME/Library/LaunchAgents"}"
PLIST_PATH="${CODEGAUGE_PLIST_PATH:-"$PLIST_DIR/$LABEL.plist"}"
STDOUT_LOG="$LOG_DIR/companion.log"
STDERR_LOG="$LOG_DIR/companion.err.log"

START_SERVICE=true
INSTALL_HOOKS=true
GO_BIN=""

main() {
  parse_args "$@"
  require_macos
  GO_BIN="$(resolve_go_bin)"

  mkdir -p "$BIN_DIR" "$LOG_DIR" "$VAR_DIR" "$PLIST_DIR"

  build_companion
  install_control_script
  write_config
  cg_write_runner "$RUNNER_PATH" "$ENV_FILE" "$BINARY_PATH"
  cg_write_launchd_plist "$PLIST_PATH" "$LABEL" "$RUNNER_PATH" "$INSTALL_HOME" "$STDOUT_LOG" "$STDERR_LOG"

  if [[ "$INSTALL_HOOKS" == true ]]; then
    install_claude_hooks
  fi
  if [[ "$START_SERVICE" == true ]]; then
    "$CONTROL_PATH" restart
  fi

  print_next_steps
}

parse_args() {
  while (($# > 0)); do
    case "$1" in
      --no-start)
        START_SERVICE=false
        ;;
      --no-hooks)
        INSTALL_HOOKS=false
        ;;
      -h | --help)
        usage
        exit 0
        ;;
      *)
        echo "Unknown option: $1" >&2
        usage >&2
        exit 2
        ;;
    esac
    shift
  done
}

require_macos() {
  if [[ "$(uname -s)" != "Darwin" ]]; then
    echo "scripts/install-macos.sh only supports macOS" >&2
    exit 1
  fi
}

resolve_go_bin() {
  if [[ -n "${CODEGAUGE_GO_BIN:-}" ]]; then
    if [[ ! -x "$CODEGAUGE_GO_BIN" ]]; then
      echo "CODEGAUGE_GO_BIN is not executable: $CODEGAUGE_GO_BIN" >&2
      exit 1
    fi
    printf '%s' "$CODEGAUGE_GO_BIN"
    return
  fi
  if command -v go >/dev/null 2>&1; then
    command -v go
    return
  fi

  local candidate
  for candidate in \
    /opt/homebrew/bin/go \
    /usr/local/bin/go \
    /usr/local/Cellar/go/*/libexec/bin/go; do
    if [[ -x "$candidate" ]]; then
      printf '%s' "$candidate"
      return
    fi
  done

  echo "go is required. Install Go or set CODEGAUGE_GO_BIN=/path/to/go." >&2
  exit 1
}

build_companion() {
  echo "Building CodeGauge Companion..."
  (cd "$REPO_ROOT/companion" && "$GO_BIN" build -o "$BINARY_PATH" ./cmd/codegauge)
}

install_control_script() {
  cp "$SCRIPT_DIR/codegaugectl.sh" "$CONTROL_PATH"
  chmod 0755 "$CONTROL_PATH"
}

write_config() {
  local host="${CODEGAUGE_HOST:-0.0.0.0}"
  local port="${CODEGAUGE_PORT:-8765}"
  local db_path="${CODEGAUGE_DB_PATH:-"$VAR_DIR/codegauge.db"}"
  local ccusage_path
  local codex_path
  local service_path

  ccusage_path="$(cg_resolve_command "${CODEGAUGE_CCUSAGE_PATH:-}" ccusage)"
  codex_path="$(resolve_codex_path)"
  service_path="$(cg_service_path "${CODEGAUGE_SERVICE_PATH:-}" "$ccusage_path" "$codex_path")"

  cg_write_env_file "$ENV_FILE" \
    "PATH=$service_path" \
    "CODEGAUGE_HOST=$host" \
    "CODEGAUGE_PORT=$port" \
    "CODEGAUGE_DB_PATH=$db_path" \
    "CODEGAUGE_CCUSAGE_PATH=$ccusage_path" \
    "CODEGAUGE_CODEX_PATH=$codex_path" \
    "CODEGAUGE_SERVER_NAME=${CODEGAUGE_SERVER_NAME:-CodeGauge Companion}" \
    "CODEGAUGE_TRAY_ENABLED=${CODEGAUGE_TRAY_ENABLED:-true}" \
    "CODEGAUGE_COLLECT_INTERVAL_SECONDS=${CODEGAUGE_COLLECT_INTERVAL_SECONDS:-60}" \
    "CODEGAUGE_WATCH_INTERVAL_SECONDS=${CODEGAUGE_WATCH_INTERVAL_SECONDS:-10}" \
    "CODEGAUGE_WARNING_THRESHOLD=${CODEGAUGE_WARNING_THRESHOLD:-80}" \
    "CODEGAUGE_CRITICAL_THRESHOLD=${CODEGAUGE_CRITICAL_THRESHOLD:-95}" \
    "CODEGAUGE_PAIR_CODE_TTL_SECONDS=${CODEGAUGE_PAIR_CODE_TTL_SECONDS:-600}" \
    "CODEGAUGE_PAIR_CODE_MAX_ATTEMPTS=${CODEGAUGE_PAIR_CODE_MAX_ATTEMPTS:-5}"
}

resolve_codex_path() {
  if [[ -n "${CODEGAUGE_CODEX_PATH:-}" ]]; then
    printf '%s' "$CODEGAUGE_CODEX_PATH"
    return
  fi
  if [[ -x "/Applications/Codex.app/Contents/Resources/codex" ]]; then
    printf '%s' "/Applications/Codex.app/Contents/Resources/codex"
    return
  fi
  cg_resolve_command "" codex
}

install_claude_hooks() {
  local port="${CODEGAUGE_PORT:-8765}"
  CODEGAUGE_HOOK_URL="http://127.0.0.1:$port/api/v1/hooks/claude" bash "$REPO_ROOT/hooks/install-hooks.sh"
}

print_next_steps() {
  cat <<SUMMARY

CodeGauge Companion installed.

Binary:  $BINARY_PATH
Config:  $ENV_FILE
Service: $PLIST_PATH
Logs:    $STDOUT_LOG

Useful commands:
  $CONTROL_PATH status
  $CONTROL_PATH logs
  $CONTROL_PATH pair-code
  $CONTROL_PATH restart

SUMMARY
}

usage() {
  cat <<'USAGE'
Usage: scripts/install-macos.sh [options]

Options:
  --no-start   Install files and LaunchAgent plist without starting launchd
  --no-hooks   Skip Claude hooks installation
  -h, --help   Show this help

Environment overrides:
  CODEGAUGE_INSTALL_HOME
  CODEGAUGE_HOST
  CODEGAUGE_PORT
  CODEGAUGE_DB_PATH
  CODEGAUGE_CCUSAGE_PATH
  CODEGAUGE_CODEX_PATH
  CODEGAUGE_TRAY_ENABLED
  CODEGAUGE_GO_BIN
  CODEGAUGE_SERVICE_PATH
USAGE
}

main "$@"
