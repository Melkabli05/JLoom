import { test } from "node:test";
import assert from "node:assert/strict";
import { ServiceRegistry } from "../src/registry/serviceRegistry.ts";

const registry = ServiceRegistry.loadBundled();

test("bundled catalog contains all shipped services", () => {
  const ids = registry.all().map((s) => s.id).sort();
  assert.deepStrictEqual(ids, ["file-service", "identity-service", "notification-service", "user-service"].sort());
});

test("service modules resolve to a non-empty list", () => {
  const svc = registry.require("notification-service");
  assert.ok(svc.modules.length > 0);
  assert.ok(svc.modules.includes("base"));
});

test("unknown service throws with a helpful hint", () => {
  assert.throws(
    () => registry.require("does-not-exist"),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.ok(err.message.includes("does-not-exist"));
      assert.ok(err.message.includes("jloom list services"));
      return true;
    },
  );
});

test("find returns undefined for unknown id without throwing", () => {
  assert.strictEqual(registry.find("does-not-exist"), undefined);
});
