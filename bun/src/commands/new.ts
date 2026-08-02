import path from "node:path";
import type { ReplIo } from "../lineSource.ts";
import * as output from "../output.ts";
import { apply } from "../orchestrate/moduleApplier.ts";
import * as prompts from "../prompts.ts";
import { isEmpty, requireEmpty } from "../util/projectPaths.ts";
import type { JloomContext } from "../context.ts";

const DEFAULT_PROJECT_NAME = "my-app";
const DEFAULT_BASE_PACKAGE = "com.example.app";
const BASE_PACKAGE_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$/;

export interface NewOptions {
  name?: string;
  service?: string;
  basePackage: string;
  archetype?: string;
  database?: string;
  capabilities?: string;
  cacheProvider?: string;
  dryRun: boolean;
  quiet: boolean;
}

function hasText(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}

export async function runNew(ctx: JloomContext, io: ReplIo, options: NewOptions): Promise<void> {
  if (!options.quiet && !hasText(options.name) && prompts.isInteractive()) {
    console.log(
      `${output.hint("Let's set up your project — press Enter on any question to accept the default.")}\n`,
    );
  }

  const target = await resolveTarget(io, options.name);

  const serviceChoices = new Map(ctx.services.all().map((s) => [`${s.id} — ${s.displayName}`, s.id]));
  const serviceId = await prompts.chooseOptional(
    io,
    options.service,
    "service",
    "What would you like to create?",
    serviceChoices,
    "Just a base project",
  );

  let moduleIds =
    serviceId === undefined
      ? await buildCapabilityWizard(io, options.database, options.capabilities, options.cacheProvider)
      : ctx.services.require(serviceId).modules;

  let archetypeAnswers: Record<string, string> = {};
  if (options.archetype !== undefined) {
    const manifest = ctx.archetypes.find(options.archetype);
    if (manifest === undefined) {
      throw new Error(`No such archetype: ${options.archetype}`);
    }
    moduleIds = [...moduleIds, ...manifest.modules];
    archetypeAnswers = manifest.answers;
  }

  const resolvedBasePackage = await prompts.promptWithDefault(
    io,
    options.basePackage,
    "base-package",
    "Base package",
    DEFAULT_BASE_PACKAGE,
  );
  if (!BASE_PACKAGE_PATTERN.test(resolvedBasePackage)) {
    throw new Error(
      `--base-package must be a valid dotted Java package name, e.g. com.acme.myapp (got '${resolvedBasePackage}')`,
    );
  }

  if (!options.quiet) {
    console.log(`${options.dryRun ? "Previewing " : "Setting up "}${output.accent(target)}...`);
  }

  const result = await apply(
    ctx.modules,
    target,
    moduleIds,
    archetypeAnswers,
    options.dryRun,
    resolvedBasePackage,
    path.basename(target),
  );

  switch (result.kind) {
    case "applied":
      console.log(
        [
          output.success(`Created ${target}`),
          "",
          output.heading("Next steps:"),
          `  cd ${target}`,
          "  ./gradlew test",
          "",
        ].join("\n"),
      );
      break;
    case "dryRun":
      console.log(output.hint(`Dry run — would create ${target} with modules [${moduleIds.join(", ")}]`));
      break;
    case "rejected":
      throw new Error(result.problems.join("\n  - "));
    case "failed":
      throw new Error(result.output);
  }
}

async function resolveTarget(io: ReplIo, name: string | undefined): Promise<string> {
  let candidate = name;
  while (true) {
    const resolved = await prompts.requireText(io, candidate, "name", "Project name", suggestProjectName());
    if (isEmpty(resolved)) {
      return resolved;
    }
    if (!prompts.isInteractive()) {
      requireEmpty(resolved);
    }
    console.log(output.error(`'${path.resolve(resolved)}' already exists and isn't empty.`));
    candidate = undefined;
  }
}

function suggestProjectName(): string {
  if (isEmpty(DEFAULT_PROJECT_NAME)) {
    return DEFAULT_PROJECT_NAME;
  }
  for (let i = 2; i < 1000; i++) {
    const candidate = `${DEFAULT_PROJECT_NAME}-${i}`;
    if (isEmpty(candidate)) {
      return candidate;
    }
  }
  return DEFAULT_PROJECT_NAME;
}

async function buildCapabilityWizard(
  io: ReplIo,
  database: string | undefined,
  capabilities: string | undefined,
  cacheProvider: string | undefined,
): Promise<string[]> {
  const moduleIds: string[] = ["base"];
  const databaseModule = await resolveDatabase(io, database);
  if (databaseModule !== undefined) {
    moduleIds.push(databaseModule);
  }

  const capabilityIds = await resolveCapabilityIds(io, capabilities, databaseModule);
  const cacheModule = capabilityIds.includes("caching") ? await resolveCacheProvider(io, cacheProvider) : undefined;

  for (const capability of capabilityIds) {
    let moduleId: string;
    switch (capability) {
      case "validation":
        moduleId = "validation";
        break;
      case "migrations":
        moduleId = isMysqlFamily(databaseModule) ? "flyway-mysql" : "flyway";
        break;
      case "security":
        moduleId = "jwt-auth";
        break;
      case "caching":
        moduleId = cacheModule!;
        break;
      case "aop":
        moduleId = "aop";
        break;
      case "scheduling":
        moduleId = "scheduling";
        break;
      case "async":
        moduleId = "async";
        break;
      case "auditing":
        moduleId = "auditing";
        break;
      case "observability":
        moduleId = "otel-tracing";
        break;
      case "openapi":
        moduleId = "openapi";
        break;
      case "testing":
        moduleId = "testcontainers";
        break;
      default:
        throw new Error(
          `Unknown capability '${capability}' — expected one of: validation, migrations, security, caching, aop, scheduling, async, auditing, observability, openapi, testing`,
        );
    }
    moduleIds.push(moduleId);
  }
  return moduleIds;
}

async function resolveDatabase(io: ReplIo, database: string | undefined): Promise<string | undefined> {
  if (database?.toLowerCase() === "none") {
    return undefined;
  }
  if (hasText(database)) {
    return database;
  }
  const choices = new Map([
    ["PostgreSQL", "postgres"],
    ["MySQL", "mysql"],
    ["MariaDB", "mariadb"],
    ["H2 (in-memory — dev/test only)", "h2"],
  ]);
  return prompts.chooseOptional(io, undefined, "database", "Database", choices, "None");
}

async function resolveCapabilityIds(
  io: ReplIo,
  capabilities: string | undefined,
  databaseModule: string | undefined,
): Promise<string[]> {
  if (hasText(capabilities)) {
    return capabilities
      .split(",")
      .map((s) => s.trim())
      .filter((s) => s !== "");
  }
  const choices = new Map<string, string>();
  choices.set("Validation", "validation");
  if (databaseModule !== undefined) {
    choices.set("Database migrations", "migrations");
  }
  choices.set("Security (JWT)", "security");
  choices.set("Caching", "caching");
  choices.set("AOP", "aop");
  choices.set("Scheduling", "scheduling");
  choices.set("Async processing", "async");
  if (databaseModule !== undefined) {
    choices.set("Auditing", "auditing");
  }
  choices.set("Observability", "observability");
  choices.set("OpenAPI", "openapi");
  if (databaseModule !== undefined) {
    choices.set("Testing infrastructure", "testing");
  }
  return prompts.chooseMultiple(io, "capabilities", "Capabilities", choices);
}

async function resolveCacheProvider(io: ReplIo, cacheProvider: string | undefined): Promise<string> {
  if (hasText(cacheProvider)) {
    return cacheProvider.toLowerCase() === "redis" ? "caching-redis" : "caching-caffeine";
  }
  if (!prompts.isInteractive()) {
    return "caching-caffeine";
  }
  const choices = new Map([
    ["Caffeine (in-process, no external service)", "caching-caffeine"],
    ["Redis", "caching-redis"],
  ]);
  return prompts.requireChoice(
    io,
    undefined,
    "cache-provider",
    "Cache provider",
    choices,
    "Caffeine (in-process, no external service)",
  );
}

function isMysqlFamily(databaseModule: string | undefined): boolean {
  return databaseModule === "mysql" || databaseModule === "mariadb";
}
