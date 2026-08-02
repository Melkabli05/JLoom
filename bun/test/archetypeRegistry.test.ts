import { test } from "node:test";
import assert from "node:assert/strict";
import { ArchetypeRegistry } from "../src/registry/archetypeRegistry.ts";

const registry = ArchetypeRegistry.loadBundled();

test("bundled catalog contains all shipped archetypes", () => {
  const ids = registry.all().map((a) => a.id).sort();
  assert.deepStrictEqual(
    ids,
    ["identity-with-user", "notification-stack", "postgres-flyway-service", "postgres-service"].sort(),
  );
});

test("archetype modules and answers parse as expected", () => {
  const archetype = registry.find("identity-with-user");
  assert.ok(archetype !== undefined);
  assert.ok(archetype.modules.includes("identity-service"));
  assert.ok(archetype.modules.includes("user-service"));
  assert.strictEqual(archetype.answers["postgres.db_name"], "app");
});

test("find returns undefined for unknown id without throwing", () => {
  assert.strictEqual(registry.find("does-not-exist"), undefined);
});
