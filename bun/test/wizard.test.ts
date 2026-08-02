import { test } from "node:test";
import assert from "node:assert/strict";
import { askText, isInteractive } from "../src/wizard.ts";

// Clack owns raw stdin/keypress handling directly - there's no line-queue-style seam left to
// fake an interactive session through (unlike the old readline-based implementation this
// replaced). So test coverage here is limited to the paths that don't require driving Clack's
// real terminal I/O: the flag-supplied-value path (which never touches a prompt at all) and a
// basic isInteractive() sanity check. The interactive re-prompt-loop behavior that askText's
// `validate` callback enables is exercised manually - see the plan for this change.

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
