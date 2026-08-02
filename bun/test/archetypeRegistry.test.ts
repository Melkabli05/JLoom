import { test } from "node:test";
import assert from "node:assert/strict";
import { catalog } from "../src/catalog.ts";

test("bundled catalog contains all shipped archetypes", () => {
  const ids = [...catalog.archetypes.values()].map((a) => a.id).sort();
  assert.deepStrictEqual(
    ids,
    ["identity-with-user", "notification-stack", "postgres-flyway-service", "postgres-service"].sort(),
  );
});

test("archetype modules and answers parse as expected", () => {
  const archetype = catalog.archetypes.get("identity-with-user");
  assert.ok(archetype !== undefined);
  assert.ok(archetype.modules.includes("identity-service"));
  assert.ok(archetype.modules.includes("user-service"));
  assert.strictEqual(archetype.answers["postgres.db_name"], "app");
});

test("find returns undefined for unknown id without throwing", () => {
  assert.strictEqual(catalog.archetypes.get("does-not-exist"), undefined);
});
