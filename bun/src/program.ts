import { Command, InvalidArgumentError, Option } from "commander";
import type { ReplIo } from "./lineSource.ts";
import {
  CACHE_PROVIDER_IDS,
  CAPABILITY_IDS,
  DATABASE_IDS,
  listArchetypes,
  listModules,
  listServices,
  runAdd,
  runConfig,
  runInfo,
  runNew,
  runStatus,
  upgrade,
} from "./apply.ts";

function parseSetFlag(value: string, previous: Record<string, string>): Record<string, string> {
  const result = { ...previous };
  for (const pair of value.split(",")) {
    const eq = pair.indexOf("=");
    if (eq > 0) result[pair.slice(0, eq)] = pair.slice(eq + 1);
  }
  return result;
}

function parseCapabilities(value: string): (typeof CAPABILITY_IDS)[number][] {
  const ids = value.split(",").map((s) => s.trim()).filter((s) => s !== "");
  for (const id of ids) {
    if (!(CAPABILITY_IDS as readonly string[]).includes(id)) {
      throw new InvalidArgumentError(`Unknown capability '${id}'. Allowed choices are ${CAPABILITY_IDS.join(", ")}.`);
    }
  }
  return ids as (typeof CAPABILITY_IDS)[number][];
}

// exitOverride prevents Commander from process.exit-ing on parse errors, help, or version
// display - cli.ts and repl.ts catch CommanderError themselves so the REPL stays alive. showHelp
// AfterError prints the full help text on a parse error, which is what users expect by default.
export function buildProgram(io: ReplIo): Command {
  const program = new Command();
  program
    .name("jloom")
    .description("Generate and evolve production-ready backends.")
    .version("jloom 0.2.0")
    .exitOverride()
    .showHelpAfterError();

  program
    .command("new")
    .description("Create a new project. Generates immediately; pass --dry-run to preview.")
    .option("--name <name>", "Project name / target directory.")
    .option("--service <id>", "Service-type id, e.g. 'notification-service' (omit for a bare base project).")
    .option("--base-package <pkg>", "Base Java package.", "com.example.app")
    .option("--archetype <id>", "Apply an archetype's modules on top.")
    .addOption(new Option("--database <db>", "Database for a bare project.").choices(DATABASE_IDS))
    .option("--capabilities <list>", "Comma-separated capabilities, e.g. validation,security,caching.", parseCapabilities)
    .addOption(new Option("--cache-provider <provider>", "Only relevant if 'caching' is in --capabilities.").choices(CACHE_PROVIDER_IDS))
    .option("--dry-run", "Preview without writing.", false)
    .option("-q, --quiet", "Suppress non-essential output.", false)
    .action(async (opts) => {
      await runNew(io, {
        name: opts.name,
        service: opts.service,
        basePackage: opts.basePackage,
        archetype: opts.archetype,
        database: opts.database,
        capabilities: opts.capabilities,
        cacheProvider: opts.cacheProvider,
        dryRun: opts.dryRun,
        quiet: opts.quiet,
      });
    });

  program
    .command("add [moduleIds...]")
    .description("Apply one or more modules to a project.")
    .option("--project <dir>", "Target project directory.", ".")
    .option(
      "--set <entries>",
      "Override module prompts, e.g. --set postgres.db_name=demo,postgres.port=5433",
      parseSetFlag,
      {},
    )
    .option("--dry-run", "Preview without writing.", false)
    .action(async (moduleIds: string[], opts) => {
      await runAdd(io, {
        project: opts.project,
        moduleIds,
        set: opts.set,
        dryRun: opts.dryRun,
      });
    });

  program
    .command("list")
    .description("List available modules, services, or archetypes.")
    .addOption(new Option("--what <what>", "What to list.").choices(["modules", "services", "archetypes"]).default("modules"))
    .action((opts) => {
      if (opts.what === "services") listServices();
      else if (opts.what === "archetypes") listArchetypes();
      else listModules();
    });

  program
    .command("info")
    .description("Show what a module changes before applying it.")
    .option("--module <id>", "Module id, e.g. 'postgres'")
    .action(async (opts) => {
      await runInfo(io, opts.module);
    });

  program
    .command("status")
    .description("Show applied modules and whether newer versions exist.")
    .option("--project <dir>", "Project directory.", ".")
    .action((opts) => {
      runStatus(opts.project);
    });

  program
    .command("upgrade")
    .description("Upgrade applied modules to the catalog's current versions.")
    .option("--project <dir>", "Project directory.", ".")
    .option("--module <id>", "Upgrade only this module.")
    .option("--dry-run", "Preview without writing.", false)
    .action((opts) => {
      upgrade(opts.project, opts.module, opts.dryRun);
    });

  program
    .command("config")
    .description("Print the resolved jloom configuration.")
    .action(() => {
      runConfig();
    });

  return program;
}
