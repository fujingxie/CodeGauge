# CodeGauge Hooks

Claude Code hook snippet and installer for CodeGauge Companion.

Default target endpoint: `http://127.0.0.1:8765/api/v1/hooks/claude`.

Install into `~/.claude/settings.json`:

```bash
./hooks/install-hooks.sh
```

Use a custom Companion port while testing:

```bash
CODEGAUGE_HOOK_URL="http://127.0.0.1:18768/api/v1/hooks/claude" ./hooks/install-hooks.sh
```

The installer creates a backup before changing an existing settings file and can be run repeatedly without duplicating CodeGauge hooks.
