import assert from "node:assert/strict";
import { execFile } from "node:child_process";
import { mkdtemp, readFile, readdir, writeFile } from "node:fs/promises";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import test from "node:test";

const scriptDir = path.dirname(fileURLToPath(import.meta.url));
const mergeScript = path.join(scriptDir, "merge-claude-settings.mjs");
const snippetPath = path.join(scriptDir, "claude-settings.snippet.json");

test("creates settings with CodeGauge hooks", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "codegauge-hooks-"));
  const settingsPath = path.join(dir, ".claude", "settings.json");

  await runMerge(settingsPath);

  const settings = await readJSON(settingsPath);
  assert.ok(settings.hooks.SessionStart);
  assert.ok(settings.hooks.Notification);
  assert.ok(settings.hooks.Stop);
  assert.equal(settings.hooks.Stop[0].hooks[0].url, "http://127.0.0.1:8765/api/v1/hooks/claude");
});

test("preserves existing settings and hook handlers", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "codegauge-hooks-"));
  const settingsPath = path.join(dir, "settings.json");
  await writeFile(
    settingsPath,
    JSON.stringify(
      {
        model: "opus",
        hooks: {
          Stop: [
            {
              hooks: [{ type: "command", command: "echo existing" }],
            },
          ],
        },
      },
      null,
      2,
    ),
  );

  await runMerge(settingsPath);

  const settings = await readJSON(settingsPath);
  assert.equal(settings.model, "opus");
  assert.equal(settings.hooks.Stop[0].hooks[0].command, "echo existing");
  assert.equal(settings.hooks.Stop[0].hooks[1].type, "http");

  const entries = await readdir(dir);
  assert.equal(entries.filter((entry) => entry.startsWith("settings.json.bak-")).length, 1);
});

test("is idempotent", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "codegauge-hooks-"));
  const settingsPath = path.join(dir, "settings.json");

  await runMerge(settingsPath);
  const first = await readFile(settingsPath, "utf8");
  await runMerge(settingsPath);
  const second = await readFile(settingsPath, "utf8");

  assert.equal(second, first);
});

test("supports custom hook URL", async () => {
  const dir = await mkdtemp(path.join(os.tmpdir(), "codegauge-hooks-"));
  const settingsPath = path.join(dir, "settings.json");
  const url = "http://127.0.0.1:18768/api/v1/hooks/claude";

  await runMerge(settingsPath, url);

  const settings = await readJSON(settingsPath);
  assert.equal(settings.hooks.Notification[0].hooks[0].url, url);
  assert.match(settings.hooks.SessionStart[0].hooks[0].command, /18768\/api\/v1\/hooks\/claude/);
});

async function runMerge(settingsPath, hookURL) {
  const args = [mergeScript, settingsPath, snippetPath];
  if (hookURL) {
    args.push(hookURL);
  }
  await new Promise((resolve, reject) => {
    execFile(process.execPath, args, (error, stdout, stderr) => {
      if (error) {
        reject(new Error(`merge failed: ${error.message}\nstdout=${stdout}\nstderr=${stderr}`));
        return;
      }
      resolve();
    });
  });
}

async function readJSON(filePath) {
  return JSON.parse(await readFile(filePath, "utf8"));
}
