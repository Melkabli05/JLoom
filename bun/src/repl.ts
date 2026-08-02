import { CommanderError } from "commander";
import type { ReplIo } from "./lineSource.ts";
import { catalog } from "./catalog.ts";
import { buildProgram } from "./program.ts";
import { output } from "./wizard.ts";

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
  if (!/^\d+$/.test(token)) return undefined;
  const idx = Number.parseInt(token, 10);
  return idx >= 1 && idx <= MENU.length ? idx : undefined;
}

// Force eager catalog load at REPL startup so any load-time error surfaces immediately.
void catalog;

export async function runRepl(io: ReplIo): Promise<void> {
  io.rl.on("SIGINT", () => {
    io.rl.write("\n");
  });

  // One Commander program instance is reused across every REPL iteration.
  const program = buildProgram(io);
  const prompt = `${output.question("jloom")}> `;
  printMenu();

  while (true) {
    io.rl.setPrompt(prompt);
    io.rl.prompt();
    const line = await io.lines.next();
    if (line === null) break;
    const trimmed = line.trim();
    if (trimmed === "") continue;
    const lower = trimmed.toLowerCase();
    if (lower === "quit" || lower === "exit" || trimmed === "0") break;
    if (lower === "menu" || trimmed === "?") {
      printMenu();
      continue;
    }

    // Allow typing just a menu number (1..8). With `from: "user"` the args are as a user would
    // type them, so no program-name prefix is prepended.
    const tokens = trimmed.split(/\s+/);
    const choice = asMenuChoice(tokens[0]!);
    if (choice !== undefined) tokens[0] = MENU[choice - 1]!.command;

    try {
      await program.parseAsync(tokens, { from: "user" });
    } catch (err) {
      if (err instanceof CommanderError && (err.code === "commander.help" || err.code === "commander.version")) {
        continue;
      }
      console.error(output.err(err instanceof Error ? err.message : String(err)));
    }
  }
}
