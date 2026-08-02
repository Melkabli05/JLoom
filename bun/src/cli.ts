#!/usr/bin/env bun
import { createInterface } from "node:readline/promises";
import { runAdd } from "./commands/add.ts";
import { runConfig } from "./commands/config.ts";
import { runInfo } from "./commands/info.ts";
import { runList } from "./commands/list.ts";
import { runNew } from "./commands/new.ts";
import { runStatus } from "./commands/status.ts";
import { runUpgrade } from "./commands/upgrade.ts";
import { createReplIo } from "./lineSource.ts";
import { runRepl } from "./repl.ts";

// ===== 3-line inline color helper (replaces output.ts) =====
const color = process.stdout.isTTY === true && process.env.NO_COLOR === undefined;
const c = (code: string, s: string): string => (color ? `\x1b[${code}m${s}\x1b[0m` : s);

// ===== argv parser (replaces Commander + program.ts) =====
function parseArgs(argv: string[]): { cmd: string | undefined; flags: Record<string, string>; positional: string[] } {
  const [, , ...rest] = argv;
  const flags: Record<string, string> = {};
  const positional: string[] = [];
  let cmd: string | undefined;
  let i = 0;
  if (rest.length > 0 && !rest[0]!.startsWith("-")) {
    cmd = rest[0];
    i = 1;
  }
  for (; i < rest.length; i++) {
    const tok = rest[i]!;
    if (tok.startsWith("--")) {
      const key = tok.slice(2);
      const next = rest[i + 1];
      if (next !== undefined && !next.startsWith("-")) {
        flags[key] = next;
        i++;
      } else {
        flags[key] = "true";
      }
    } else {
      positional.push(tok);
    }
  }
  return { cmd, flags, positional };
}

const USAGE = `jloom — generate and evolve production-ready backends.

Usage:
  jloom [command] [options]

Commands:
  new        Create a new project (interactive or via flags)
  add        Add a module to an existing project
  list       List available modules, services, or archetypes
  info       Show what a module does before applying it
  status     Show applied modules and any newer versions
  upgrade    Pull newer versions of one or more modules
  config     Print the resolved jloom configuration
  help       Show this message

Examples:
  jloom new --name my-app --service user-service --base-package com.acme.demo
  jloom add postgres flyway --project ./my-app
  jloom list
  jloom info --module postgres
  jloom upgrade --dry-run`;

function parseSetFlag(value: string, previous: Record<string, string>): Record<string, string> {
  const result = { ...previous };
  for (const pair of value.split(",")) {
    const eq = pair.indexOf("=");
    if (eq > 0) {
      result[pair.slice(0, eq)] = pair.slice(eq + 1);
    }
  }
  return result;
}

// ===== dispatch (replaces program.ts) =====
async function dispatch(cmd: string | undefined, flags: Record<string, string>, positional: string[]): Promise<void> {
  switch (cmd) {
    case undefined:
    case "help":
      console.log(USAGE);
      return;
    case "new":
      await runNew(io, {
        name: flags.name,
        service: flags.service,
        basePackage: flags["base-package"] ?? "com.example.app",
        archetype: flags.archetype,
        database: flags.database,
        capabilities: flags.capabilities,
        cacheProvider: flags["cache-provider"],
        dryRun: flags["dry-run"] === "true",
        quiet: flags.quiet === "true",
      });
      return;
    case "add":
      await runAdd(io, {
        project: flags.project ?? ".",
        moduleIds: positional,
        set: flags.set ? parseSetFlag(flags.set, {}) : {},
        dryRun: flags["dry-run"] === "true",
      });
      return;
    case "list":
      runList(flags.what ?? "modules");
      return;
    case "info":
      await runInfo(io, flags.module);
      return;
    case "status":
      runStatus(flags.project ?? ".");
      return;
    case "upgrade":
      runUpgrade(flags.project ?? ".", flags.module, flags["dry-run"] === "true");
      return;
    case "config":
      runConfig();
      return;
    default:
      console.error(c("31", `✗ unknown command: ${cmd}`));
      console.error(c("2", "Run 'jloom help' for usage."));
      throw new Error(`unknown command: ${cmd}`);
  }
}

const rl = createInterface({ input: process.stdin, output: process.stdout });
const io = createReplIo(rl);

try {
  if (process.argv.length <= 2) {
    await runRepl(io);
  } else {
    const { cmd, flags, positional } = parseArgs(process.argv);
    await dispatch(cmd, flags, positional);
  }
} catch (err) {
  console.error(c("31", `✗ ${err instanceof Error ? err.message : String(err)}`));
  process.exit(1);
}

rl.close();
