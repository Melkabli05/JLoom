import * as output from "./output.ts";
import { buildProgram, execute } from "./program.ts";
import type { ReplIo } from "./lineSource.ts";
import type { JloomContext } from "./context.ts";

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

export async function runRepl(ctx: JloomContext, io: ReplIo): Promise<void> {
  // Ctrl-C cancels the current line without exiting the REPL (matches the Java version's
  // UserInterruptException -> continue handling). Without a listener, readline would just
  // pause the input stream instead of cleanly returning to the next prompt.
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

    const tokens = tokenize(trimmed);
    if (tokens[0] === "help") {
      // Route directly to outputHelp() (documented as non-exiting) rather than through full
      // argument parsing, since Commander's built-in `help` subcommand is designed to end the
      // program rather than return control to a REPL loop.
      buildProgram(ctx, io).outputHelp();
      continue;
    }

    await execute(buildProgram(ctx, io), tokens, "user");
  }
}
