import { test } from "node:test";
import assert from "node:assert/strict";
import { compose, composeUpgrade } from "../src/merge/recipeComposer.ts";
import { ModuleRegistry } from "../src/registry/moduleRegistry.ts";
import type { ModuleManifest } from "../src/registry/types.ts";

const registry = ModuleRegistry.loadBundled();

test("composes bundled fragments into a valid operation list", () => {
  const postgres = registry.require("postgres");
  const flyway = registry.require("flyway");

  const ops = compose([
    { manifest: postgres, answers: { db_name: "demo" } },
    { manifest: flyway, answers: {} },
  ]);

  assert.ok(ops.length > 0);
  for (const op of ops) {
    assert.ok(typeof op.type === "string" && op.type.length > 0);
  }
});

test("substitutes prompt answers into fragment text", () => {
  const postgres = registry.require("postgres");
  const ops = compose([{ manifest: postgres, answers: { db_name: "demo-db" } }]);

  const serialized = JSON.stringify(ops);
  assert.ok(serialized.includes("demo-db"));
  assert.ok(!serialized.includes("{{db_name}}"));
});

test("rejects a fragment whose resource is missing", () => {
  const phantom: ModuleManifest = {
    id: "phantom",
    version: "1.0.0",
    requires: [],
    conflicts: [],
    prompts: [],
    mergeRecipes: ["does-not-exist.yml"],
    fileTemplates: [],
    upgrades: [],
    scaffold: false,
  };

  assert.throws(
    () => compose([{ manifest: phantom, answers: {} }]),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.ok(err.message.includes("phantom"));
      assert.ok(err.message.includes("does-not-exist.yml"));
      return true;
    },
  );
});

test("composeUpgrade chains real bundled upgrade recipes", () => {
  const postgres = registry.require("postgres");
  assert.ok(postgres.upgrades.length >= 2);

  const steps = postgres.upgrades.slice(0, 2).map((u) => ({
    moduleId: "postgres",
    recipeResourcePath: u.recipe,
    answers: {},
  }));

  const ops = composeUpgrade(steps);
  assert.strictEqual(ops.length, 3);
});

test("compose preserves a pinned dependency version from the fragment", () => {
  const fileService: ModuleManifest = {
    id: "file-service",
    version: "0.1.0",
    requires: [],
    conflicts: [],
    prompts: [{ key: "internal_service_key", type: "secret", defaultValue: "x" }],
    mergeRecipes: ["merges/gradle.yml"],
    fileTemplates: [],
    upgrades: [],
    scaffold: false,
  };

  const ops = compose([{ manifest: fileService, answers: { internal_service_key: "x" } }]);
  const versions = ops
    .filter((op) => op.type === "org.openrewrite.gradle.AddDependency")
    .map((op) => op.version);
  assert.ok(versions.includes("8.6.0"));
});
