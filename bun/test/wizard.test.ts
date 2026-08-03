import { test } from "node:test";
import assert from "node:assert/strict";
import { askText, isInteractive } from "../src/wizard.ts";
test("isInteractive() reflects the current (non-TTY, test runner) environment", () => {
  assert.strictEqual(isInteractive(), false);
});
test("askText: a valid flag-supplied value passes validate() and returns unchanged", async () => {
  const result = await askText("com.acme.demo", "base-package", "Base package", "com.example.app", (v) =>
    /^[a-z]+(\.[a-z]+)*$/.test(v) ? undefined : "invalid",
  );
  assert.strictEqual(result, "com.acme.demo");
});
test("askText: an invalid flag-supplied value throws immediately, mentioning the promptLabel", async () => {
  await assert.rejects(
    () =>
      askText("123bad", "base-package", "Base package", "com.example.app", (v) =>
        /^[a-z]+(\.[a-z]+)*$/.test(v) ? undefined : "must be lowercase dotted segments",
      ),
    (err: unknown) => {
      assert.ok(err instanceof Error);
      assert.ok(err.message.includes("base-package"));
      assert.ok(err.message.includes("must be lowercase dotted segments"));
      return true;
    },
  );
});
test("askText: a flag-supplied value with no validate callback returns unchanged", async () => {
  const result = await askText("my-app", "name", "Project name", "default-name");
  assert.strictEqual(result, "my-app");
});
