#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SETTINGS_PATH="${CODEGAUGE_CLAUDE_SETTINGS:-"$HOME/.claude/settings.json"}"
SNIPPET_PATH="${CODEGAUGE_CLAUDE_SNIPPET:-"$SCRIPT_DIR/claude-settings.snippet.json"}"
HOOK_URL="${CODEGAUGE_HOOK_URL:-"http://127.0.0.1:8765/api/v1/hooks/claude"}"

if ! command -v node >/dev/null 2>&1; then
  echo "node is required to merge Claude settings JSON" >&2
  exit 1
fi

node "$SCRIPT_DIR/merge-claude-settings.mjs" "$SETTINGS_PATH" "$SNIPPET_PATH" "$HOOK_URL"
