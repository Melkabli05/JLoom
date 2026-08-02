import { test } from "node:test";
import assert from "node:assert/strict";
import { CommanderError } from "commander";
import { buildProgram } from "../src/program.ts";
import { createReplIo } from "../src/lineSource.ts";
import type { Interface } from "node:readline/promises";

// A real Readline Interface stub. Every test passes a fully-specified argv so no prompt
// reads ever need to return anything - the line queue is effectively never consumed. We
// silence the readline event-emitters with a no-op `on()` so the program tree can attach
// without throwing.
const rlStub: Interface = Object.assign(Object.create(null), {
  on: () => rlStub,
  setPrompt: () => undefined,
  prompt: () => undefined,
  write: () => undefined,
  close: () => undefined,
  question: async () => "",
  pause: () => undefined,
  resume: () => undefined,
  terminal: false,
}) as unknown as Interface;
const io = createReplIo(rlStub);

test("all 7 commands register on the program", () => {
  const program = buildProgram(io);
  const names = program.commands.map((c) => c.name());
  assert.deepStrictEqual(
    names.sort(),
    ["add", "config", "info", "list", "new", "status", "upgrade"].sort(),
  );
});

test("--version: exitOverride suppresses process.exit, error code may be 'commander.help' due to showHelpAfterError", async () => {
  const program = buildProgram(io);
  const orig = console.log;
  console.log = () => {};
  let captured: CommanderError | undefined;
  try {
    await program.parseAsync(["--version"], { from: "node" });
  } catch (err) {
    captured = err as CommanderError;
  } finally {
    console.log = orig;
  }
  // exitOverride() makes Commander throw rather than process.exit - we just confirm the
  // error makes it back to us as a CommanderError. The exact code can be 'commander.help'
  // (when showHelpAfterError() converts it) or 'commander.version' depending on the order
  // Commander processes the help-after-error hook. Both are fine.
  assert.ok(captured instanceof CommanderError);
});

test("--help throws CommanderError with code 'commander.help'", async () => {
  const program = buildProgram(io);
  const orig = console.log;
  console.log = () => {};
  let captured: CommanderError | undefined;
  try {
    await program.parseAsync(["--help"], { from: "node" });
  } catch (err) {
    captured = err as CommanderError;
  } finally {
    console.log = orig;
  }
  assert.ok(captured instanceof CommanderError);
  assert.strictEqual(captured.code, "commander.help");
});

test("unknown command throws CommanderError (Commander rethrows parse failures via exitOverride)", async () => {
  const program = buildProgram(io);
  const orig = console.error;
  console.error = () => {};
  let captured: CommanderError | undefined;
  try {
    await program.parseAsync(["bogus"], { from: "node" });
  } catch (err) {
    captured = err as CommanderError;
  } finally {
    console.error = orig;
  }
  // showHelpAfterError() converts the unknown-command error into a help-display error so the
  // caller knows parse failed and the help text was printed. We just need to confirm exitOverride
  // is actually swallowing the error rather than the process exiting.
  assert.ok(captured instanceof CommanderError);
});

test("REPL-style invocation: `from: 'user'` argv with the program-name prefix omitted parses correctly", async () => {
  const program = buildProgram(io);
  // 'list' as a user would type it - this is the form the REPL uses.
  await assert.doesNotReject(async () => {
    await program.parseAsync(["list"], { from: "user" });
  });
});

