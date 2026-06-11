#!/usr/bin/env node
import { copyFile, mkdir, readFile, writeFile } from "node:fs/promises";
import path from "node:path";

const DEFAULT_HOOK_URL = "http://127.0.0.1:8765/api/v1/hooks/claude";

async function main() {
  const [settingsPath, snippetPath, hookURL = DEFAULT_HOOK_URL] = process.argv.slice(2);
  if (!settingsPath || !snippetPath) {
    throw new Error("usage: merge-claude-settings.mjs <settings.json> <snippet.json> [hook-url]");
  }

  const { value: settings, existed, raw } = await readJSONOrDefault(settingsPath, {});
  const snippet = await readJSON(snippetPath);
  const configuredSnippet = withHookURL(snippet, hookURL);
  const next = mergeHooks(settings, configuredSnippet);
  const formatted = `${JSON.stringify(next, null, 2)}\n`;

  if (raw === formatted) {
    console.log(`CodeGauge hooks already installed in ${settingsPath}`);
    return;
  }

  await mkdir(path.dirname(settingsPath), { recursive: true });
  if (existed) {
    const backupPath = `${settingsPath}.bak-${timestamp()}`;
    await copyFile(settingsPath, backupPath);
    console.log(`Backed up existing settings to ${backupPath}`);
  }
  await writeFile(settingsPath, formatted);
  console.log(`Installed CodeGauge hooks into ${settingsPath}`);
}

function mergeHooks(settings, snippet) {
  if (!isObject(settings)) {
    throw new Error("settings root must be a JSON object");
  }
  if (!isObject(snippet.hooks)) {
    throw new Error("snippet must contain a hooks object");
  }

  const next = structuredClone(settings);
  if (next.hooks === undefined) {
    next.hooks = {};
  }
  if (!isObject(next.hooks)) {
    throw new Error("settings.hooks must be a JSON object");
  }

  for (const [eventName, snippetGroups] of Object.entries(snippet.hooks)) {
    if (!Array.isArray(snippetGroups)) {
      throw new Error(`snippet.hooks.${eventName} must be an array`);
    }
    if (next.hooks[eventName] === undefined) {
      next.hooks[eventName] = [];
    }
    if (!Array.isArray(next.hooks[eventName])) {
      throw new Error(`settings.hooks.${eventName} must be an array`);
    }

    for (const snippetGroup of snippetGroups) {
      const targetGroup = findOrCreateGroup(next.hooks[eventName], snippetGroup);
      mergeHookHandlers(targetGroup, snippetGroup);
    }
  }

  return next;
}

function findOrCreateGroup(groups, snippetGroup) {
  const matcherKey = matcherOf(snippetGroup);
  const existing = groups.find((group) => matcherOf(group) === matcherKey);
  if (existing) {
    if (!Array.isArray(existing.hooks)) {
      throw new Error("existing hook group has non-array hooks");
    }
    return existing;
  }

  const created = { ...structuredClone(snippetGroup), hooks: [] };
  groups.push(created);
  return created;
}

function mergeHookHandlers(targetGroup, snippetGroup) {
  if (!Array.isArray(snippetGroup.hooks)) {
    throw new Error("snippet hook group must contain a hooks array");
  }
  for (const handler of snippetGroup.hooks) {
    const canonicalHandler = stableStringify(handler);
    const exists = targetGroup.hooks.some((existing) => stableStringify(existing) === canonicalHandler);
    if (!exists) {
      targetGroup.hooks.push(structuredClone(handler));
    }
  }
}

function withHookURL(snippet, hookURL) {
  const next = structuredClone(snippet);
  for (const groups of Object.values(next.hooks ?? {})) {
    for (const group of groups) {
      for (const handler of group.hooks ?? []) {
        if (handler.type === "http" && typeof handler.url === "string") {
          handler.url = hookURL;
        }
        if (handler.type === "command" && typeof handler.command === "string") {
          handler.command = handler.command.replaceAll(DEFAULT_HOOK_URL, hookURL);
        }
      }
    }
  }
  return next;
}

function matcherOf(group) {
  if (!isObject(group)) {
    throw new Error("hook group must be an object");
  }
  return Object.hasOwn(group, "matcher") ? String(group.matcher) : "";
}

async function readJSON(filePath) {
  try {
    return JSON.parse(await readFile(filePath, "utf8"));
  } catch (error) {
    throw new Error(`read JSON ${filePath}: ${error.message}`);
  }
}

async function readJSONOrDefault(filePath, defaultValue) {
  try {
    const raw = await readFile(filePath, "utf8");
    return { value: JSON.parse(raw), existed: true, raw };
  } catch (error) {
    if (error.code === "ENOENT") {
      return { value: defaultValue, existed: false, raw: "" };
    }
    throw new Error(`read JSON ${filePath}: ${error.message}`);
  }
}

function stableStringify(value) {
  if (Array.isArray(value)) {
    return `[${value.map(stableStringify).join(",")}]`;
  }
  if (isObject(value)) {
    return `{${Object.keys(value)
      .sort()
      .map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`)
      .join(",")}}`;
  }
  return JSON.stringify(value);
}

function isObject(value) {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function timestamp() {
  return new Date().toISOString().replaceAll(/[-:.TZ]/g, "").slice(0, 14);
}

main().catch((error) => {
  console.error(error.message);
  process.exit(1);
});
