import { test } from "node:test";
import assert from "node:assert/strict";
import { ServiceRegistry } from "../src/registry/serviceRegistry.ts";
import { modulesFor } from "../src/registry/types.ts";

const registry = ServiceRegistry.loadBundled();

test("bundled catalog contains all shipped services", () => {
  const ids = registry.all().map((s) => s.id).sort();
  assert.deepStrictEqual(
    ids,
    ["file-service", "identity-service", "micronaut-skeleton", "notification-service", "user-service"].sort(),
  );
});

test("list-shape service falls back to default for every framework it supports", () => {
  const svc = registry.require("notification-service");
  assert.ok(modulesFor(svc, "spring-boot").length > 0);
  assert.ok(svc.framework.includes("spring-boot"));
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
