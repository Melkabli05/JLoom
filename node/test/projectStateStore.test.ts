import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import {
  emptyState,
  loadState,
  saveState,
  withApplied,
  withBasePackage,
  withProjectName,
} from "../src/state/projectStateStore.ts";

function tempDir(): string {
  return mkdtempSync(path.join(os.tmpdir(), "jloom-state-"));
}

test("load returns empty state when no state file exists", () => {
  const dir = tempDir();
  try {
    assert.deepStrictEqual(loadState(dir), emptyState());
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("save then load round trips", () => {
  const dir = tempDir();
  try {
    let seeded = emptyState();
    seeded = withBasePackage(seeded, "com.acme.demo");
    seeded = withProjectName(seeded, "acme");
    seeded = withApplied(seeded, {
      id: "postgres",
      version: "1.0.0",
      appliedAt: "2026-01-01T00:00:00.000Z",
      answers: { db_name: "demo" },
    });

    saveState(dir, seeded);
    const loaded = loadState(dir);

    assert.strictEqual(loaded.basePackage, "com.acme.demo");
    assert.strictEqual(loaded.projectName, "acme");
    assert.deepStrictEqual(loaded.modules.map((m) => m.id), ["postgres"]);
    assert.strictEqual(loaded.modules[0]!.version, "1.0.0");
    assert.strictEqual(loaded.modules[0]!.answers.db_name, "demo");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("save creates the state directory if missing", () => {
  const dir = tempDir();
  try {
    const fresh = path.join(dir, "brand-new-project");
    saveState(fresh, withProjectName(emptyState(), "brand-new-project"));
    assert.strictEqual(loadState(fresh).projectName, "brand-new-project");
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
