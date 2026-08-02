#!/usr/bin/env node
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { createInterface } from "node:readline/promises";
import { createContext } from "./context.ts";
import { createReplIo } from "./lineSource.ts";
import { buildProgram, execute } from "./program.ts";
import { replCompleter, runRepl } from "./repl.ts";

const historyFile = path.join(os.homedir(), ".jloom", "history");

function loadHistory(): string[] {
  if (!existsSync(historyFile)) {
    return [];
  }
  return readFileSync(historyFile, "utf8").split("\n").filter((line) => line !== "");
}

function persistHistory(history: string[]): void {
  mkdirSync(path.dirname(historyFile), { recursive: true });
  writeFileSync(historyFile, `${history.join("\n")}\n`, "utf8");
}

const ctx = createContext();
const rl = createInterface({
  input: process.stdin,
  output: process.stdout,
  completer: replCompleter,
  history: loadHistory(),
  historySize: 1000,
});
rl.on("history", persistHistory);
const io = createReplIo(rl);

if (process.argv.length <= 2) {
  await runRepl(ctx, io);
  rl.close();
  process.exit(0);
} else {
  const exitCode = await execute(buildProgram(ctx, io), process.argv, "node");
  rl.close();
  process.exit(exitCode);
}
