#!/usr/bin/env bun
import { CommanderError } from "commander";
import { buildProgram } from "./program.ts";
import { output } from "./wizard.ts";
const CLEAN_EXIT_CODES = new Set(["commander.helpDisplayed", "commander.help", "commander.version"]);
async function main(): Promise<void> {



  const argv = process.argv.length <= 2 ? [...process.argv, "new"] : process.argv;
  try {
    await buildProgram().parseAsync(argv);
  } catch (err) {
    if (err instanceof CommanderError && CLEAN_EXIT_CODES.has(err.code)) return;
    console.error(output.err(err instanceof Error ? err.message : String(err)));
    process.exitCode = 1;
  }
}
await main();
