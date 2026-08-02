import { test } from "node:test";
import assert from "node:assert/strict";
import { catalog, validate, findUpgradePath } from "../src/catalog.ts";

// ===== Module catalog: load, validate, findUpgradePath =====

test("catalog loads all 20 module manifests from the bundled index", () => {
  const ids = [...catalog.modules.keys()].sort();
  assert.ok(ids.includes("base"));
  assert.ok(ids.includes("postgres"));
  assert.ok(ids.includes("jwt-auth"));
  assert.ok(catalog.modules.size >= 20);
});

test("catalog loads all 4 services from services.yml", () => {
  const ids = [...catalog.services.keys()].sort();
  assert.deepStrictEqual(ids, ["file-service", "identity-service", "notification-service", "user-service"].sort());
});

test("catalog loads all 4 archetypes from archetypes/archetypes.yml", () => {
  const ids = [...catalog.archetypes.keys()].sort();
  assert.deepStrictEqual(
    ids,
    ["identity-with-user", "notification-stack", "postgres-flyway-service", "postgres-service"].sort(),
  );
});

test("service modules list is non-empty and includes the base module", () => {
  const svc = catalog.services.get("notification-service")!;
  assert.ok(svc.modules.length > 0);
  assert.ok(svc.modules.includes("base"));
});

test("archetype modules and answers parse as expected", () => {
  const archetype = catalog.archetypes.get("identity-with-user")!;
  assert.ok(archetype.modules.includes("identity-service"));
  assert.ok(archetype.modules.includes("user-service"));
  assert.strictEqual(archetype.answers["postgres.db_name"], "app");
});

test("find returns undefined for unknown ids without throwing", () => {
  assert.strictEqual(catalog.modules.get("does-not-exist"), undefined);
  assert.strictEqual(catalog.services.get("does-not-exist"), undefined);
  assert.strictEqual(catalog.archetypes.get("does-not-exist"), undefined);
});

// ===== upgrade path walking =====

test("upgrade path chains multiple steps from old version to current", () => {
  const postgres = catalog.modules.get("postgres")!;
  assert.ok(postgres.upgrades.length > 0);
  const from = postgres.upgrades[0]!.from;
  const expectedFinal = postgres.upgrades[postgres.upgrades.length - 1]!.to;

  const path = findUpgradePath(catalog, "postgres", from);

  assert.strictEqual(path.length, postgres.upgrades.length);
  assert.strictEqual(path[path.length - 1]!.to, expectedFinal);
  for (let i = 1; i < path.length; i++) {
    assert.strictEqual(path[i]!.from, path[i - 1]!.to);
  }
});

test("upgrade path is empty when already at current version", () => {
  const postgres = catalog.modules.get("postgres")!;
  assert.deepStrictEqual(findUpgradePath(catalog, "postgres", postgres.version), []);
});

test("upgrade path is empty when no chain reaches current version", () => {
  assert.deepStrictEqual(findUpgradePath(catalog, "postgres", "0.0.0-not-a-version"), []);
});

// ===== validation (requires/conflicts) =====

test("validate returns empty for a self-consistent batch", () => {
  assert.deepStrictEqual(validate(catalog, [], ["base"]), []);
});

test("validate reports missing requires", () => {
  const problems = validate(catalog, [], ["notification-service"]);
  assert.ok(problems.length > 0);
  assert.ok(problems.join(" ").includes("requires"));
});

test("validate enforces order within a batch", () => {
  const problems = validate(catalog, [], ["notification-service", "base"]);
  assert.ok(problems.join(" ").includes("AFTER"));
});

test("validate rejects conflicting modules", () => {
  const byId = new Map([...catalog.modules.values()].map((m) => [m.id, m]));
  for (const m of byId.values()) {
    for (const conflict of m.conflicts) {
      if (byId.has(conflict)) {
        const problems = validate(catalog, [], [m.id, conflict]);
        assert.ok(problems.join(" ").includes("conflicts"));
        return;
      }
    }
  }
});

test("validate flags unknown module ids", () => {
  const problems = validate(catalog, [], ["not-a-real-module"]);
  assert.ok(problems.includes("Unknown module: not-a-real-module"));
});
