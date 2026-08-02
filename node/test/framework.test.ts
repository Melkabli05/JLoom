import { test } from "node:test";
import assert from "node:assert/strict";
import { byId, allIds } from "../src/framework.ts";
import { ModuleRegistry } from "../src/registry/moduleRegistry.ts";

test("spring-boot supports every declared capability", () => {
  const fw = byId("spring-boot");
  for (const capability of fw.supportedCapabilities) {
    assert.ok(fw.supports(capability), `spring-boot should support '${capability}'`);
  }
});

test("micronaut supports every declared capability except caching", () => {
  const fw = byId("micronaut");
  for (const capability of fw.supportedCapabilities) {
    assert.ok(fw.supports(capability), `micronaut should support '${capability}'`);
  }
  assert.strictEqual(fw.supports("caching"), false);
});

test("neither framework advertises capabilities for which no module exists", () => {
  const advertised = new Set([...byId("spring-boot").supportedCapabilities, ...byId("micronaut").supportedCapabilities]);

  for (const m of ModuleRegistry.loadBundled().all()) {
    if (m.provides !== undefined && m.provides.startsWith("capability:")) {
      const cap = m.provides.slice("capability:".length);
      assert.ok(advertised.has(cap), `capability '${cap}' is provided by ${m.id} but not advertised`);
    }
  }
});

test("byId rejects unknown framework with all known ids in the message", () => {
  assert.throws(
    () => byId("quarkus"),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.ok(err.message.includes("spring-boot"));
      assert.ok(err.message.includes("micronaut"));
      return true;
    },
  );
});

test("allIds returns every framework in declaration order", () => {
  assert.deepStrictEqual(allIds(), ["spring-boot", "micronaut"]);
});
