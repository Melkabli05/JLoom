import { test } from "node:test";
import assert from "node:assert/strict";
import { catalog, validate, findUpgradePath } from "../src/catalog.ts";

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
