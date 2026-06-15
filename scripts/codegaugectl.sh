#!/usr/bin/env bash
set -euo pipefail

INSTALL_HOME="${CODEGAUGE_INSTALL_HOME:-"$HOME/.codegauge"}"
LABEL="${CODEGAUGE_LABEL:-com.codegauge.companion}"
PLIST_PATH="${CODEGAUGE_PLIST_PATH:-"$HOME/Library/LaunchAgents/$LABEL.plist"}"
ENV_FILE="${CODEGAUGE_ENV_FILE:-"$INSTALL_HOME/codegauge.env"}"
LOG_DIR="${CODEGAUGE_LOG_DIR:-"$INSTALL_HOME/log"}"
STDOUT_LOG="$LOG_DIR/companion.log"
STDERR_LOG="$LOG_DIR/companion.err.log"

if [[ -f "$ENV_FILE" ]]; then
  # shellcheck disable=SC1090
  source "$ENV_FILE"
fi

PORT="${CODEGAUGE_PORT:-8765}"
HEALTH_URL="http://127.0.0.1:$PORT/api/v1/health"

main() {
  local command="${1:-help}"
  case "$command" in
    start)
      service_start
      ;;
    stop)
      service_stop
      ;;
    restart)
      service_stop
      sleep 1
      service_start
      ;;
    status)
      service_status
      ;;
    logs)
      service_logs
      ;;
    pair-code)
      service_pair_code
      ;;
    health)
      service_health
      ;;
    uninstall)
      service_uninstall
      ;;
    help | -h | --help)
      usage
      ;;
    *)
      echo "Unknown command: $command" >&2
      usage >&2
      exit 2
      ;;
  esac
}

service_start() {
  require_launchctl
  require_plist

  local domain
  domain="$(launchd_domain)"
  launchctl enable "$domain/$LABEL" >/dev/null 2>&1 || true
  if launchctl print "$domain/$LABEL" >/dev/null 2>&1; then
    launchctl kickstart -k "$domain/$LABEL"
  else
    launchctl bootstrap "$domain" "$PLIST_PATH"
  fi
  echo "CodeGauge Companion started ($LABEL)"
}

service_stop() {
  require_launchctl

  local domain
  domain="$(launchd_domain)"
  if launchctl print "$domain/$LABEL" >/dev/null 2>&1; then
    launchctl bootout "$domain/$LABEL"
    echo "CodeGauge Companion stopped ($LABEL)"
    return
  fi
  echo "CodeGauge Companion is not loaded ($LABEL)"
}

service_status() {
  echo "Label: $LABEL"
  echo "Plist: $PLIST_PATH"
  echo "Config: $ENV_FILE"
  echo "Logs: $STDOUT_LOG"
  echo "Health: $HEALTH_URL"

  if command -v launchctl >/dev/null 2>&1; then
    local domain
    domain="$(launchd_domain)"
    if launchctl print "$domain/$LABEL" >/dev/null 2>&1; then
      echo "Launchd: loaded"
    else
      echo "Launchd: not loaded"
    fi
  else
    echo "Launchd: unavailable"
  fi

  service_health || true
}

service_logs() {
  mkdir -p "$LOG_DIR"
  touch "$STDOUT_LOG" "$STDERR_LOG"
  tail -n "${CODEGAUGE_LOG_LINES:-80}" -f "$STDOUT_LOG" "$STDERR_LOG"
}

service_pair_code() {
  local pair_code
  pair_code="$(
    grep -h "CodeGauge pairing code:" "$STDOUT_LOG" "$STDERR_LOG" 2>/dev/null \
      | tail -n 1 \
      | sed 's/^.*CodeGauge pairing code: //'
  )"
  if [[ -z "$pair_code" ]]; then
    echo "No pairing code found in logs yet. Run: codegaugectl logs" >&2
    exit 1
  fi
  echo "$pair_code"
}

service_health() {
  if ! command -v curl >/dev/null 2>&1; then
    echo "Health: curl not found"
    return 1
  fi
  local response
  if response="$(curl -fsS "$HEALTH_URL" 2>/dev/null)" && [[ "$response" == *'"ok":true'* ]]; then
    echo "$response"
    return 0
  fi
  echo "Health: unavailable"
  return 1
}

service_uninstall() {
  service_stop || true
  rm -f "$PLIST_PATH"
  echo "Removed $PLIST_PATH"
  echo "Kept installed files and data under $INSTALL_HOME"
}

launchd_domain() {
  printf 'gui/%s' "$(id -u)"
}

require_launchctl() {
  if ! command -v launchctl >/dev/null 2>&1; then
    echo "launchctl is required on macOS" >&2
    exit 1
  fi
}

require_plist() {
  if [[ ! -f "$PLIST_PATH" ]]; then
    echo "LaunchAgent plist not found: $PLIST_PATH" >&2
    echo "Run scripts/install-macos.sh first." >&2
    exit 1
  fi
}

usage() {
  cat <<'USAGE'
Usage: codegaugectl <command>

Commands:
  start       Load and start the launchd service
  stop        Stop and unload the launchd service
  restart     Restart the launchd service
  status      Show paths, launchd state, and health
  health      Check the local health endpoint
  logs        Tail Companion logs
  pair-code   Print the latest pairing code from logs
  uninstall   Unload service and remove LaunchAgent plist, keeping data
USAGE
}

main "$@"
