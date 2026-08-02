import * as output from "./output.ts";
import type { ReplIo } from "./lineSource.ts";
import { catalog } from "./catalog.ts";
import { runAdd } from "./commands/add.ts";
import { runConfig } from "./commands/config.ts";
import { runInfo } from "./commands/info.ts";
import { runList } from "./commands/list.ts";
import { runNew } from "./commands/new.ts";
import { runStatus } from "./commands/status.ts";
import { runUpgrade } from "./commands/upgrade.ts";

interface MenuEntry {
  command: string;
  description: string;
}

const MENU: MenuEntry[] = [
  { command: "new", description: "Create a new project" },
  { command: "add", description: "Add a module to an existing project" },
  { command: "list", description: "List available modules, services, or archetypes" },
  { command: "info", description: "Show what a module does before applying it" },
  { command: "status", description: "Show applied modules and any newer versions" },
  { command: "upgrade", description: "Pull newer versions of one or more modules" },
  { command: "config", description: "Print the resolved jloom configuration" },
  { command: "help", description: "Show full usage" },
];

export const REPL_COMMAND_NAMES = [...MENU.map((m) => m.command), "interactive"];

export function replCompleter(line: string): [string[], string] {
  const hits = REPL_COMMAND_NAMES.filter((c) => c.startsWith(line));
  return [hits.length > 0 ? hits : REPL_COMMAND_NAMES, line];
}

function printMenu(): void {
  console.log(output.question("What would you like to do?"));
  MENU.forEach((entry, i) => {
    console.log(`  ${output.accent(`${i + 1})`)} ${entry.description}`);
  });
  console.log(`  ${output.accent("0)")} Quit`);
  console.log();
  console.log(output.hint("Pick a number, or type a full command (tab-completes; 'menu' shows this again)."));
  console.log();
}

function asMenuChoice(token: string): number | undefined {
  if (!/^\d+$/.test(token)) {
    return undefined;
  }
  const idx = Number.parseInt(token, 10);
  return idx >= 1 && idx <= MENU.length ? idx : undefined;
}

function tokenize(trimmed: string): string[] {
  const tokens = trimmed.split(/\s+/);
  if (tokens[0] === "jloom") {
    return tokens.slice(1);
  }
  const choice = asMenuChoice(tokens[0]!);
  if (choice !== undefined) {
    tokens[0] = MENU[choice - 1]!.command;
  }
  return tokens;
}

function parseFlags(tokens: string[]): Record<string, string> {
  const result: Record<string, string> = {};
  for (let i = 0; i < tokens.length - 1; i++) {
    if (tokens[i]!.startsWith("--")) {
      const key = tokens[i]!.slice(2);
      const next = tokens[i + 1];
      if (next !== undefined && !next.startsWith("--")) {
        result[key] = next;
      }
    }
  }
  return result;
}

async function dispatch(io: ReplIo, tokens: string[]): Promise<void> {
  const cmd = tokens[0];
  const allArgs = tokens.slice(1);
  const flags = parseFlags([cmd ?? "", ...allArgs]);
  const nonFlagArgs = allArgs.filter((t) => !t.startsWith("--"));

  if (cmd === undefined || cmd === "help") {
    console.log(jloomUsage());
    return;
  }

  switch (cmd) {
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
        moduleIds: nonFlagArgs,
        set: {},
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
      throw new Error(`unknown command: ${cmd ?? "(none)"}`);
  }
}

// Reference catalog so any load-time error surfaces during REPL startup, not the first command.
void catalog;

function jloomUsage(): string {
  return `jloom — generate and evolve production-ready backends.

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
}

export async function runRepl(io: ReplIo): Promise<void> {
  io.rl.on("SIGINT", () => {
    io.rl.write("\n");
  });

  const prompt = `${output.question("jloom")}> `;
  printMenu();

  while (true) {
    io.rl.setPrompt(prompt);
    io.rl.prompt();
    const line = await io.lines.next();
    if (line === null) {
      break;
    }
    const trimmed = line.trim();
    if (trimmed === "") {
      continue;
    }
    const lower = trimmed.toLowerCase();
    if (lower === "quit" || lower === "exit" || trimmed === "0") {
      break;
    }
    if (lower === "menu" || trimmed === "?") {
      printMenu();
      continue;
    }

    await dispatch(io, tokenize(trimmed));
  }
}
