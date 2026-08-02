import { Command, CommanderError } from "commander";
import * as output from "./output.ts";
import type { ReplIo } from "./lineSource.ts";
import type { JloomContext } from "./context.ts";
import { runAdd } from "./commands/add.ts";
import { runConfig } from "./commands/config.ts";
import { runInfo } from "./commands/info.ts";
import { runList } from "./commands/list.ts";
import { runNew } from "./commands/new.ts";
import { runStatus } from "./commands/status.ts";
import { runUpgrade } from "./commands/upgrade.ts";

/** Shared error formatting for both the one-shot CLI entry point and the REPL - mirroring
 * JloomCommandLine.create() on the Java side, which existed specifically so this logic lived
 * in exactly one place instead of being copy-pasted at every call site. */
export async function execute(program: Command, args: string[], from: "node" | "user"): Promise<number> {
  try {
    await program.parseAsync(args, { from });
    return 0;
  } catch (err) {
    if (err instanceof CommanderError) {
      if (err.code === "commander.helpDisplayed" || err.code === "commander.version") {
        return 0;
      }
      const message = err.message.replace(/^error:\s*/, "");
      console.error(output.error(message));
      console.error();
      console.error("Run 'jloom --help' for usage.");
      return typeof err.exitCode === "number" ? err.exitCode : 1;
    }
    const message = err instanceof Error ? err.message : String(err);
    console.error(output.error(message));
    return 1;
  }
}

function parseSet(value: string, previous: Record<string, string>): Record<string, string> {
  const result = { ...previous };
  for (const pair of value.split(",")) {
    const eq = pair.indexOf("=");
    if (eq > 0) {
      result[pair.slice(0, eq)] = pair.slice(eq + 1);
    }
  }
  return result;
}

/** Builds a fresh Commander program each call, rather than reusing one instance across
 * repeated REPL invocations - Commander commands hold parsed option values as instance
 * state, and rebuilding the (cheap) command tree per invocation avoids any risk of stale
 * flags bleeding between unrelated REPL commands. */
export function buildProgram(ctx: JloomContext, io: ReplIo): Command {
  const program = new Command();
  program
    .name("jloom")
    .description("Generate and evolve production-ready backends.")
    .version("jloom 0.2.0")
    .exitOverride()
    .configureOutput({ writeErr: (str) => process.stderr.write(str) });

  program
    .command("new")
    .description("Create a new project. Generates immediately; pass --dry-run to preview.")
    .exitOverride()
    .option("--name <name>", "Project name / target directory.")
    .option("--service <id>", "Service-type id, e.g. 'notification-service' (omit for a bare base project).")
    .option("--base-package <package>", "Base Java package.", "com.example.app")
    .option("--archetype <id>", "Apply an archetype's modules on top.")
    .option("--database <db>", "Database for a bare project: postgres | mysql | mariadb | h2 | none.")
    .option("--capabilities <list>", "Comma-separated capabilities, e.g. validation,security,caching.")
    .option("--cache-provider <provider>", "caffeine | redis. Only relevant if 'caching' is in --capabilities.")
    .option("--dry-run", "Preview without writing.", false)
    .option("-q, --quiet", "Suppress non-essential output.", false)
    .action(async (opts) => {
      await runNew(ctx, io, opts);
    });

  program
    .command("add")
    .description("Apply one or more modules to a project.")
    .exitOverride()
    .argument("[moduleIds...]", "Module ids to add.")
    .option("--project <dir>", "Target project directory.", ".")
    .option(
      "--set <entries>",
      "Override module prompts, e.g. --set postgres.db_name=demo,postgres.port=5433",
      parseSet,
      {},
    )
    .option("--dry-run", "Preview without writing.", false)
    .action(async (moduleIds: string[], opts) => {
      await runAdd(ctx, io, { project: opts.project, moduleIds, set: opts.set, dryRun: opts.dryRun });
    });

  program
    .command("list")
    .description("List available modules, services, or archetypes.")
    .exitOverride()
    .option("--what <what>", "What to list: modules|services|archetypes.", "modules")
    .action((opts) => {
      runList(ctx, opts.what);
    });

  program
    .command("info")
    .description("Show what a module changes before applying it.")
    .exitOverride()
    .option("--module <id>", "Module id, e.g. 'postgres'")
    .action(async (opts) => {
      await runInfo(ctx, io, opts.module);
    });

  program
    .command("status")
    .description("Show applied modules and whether newer versions exist.")
    .exitOverride()
    .option("--project <dir>", "Project directory.", ".")
    .action((opts) => {
      runStatus(ctx, opts.project);
    });

  program
    .command("upgrade")
    .description("Upgrade applied modules to the catalog's current versions.")
    .exitOverride()
    .option("--project <dir>", "Project directory.", ".")
    .option("--module <id>", "Upgrade only this module.")
    .option("--dry-run", "Preview without writing.", false)
    .action((opts) => {
      runUpgrade(ctx, opts.project, opts.module, opts.dryRun);
    });

  program
    .command("config")
    .description("Print the resolved jloom configuration.")
    .exitOverride()
    .action(() => {
      runConfig();
    });

  return program;
}
