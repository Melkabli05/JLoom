import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createTextFile } from "../src/merge/createTextFile.ts";

function tempProject(): string {
  return mkdtempSync(path.join(os.tmpdir(), "jloom-createtextfile-"));
}

test("writes a new file, creating parent directories", () => {
  const root = tempProject();
  try {
    createTextFile(root, {
      type: "org.openrewrite.text.CreateTextFile",
      relativeFileName: "src/main/resources/db/migration/V2__x.sql",
      fileContents: "create table x();\n",
      overwriteExisting: false,
    });
    const written = readFileSync(path.join(root, "src/main/resources/db/migration/V2__x.sql"), "utf8");
    assert.strictEqual(written, "create table x();\n");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("does not overwrite an existing file when overwriteExisting is false", () => {
  const root = tempProject();
  try {
    const target = path.join(root, "existing.sql");
    writeFileSync(target, "original\n", "utf8");
    createTextFile(root, {
      type: "org.openrewrite.text.CreateTextFile",
      relativeFileName: "existing.sql",
      fileContents: "replacement\n",
      overwriteExisting: false,
    });
    assert.strictEqual(readFileSync(target, "utf8"), "original\n");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("overwrites an existing file when overwriteExisting is true", () => {
  const root = tempProject();
  try {
    const target = path.join(root, "existing.sql");
    writeFileSync(target, "original\n", "utf8");
    createTextFile(root, {
      type: "org.openrewrite.text.CreateTextFile",
      relativeFileName: "existing.sql",
      fileContents: "replacement\n",
      overwriteExisting: true,
    });
    assert.strictEqual(readFileSync(target, "utf8"), "replacement\n");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
