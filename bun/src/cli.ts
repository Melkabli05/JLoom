#!/usr/bin/env bun
import { createInterface } from "node:readline/promises";
import { CommanderError } from "commander";
import { buildProgram } from "./program.ts";
import { createReplIo } from "./lineSource.ts";
import { runRepl } from "./repl.ts";
import { output } from "./wizard.ts";

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
        // showHelpAfterError() converts every parse error into a help-display, so Commander
        // throws 'commander.help' on both legitimate --help requests and on bad input. The
        // latter case is fine to swallow here - the help text was already printed, and exiting
        // with code 1 is acceptable. If you want to distinguish them, check if the user's
        // argv contained --help/--version explicitly.
        if (err instanceof CommanderError && (err.code === "commander.help" || err.code === "commander.version")) return;
        console.error(output.err(err instanceof Error ? err.message : String(err)));
        process.exitCode = 1;
      }
    }
  } finally {
    rl.close();
  }
}

await main();
