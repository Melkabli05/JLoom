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
