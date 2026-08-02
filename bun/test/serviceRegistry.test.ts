import { test } from "node:test";
import assert from "node:assert/strict";
import { catalog } from "../src/catalog.ts";

test("bundled catalog contains all shipped services", () => {
  const ids = [...catalog.services.values()].map((s) => s.id).sort();
  assert.deepStrictEqual(ids, ["file-service", "identity-service", "notification-service", "user-service"].sort());
});

test("service modules resolve to a non-empty list", () => {
  const svc = catalog.services.get("notification-service")!;
  assert.ok(svc.modules.length > 0);
  assert.ok(svc.modules.includes("base"));
});

test("find returns undefined for unknown id without throwing", () => {
  assert.strictEqual(catalog.services.get("does-not-exist"), undefined);
});
