import { test } from "node:test";
import assert from "node:assert/strict";
import { CommanderError } from "commander";
import { buildProgram } from "../src/program.ts";

async function captureError(argv: string[]): Promise<CommanderError> {
  const program = buildProgram();
  const origLog = console.log;
  const origErr = console.error;
  console.log = () => undefined;
  console.error = () => undefined;
  try {
    await program.parseAsync(argv, { from: "user" });
    throw new Error(`expected ${JSON.stringify(argv)} to throw a CommanderError`);
  } catch (err) {
    if (!(err instanceof CommanderError)) throw err;
    return err;
  } finally {
    console.log = origLog;
    console.error = origErr;
  }
}

test("all 7 commands register on the program", () => {
  const program = buildProgram();
  const names = program.commands.map((c) => c.name());
  assert.deepStrictEqual(
    names.sort(),
    ["add", "config", "info", "list", "new", "status", "upgrade"].sort(),
  );
});

test("--help exits cleanly with code 'commander.helpDisplayed' and exitCode 0", async () => {
  const err = await captureError(["--help"]);
  assert.strictEqual(err.code, "commander.helpDisplayed");
  assert.strictEqual(err.exitCode, 0);
});

test("bare 'help' subcommand exits cleanly with code 'commander.help' and exitCode 0", async () => {
  const err = await captureError(["help"]);
  assert.strictEqual(err.code, "commander.help");
  assert.strictEqual(err.exitCode, 0);
});

test("--version exits cleanly with code 'commander.version' and exitCode 0", async () => {
  const err = await captureError(["--version"]);
  assert.strictEqual(err.code, "commander.version");
  assert.strictEqual(err.exitCode, 0);
});

test("unknown command exits with code 'commander.unknownCommand' and exitCode 1", async () => {
  const err = await captureError(["bogus"]);
  assert.strictEqual(err.code, "commander.unknownCommand");
  assert.strictEqual(err.exitCode, 1);
  assert.ok(err.message.includes("unknown command"));
});

test("invalid --database choice exits with code 'commander.invalidArgument' and a message naming the allowed choices", async () => {
  const err = await captureError(["new", "--database", "bogus"]);
  assert.strictEqual(err.code, "commander.invalidArgument");
  assert.strictEqual(err.exitCode, 1);
  assert.ok(err.message.includes("Allowed choices are"));
});

test("`from: 'user'` argv with the program-name prefix omitted parses correctly", async () => {
  const program = buildProgram();
  await assert.doesNotReject(async () => {
    await program.parseAsync(["list"], { from: "user" });
  });
});
