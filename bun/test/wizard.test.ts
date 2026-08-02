import { test } from "node:test";
import assert from "node:assert/strict";
import { askConfirm, askText } from "../src/wizard.ts";
import type { ReplIo } from "../src/lineSource.ts";

// isInteractive() reads process.stdin.isTTY / process.stdout.isTTY directly, so tests that
// need to exercise the "interactive" branch temporarily stub those properties and restore them
// afterward - there's no other seam to inject this without changing production code.
function withTTY<T>(value: boolean, fn: () => T): T {
  const stdinDesc = Object.getOwnPropertyDescriptor(process.stdin, "isTTY");
  const stdoutDesc = Object.getOwnPropertyDescriptor(process.stdout, "isTTY");
  Object.defineProperty(process.stdin, "isTTY", { value, configurable: true });
  Object.defineProperty(process.stdout, "isTTY", { value, configurable: true });
  try {
    return fn();
  } finally {
    if (stdinDesc) Object.defineProperty(process.stdin, "isTTY", stdinDesc);
    if (stdoutDesc) Object.defineProperty(process.stdout, "isTTY", stdoutDesc);
  }
}

// A minimal ReplIo whose line source replays a fixed queue rather than reading from a real
// readline Interface - lets us script an interactive session (multiple answers in sequence)
// deterministically, without a real terminal.
function fakeIoWithLines(lines: string[]): ReplIo {
  const queue = [...lines];
  return {
    rl: { setPrompt: () => undefined, prompt: () => undefined } as unknown as ReplIo["rl"],
    lines: { next: async () => (queue.length > 0 ? queue.shift()! : null) },
  };
}

test("askConfirm returns the default immediately when non-interactive, without consuming any input", async () => {
  const io = fakeIoWithLines(["this should never be read"]);
  const result = await withTTY(false, () => askConfirm(io, "Proceed?", true));
  assert.strictEqual(result, true);
});

test("askConfirm (interactive): blank input returns the default", async () => {
  const io = fakeIoWithLines([""]);
  const result = await withTTY(true, () => askConfirm(io, "Proceed?", true));
  assert.strictEqual(result, true);
});

test("askConfirm (interactive): 'n' returns false even when the default is true", async () => {
  const io = fakeIoWithLines(["n"]);
  const result = await withTTY(true, () => askConfirm(io, "Proceed?", true));
  assert.strictEqual(result, false);
});

test("askText: a valid flag-supplied value passes validate() and returns unchanged", async () => {
  const io = fakeIoWithLines([]);
  const result = await askText(io, "com.acme.demo", "base-package", "Base package", "com.example.app", (v) =>
    /^[a-z]+(\.[a-z]+)*$/.test(v) ? undefined : "invalid",
  );
  assert.strictEqual(result, "com.acme.demo");
});

test("askText: an invalid flag-supplied value throws immediately, mentioning the promptLabel", async () => {
  const io = fakeIoWithLines([]);
  await assert.rejects(
    () =>
      askText(io, "123bad", "base-package", "Base package", "com.example.app", (v) =>
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

test("askText (interactive): an invalid answer re-prompts instead of aborting, then accepts the next valid one", async () => {
  const validate = (v: string): string | undefined => (/^[a-z]+(\.[a-z]+)*$/.test(v) ? undefined : "must be lowercase dotted segments");
  const io = fakeIoWithLines(["123bad", "com.acme.demo"]);
  const result = await withTTY(true, () => askText(io, undefined, "base-package", "Base package", "com.example.app", validate));
  assert.strictEqual(result, "com.acme.demo");
});

test("askText (interactive): blank input falls back to the default value", async () => {
  const io = fakeIoWithLines([""]);
  const result = await withTTY(true, () => askText(io, undefined, "name", "Project name", "my-app"));
  assert.strictEqual(result, "my-app");
});
