import { test } from "node:test";
import assert from "node:assert/strict";
import { catalog, validate, findUpgradePath, resolveModules, type Catalog, type ModuleManifest, type ProviderPicker } from "../src/catalog.ts";

function makeModule(overrides: Partial<ModuleManifest> & { id: string }): ModuleManifest {
  return {
    version: "1.0.0",
    requires: [],
    conflicts: [],
    prompts: [],
    mergeRecipes: [],
    fileTemplates: [],
    upgrades: [],
    scaffold: false,
    ...overrides,
  };
}
function fakeCatalog(modules: ModuleManifest[]): Catalog {
  return { modules: new Map(modules.map((m) => [m.id, m])), services: new Map(), archetypes: new Map() };
}
function neverPick(): ProviderPicker {
  return async () => undefined;
}

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

// ===== resolveModules: catalog-driven dependency resolution =====

test("resolveModules auto-adds a single-provider capability requirement, ordered before its dependent", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "a", provides: "capability:x" }),
    makeModule({ id: "b", requires: ["capability:x"] }),
  ]);
  const result = await resolveModules(cat, [], ["b"], neverPick());
  assert.deepStrictEqual(result.problems, []);
  assert.deepStrictEqual(result.moduleIds, ["a", "b"]);
  assert.deepStrictEqual(result.added, ["a"]);
});

test("resolveModules delegates an ambiguous multi-provider capability to pickProvider", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "db1", provides: "capability:db", conflicts: ["db2"] }),
    makeModule({ id: "db2", provides: "capability:db", conflicts: ["db1"] }),
    makeModule({ id: "app", requires: ["capability:db"] }),
  ]);
  let calls = 0;
  const pick: ProviderPicker = async (capability, candidates) => {
    calls++;
    assert.strictEqual(capability, "capability:db");
    assert.deepStrictEqual(candidates.map((m) => m.id).sort(), ["db1", "db2"]);
    return "db1";
  };
  const result = await resolveModules(cat, [], ["app"], pick);
  assert.deepStrictEqual(result.problems, []);
  assert.deepStrictEqual(result.moduleIds, ["db1", "app"]);
  assert.strictEqual(calls, 1);
});

test("resolveModules asks pickProvider at most once per capability, even across multiple dependents", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "db1", provides: "capability:db", conflicts: ["db2"] }),
    makeModule({ id: "db2", provides: "capability:db", conflicts: ["db1"] }),
    makeModule({ id: "app1", requires: ["capability:db"] }),
    makeModule({ id: "app2", requires: ["capability:db"] }),
  ]);
  let calls = 0;
  const pick: ProviderPicker = async () => {
    calls++;
    return "db1";
  };
  const result = await resolveModules(cat, [], ["app1", "app2"], pick);
  assert.deepStrictEqual(result.problems, []);
  assert.strictEqual(calls, 1);
  assert.strictEqual(result.moduleIds.filter((id) => id === "db1").length, 1);
});

test("resolveModules reports a clear problem when pickProvider can't resolve (non-interactive)", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "db1", provides: "capability:db", conflicts: ["db2"] }),
    makeModule({ id: "db2", provides: "capability:db", conflicts: ["db1"] }),
    makeModule({ id: "app", requires: ["capability:db"] }),
  ]);
  const result = await resolveModules(cat, [], ["app"], neverPick());
  assert.ok(result.problems.length > 0);
  assert.ok(result.problems.join(" ").includes("db1"));
  assert.ok(result.problems.join(" ").includes("db2"));
});

test("resolveModules reports a clear problem when no module provides a required capability", () =>
  resolveModules(fakeCatalog([makeModule({ id: "app", requires: ["capability:missing"] })]), [], ["app"], neverPick()).then(
    (result) => {
      assert.ok(result.problems.join(" ").includes("No module in the catalog provides 'capability:missing'"));
    },
  ));

test("resolveModules reorders a batch even when the dependent is listed before its dependency", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "a", provides: "capability:x" }),
    makeModule({ id: "b", requires: ["capability:x"] }),
  ]);
  const result = await resolveModules(cat, [], ["b", "a"], neverPick());
  assert.deepStrictEqual(result.problems, []);
  assert.deepStrictEqual(result.moduleIds, ["a", "b"]);
});

test("resolveModules prefers a provider explicitly listed later in the same batch over asking pickProvider", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "db1", provides: "capability:db", conflicts: ["db2"] }),
    makeModule({ id: "db2", provides: "capability:db", conflicts: ["db1"] }),
    makeModule({ id: "app", requires: ["capability:db"] }),
  ]);
  const result = await resolveModules(cat, [], ["app", "db2"], neverPick());
  assert.deepStrictEqual(result.problems, []);
  assert.deepStrictEqual(result.moduleIds, ["db2", "app"]);
});

test("resolveModules still catches conflicts introduced by the requested batch", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "x", provides: "capability:c", conflicts: ["y"] }),
    makeModule({ id: "y", provides: "capability:c" }),
  ]);
  const result = await resolveModules(cat, [], ["x", "y"], neverPick());
  assert.ok(result.problems.join(" ").includes("conflicts"));
});

test("resolveModules flags unknown module ids the same way validate does", async () => {
  const result = await resolveModules(fakeCatalog([]), [], ["not-a-real-module"], neverPick());
  assert.ok(result.problems.includes("Unknown module: not-a-real-module"));
});

test("resolveModules leaves an already-satisfied batch untouched (no auto-adds)", async () => {
  const cat = fakeCatalog([
    makeModule({ id: "a", provides: "capability:x" }),
    makeModule({ id: "b", requires: ["capability:x"] }),
  ]);
  const result = await resolveModules(cat, ["a"], ["b"], neverPick());
  assert.deepStrictEqual(result.problems, []);
  assert.deepStrictEqual(result.moduleIds, ["b"]);
  assert.deepStrictEqual(result.added, []);
});
