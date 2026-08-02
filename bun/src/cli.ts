#!/usr/bin/env bun
import { createInterface } from "node:readline/promises";
import { CommanderError } from "commander";
import { buildProgram } from "./program.ts";
import { createReplIo } from "./lineSource.ts";
import { runRepl } from "./repl.ts";
import { output } from "./wizard.ts";

// Commander exit codes that mean "the requested info was already printed, exit cleanly" -
// --help/-h -> commander.helpDisplayed, the `help` subcommand -> commander.help,
// --version/-V -> commander.version. Anything else (commander.unknownCommand,
// commander.invalidArgument, ...) is a real error with a useful message and should exit 1.
const CLEAN_EXIT_CODES = new Set(["commander.helpDisplayed", "commander.help", "commander.version"]);

const rl = createInterface({ input: process.stdin, output: process.stdout });
const io = createReplIo(rl);

async function main(): Promise<void> {
  try {
    if (process.argv.length <= 2) {
      await runRepl(io);
    } else {
      try {
        await buildProgram(io).parseAsync(process.argv);
      } catch (err) {
        if (err instanceof CommanderError && CLEAN_EXIT_CODES.has(err.code)) return;
        console.error(output.err(err instanceof Error ? err.message : String(err)));
        process.exitCode = 1;
      }
    }
  } finally {
    rl.close();
  }
}

await main();
