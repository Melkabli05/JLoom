import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import yaml from "js-yaml";
import { apply } from "../src/orchestrate/moduleApplier.ts";
import { ModuleRegistry } from "../src/registry/moduleRegistry.ts";
import { loadState } from "../src/state/projectStateStore.ts";

const registry = ModuleRegistry.loadBundled();

function tempDir(): string {
  return mkdtempSync(path.join(os.tmpdir(), "jloom-apply-"));
}

test("dry run on a brand-new project writes nothing to disk", () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    const result = apply(registry, target, ["base"], {}, true, "com.acme.demo", "my-app");

    assert.strictEqual(result.kind, "dryRun");
    assert.strictEqual(existsSync(path.join(target, ".jloom", "state.json")), false);
    assert.strictEqual(existsSync(target), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("rejection leaves no state file on disk", () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "half-baked");
    const result = apply(registry, target, ["not-a-real-module"], {}, false, "com.acme.demo", "half-baked");

    assert.strictEqual(result.kind, "rejected");
    assert.strictEqual(existsSync(path.join(target, ".jloom", "state.json")), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// The following exercise the actual merge-into-real-files path end to end - coverage the Java
// suite never had in isolation, since it only ever reached this transitively through a real
// Gradle+OpenRewrite subprocess (network-dependent, ~seconds per test). Here it's in-process
// and needs no subprocess or network at all.

test("applying base scaffolds a real project directory", () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    const result = apply(registry, target, ["base"], {}, false, "com.acme.demo", "my-app");

    assert.strictEqual(result.kind, "applied");
    assert.ok(existsSync(path.join(target, "build.gradle.kts")));
    assert.ok(existsSync(path.join(target, "src/main/java/com/acme/demo/Application.java")));

    const state = loadState(target);
    assert.strictEqual(state.projectName, "my-app");
    assert.strictEqual(state.basePackage, "com.acme.demo");
    assert.deepStrictEqual(state.modules.map((m) => m.id), ["base"]);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("applying postgres after base merges the dependency and datasource config", () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    apply(registry, target, ["base"], {}, false, "com.acme.demo", "my-app");

    const result = apply(registry, target, ["postgres"], { "postgres.db_name": "demo" }, false, undefined, undefined);
    assert.strictEqual(result.kind, "applied");

    const buildFile = readFileSync(path.join(target, "build.gradle.kts"), "utf8");
    assert.ok(buildFile.includes("org.postgresql:postgresql"));
    assert.ok(buildFile.includes("spring-boot-starter-data-jpa"));

    const appYaml = yaml.load(readFileSync(path.join(target, "src/main/resources/application.yml"), "utf8")) as any;
    assert.strictEqual(appYaml.spring.datasource.url, "jdbc:postgresql://localhost:5432/demo");

    const state = loadState(target);
    assert.deepStrictEqual(state.modules.map((m) => m.id), ["base", "postgres"]);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
