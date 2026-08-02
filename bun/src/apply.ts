import path from "node:path";
import { chmodSync, existsSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { mkdirSync } from "node:fs";
import type { ReplIo } from "./lineSource.ts";
import { catalog, validate, findUpgradePath, readBytes, readText, resolvePath, type ModuleManifest, type Upgrade } from "./catalog.ts";
import { generateSpringBootProject, initializrDependenciesFor, type FetchLike } from "./initializr.ts";
import { applyOperations, compose, composeUpgrade, substitute, type ModuleSelection, type UpgradeStep } from "./merge.ts";
import { askChoice, askMultiple, askNonBlankText, askOptional, askText, isInteractive, output } from "./wizard.ts";
import {
  appliedIds,
  loadState,
  saveState,
  withApplied,
  withBasePackage,
  withProjectName,
} from "./state.ts";
import type { AppliedModule, ProjectState } from "./state.ts";

const DEFAULT_PROJECT_NAME = "my-app";
const DEFAULT_BASE_PACKAGE = "com.example.app";
const BASE_PACKAGE_PATTERN = /^[a-zA-Z_][a-zA-Z0-9_]*(\.[a-zA-Z_][a-zA-Z0-9_]*)*$/;

// ===== Project path utilities (inlined from util/projectPaths.ts) =====

export function isEmptyProject(dir: string): boolean {
  if (!existsSync(dir) || !statSync(dir).isDirectory()) return true;
  try {
    return readdirSync(dir).length === 0;
  } catch {
    return false;
  }
}

export function requireEmptyProject(target: string): void {
  if (!isEmptyProject(target)) {
    throw new Error(
      `'${path.resolve(target)}' already exists and isn't empty — pass a different --name or remove it first.`,
    );
  }
}

// ===== File template copying (inlined from scaffold/fileTreeCopier.ts) =====

function isBinary(relativePath: string): boolean {
  return relativePath.endsWith(".jar") || relativePath.endsWith(".png") || relativePath.endsWith(".ico");
}

function isExecutableScript(relativePath: string): boolean {
  if (relativePath.endsWith(".bat") || relativePath.endsWith(".cmd")) return false;
  const fileName = path.basename(relativePath);
  return fileName === "gradlew" || fileName.endsWith(".sh") || fileName.endsWith(".command");
}

export function copyModuleFiles(manifest: ModuleManifest, targetRoot: string, tokens: Record<string, string>): void {
  for (const relativePath of manifest.fileTemplates) {
    const destinationRelativePath = substitute(relativePath, tokens);
    const destination = path.join(targetRoot, destinationRelativePath);
    const sourceRelative = `files/${relativePath}`;

    if (resolvePath(manifest.id, sourceRelative) === undefined) {
      throw new Error(`Module '${manifest.id}' declares fileTemplate '${relativePath}' but no such resource exists under files/`);
    }

    mkdirSync(path.dirname(destination), { recursive: true });

    if (isBinary(relativePath)) {
      writeFileSync(destination, readBytes(manifest.id, sourceRelative)!);
    } else {
      writeFileSync(destination, substitute(readText(manifest.id, sourceRelative)!, tokens), "utf8");
    }

    if (isExecutableScript(relativePath)) {
      chmodSync(destination, 0o755);
    }
  }
}

// ===== apply() — the validate -> token-state -> compose -> scaffold -> merge -> state flow =====

export type ApplyResult =
  | { kind: "applied"; output: string }
  | { kind: "dryRun"; diff: string }
  | { kind: "rejected"; problems: string[] }
  | { kind: "failed"; output: string };

function deriveGroupId(basePackage: string): string {
  const segments = basePackage.split(".");
  return segments.length > 1 ? segments.slice(0, -1).join(".") : basePackage;
}

function projectLevelTokens(state: ProjectState): Record<string, string> {
  if (state.basePackage === undefined && state.projectName === undefined) return {};
  const tokens: Record<string, string> = {};
  if (state.projectName !== undefined) tokens.project_name = state.projectName;
  if (state.basePackage !== undefined) {
    tokens.package = state.basePackage;
    tokens.package_path = state.basePackage.split(".").join("/");
  }
  return tokens;
}

function resolveDefaultsOnly(module: ModuleManifest, overrides: Record<string, string>): Record<string, string> {
  const answers: Record<string, string> = {};
  for (const prompt of module.prompts) {
    const overrideKey = `${module.id}.${prompt.key}`;
    if (overrideKey in overrides) {
      answers[prompt.key] = overrides[overrideKey]!;
      continue;
    }
    if (prompt.defaultValue !== undefined) {
      answers[prompt.key] = prompt.defaultValue;
      continue;
    }
    throw new Error(`Module '${module.id}' requires --set ${overrideKey}=<value> (no default)`);
  }
  return answers;
}

function finish(
  targetProject: string,
  state: ProjectState,
  moduleIds: string[],
  answersByModule: Map<string, Record<string, string>>,
  dryRun: boolean,
  out: string,
  basePackage: string | undefined,
  projectName: string | undefined,
): ApplyResult {
  if (dryRun) return { kind: "dryRun", diff: out };

  let seeded = state;
  if (appliedIds(state).length === 0) {
    const name = projectName !== undefined ? projectName : path.basename(targetProject);
    seeded = withProjectName(seeded, name);
    if (basePackage !== undefined) seeded = withBasePackage(seeded, basePackage);
  }

  let updated = seeded;
  for (const id of moduleIds) {
    const manifest = catalog.modules.get(id)!;
    updated = withApplied(updated, {
      id,
      version: manifest.version,
      appliedAt: new Date().toISOString(),
      answers: answersByModule.get(id) ?? {},
    });
  }
  saveState(targetProject, updated);
  return { kind: "applied", output: out };
}

export async function apply(
  targetProject: string,
  moduleIds: string[],
  overrides: Record<string, string>,
  dryRun: boolean,
  basePackage: string | undefined,
  projectName: string | undefined,
  fetchImpl: FetchLike = fetch,
): Promise<ApplyResult> {
  const state = loadState(targetProject);

  const problems = validate(catalog, appliedIds(state), moduleIds);
  if (problems.length > 0) {
    return { kind: "rejected", problems };
  }

  const tokenState =
    appliedIds(state).length === 0 && (basePackage !== undefined || projectName !== undefined)
      ? withBasePackage(
          withProjectName(state, projectName !== undefined ? projectName : state.projectName),
          basePackage !== undefined ? basePackage : state.basePackage,
        )
      : state;
  const projectTokens = projectLevelTokens(tokenState);

  const selections: ModuleSelection[] = [];
  const answersByModule = new Map<string, Record<string, string>>();
  for (const id of moduleIds) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) {
      return { kind: "rejected", problems: [`Unknown module: ${id}`] };
    }
    const answers = resolveDefaultsOnly(manifest, overrides);
    answersByModule.set(id, answers);
    if (manifest.mergeRecipes.length > 0) {
      selections.push({ manifest, answers });
    }
  }

  if (!dryRun) {
    for (const id of moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (manifest.scaffold) {
        if (manifest.id === "base") {
          const resolvedBasePackage = tokenState.basePackage ?? DEFAULT_BASE_PACKAGE;
          const resolvedProjectName = tokenState.projectName ?? path.basename(targetProject);
          try {
            await generateSpringBootProject(
              targetProject,
              {
                groupId: deriveGroupId(resolvedBasePackage),
                artifactId: resolvedProjectName,
                packageName: resolvedBasePackage,
                name: resolvedProjectName,
                dependencies: initializrDependenciesFor(moduleIds),
              },
              fetchImpl,
            );
          } catch (err) {
            return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
          }
        }
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        copyModuleFiles(manifest, targetProject, fileTokens);
      }
    }
  }

  let recipeOutput = "no merges required";
  if (selections.length > 0) {
    const operations = compose(selections);
    if (dryRun) {
      return finish(targetProject, state, moduleIds, answersByModule, dryRun, "Dry run — no changes written.", basePackage, projectName);
    }
    try {
      applyOperations(targetProject, operations);
    } catch (err) {
      return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
    }
    recipeOutput = `Applied ${operations.length} merge operation(s).`;
  }

  if (!dryRun) {
    for (const id of moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (!manifest.scaffold && manifest.fileTemplates.length > 0) {
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        copyModuleFiles(manifest, targetProject, fileTokens);
      }
    }
  }

  return finish(targetProject, state, moduleIds, answersByModule, dryRun, recipeOutput, basePackage, projectName);
}

// ===== upgrade() — the upgrade-only flow (no Initializr call, no file copy, no scaffold) =====

export type UpgradeResult =
  | { kind: "upToDate" }
  | { kind: "upgraded"; changes: string[] }
  | { kind: "dryRun"; changes: string[] }
  | { kind: "blocked"; reasons: string[] }
  | { kind: "failed"; output: string };

interface PlannedUpgrade {
  applied: AppliedModule;
  newVersion: string;
  path: Upgrade[];
}

export function upgrade(targetProject: string, onlyModuleId: string | undefined, dryRun: boolean): UpgradeResult {
  const state = loadState(targetProject);
  const candidates = onlyModuleId === undefined
    ? state.modules
    : state.modules.filter((m) => m.id === onlyModuleId);
  if (onlyModuleId !== undefined && candidates.length === 0) {
    throw new Error(`Module '${onlyModuleId}' is not applied to this project. Run 'jloom status' to see what is.`);
  }

  const planned: PlannedUpgrade[] = [];
  const blocked: string[] = [];
  for (const applied of candidates) {
    const current = catalog.modules.get(applied.id);
    if (current === undefined || current.version === applied.version) continue;
    const path = findUpgradePath(catalog, applied.id, applied.version);
    if (path.length === 0) {
      blocked.push(`${applied.id} is at ${applied.version}, catalog has ${current.version}, but no upgrade recipe bridges them`);
      continue;
    }
    planned.push({ applied, newVersion: current.version, path });
  }

  if (blocked.length > 0) return { kind: "blocked", reasons: blocked };
  if (planned.length === 0) return { kind: "upToDate" };

  const steps: UpgradeStep[] = [];
  for (const p of planned) {
    for (const step of p.path) {
      steps.push({ moduleId: p.applied.id, recipeResourcePath: step.recipe, answers: p.applied.answers });
    }
  }

  const operations = composeUpgrade(steps);
  const changes = planned.map((p) => `${p.applied.id}: ${p.applied.version} -> ${p.newVersion}`);

  if (dryRun) return { kind: "dryRun", changes };

  try {
    applyOperations(targetProject, operations);
  } catch (err) {
    return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
  }

  let updated = state;
  for (const p of planned) {
    updated = withApplied(updated, {
      id: p.applied.id,
      version: p.newVersion,
      appliedAt: new Date().toISOString(),
      answers: p.applied.answers,
    });
  }
  saveState(targetProject, updated);
  return { kind: "upgraded", changes };
}

// ===== list/status/info/config — straight command outputs =====

function javaList(items: string[]): string {
  return `[${items.join(", ")}]`;
}

function widthOf<T>(items: T[], field: (item: T) => string): number {
  return items.reduce((max, item) => Math.max(max, field(item).length), 0);
}

function pad(text: string, width: number): string {
  return text + " ".repeat(Math.max(0, width - text.length));
}

export function listModules(): void {
  const sorted = [...catalog.modules.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (m) => m.id);
  const versionWidth = widthOf(sorted, (m) => m.version);
  const body = sorted
    .map((m) => {
      const provides = m.provides === undefined ? "" : `  provides=${m.provides}`;
      const requires = m.requires.length === 0 ? "" : `  requires=${javaList(m.requires)}`;
      const paddedId = output.accent(pad(m.id, idWidth));
      return `  ${paddedId}  ${pad(m.version, versionWidth)}${provides}${requires}`;
    })
    .join("\n");
  console.log(`${output.question("Available modules:")}\n${body}`);
}

export function listServices(): void {
  const sorted = [...catalog.services.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (s) => s.id);
  const nameWidth = widthOf(sorted, (s) => s.displayName);
  const body = sorted
    .map((s) => {
      const paddedId = output.accent(pad(s.id, idWidth));
      return `  ${paddedId}  ${pad(s.displayName, nameWidth)}  modules=${javaList(s.modules)}`;
    })
    .join("\n");
  console.log(`${output.question("Available services:")}\n${body}`);
}

export function listArchetypes(): void {
  const sorted = [...catalog.archetypes.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (a) => a.id);
  const body = sorted
    .map((a) => {
      const paddedId = output.accent(pad(a.id, idWidth));
      return `  ${paddedId}  modules=${javaList(a.modules)}`;
    })
    .join("\n");
  console.log(`${output.question("Available archetypes:")}\n${body}`);
}

export function runList(what: string): void {
  switch (what.toLowerCase()) {
    case "archetypes":
      listArchetypes();
      return;
    case "services":
      listServices();
      return;
    default:
      listModules();
  }
}

export function runStatus(project: string): void {
  const projectPath = path.resolve(project);
  const state = loadState(projectPath);

  if (state.modules.length === 0) {
    console.log(`No applied modules in ${projectPath}.`);
    return;
  }

  const lines: string[] = [output.question(`Applied modules in ${projectPath}:`)];
  for (const applied of state.modules) {
    const latest = catalog.modules.get(applied.id);
    const note = latest !== undefined && latest.version !== applied.version
      ? `catalog has ${latest.version} — run 'jloom upgrade' to pick it up`
      : output.hint("up to date");
    const paddedId = output.accent(pad(applied.id, 25));
    lines.push(`  ${paddedId} ${pad(applied.version, 10)} ${note}`);
  }
  console.log(lines.join("\n"));
}

export async function runInfo(io: ReplIo, moduleId: string | undefined): Promise<void> {
  const id = await askNonBlankText(io, moduleId, "module", "Which module? (see 'jloom list' for ids)");
  const mod = catalog.modules.get(id);
  if (mod === undefined) {
    throw new Error(`No such module: '${id}'. Run 'jloom list' to see available modules.`);
  }

  const lines: string[] = [`${output.accent(mod.id)} ${mod.version}`];
  if (mod.requires.length > 0) lines.push(`  requires: [${mod.requires.join(", ")}]`);
  if (mod.fileTemplates.length > 0) {
    lines.push(output.question("  adds new files:"));
    for (const t of mod.fileTemplates) lines.push(`    + ${t}`);
  }
  if (mod.mergeRecipes.length > 0) {
    lines.push(output.question("  edits existing files via merge recipes:"));
    for (const t of mod.mergeRecipes) lines.push(`    ~ ${t}`);
  }
  console.log(lines.join("\n"));
}

export function runConfig(): void {
  console.log(
    [
      output.question("jloom config:"),
      `  color:       ${output.isColorOn ? "ON" : "OFF"} (tty=${process.stdout.isTTY === true})`,
      "  state dir:   <project>/.jloom (per-project; not configurable)",
      "",
    ].join("\n"),
  );
}

// ===== add — needs the wizard, so it lives here too =====

export async function runAdd(io: ReplIo, opts: { project: string; moduleIds: string[]; set: Record<string, string>; dryRun: boolean }): Promise<void> {
  let ids = opts.moduleIds;
  if (ids.length === 0) {
    const typed = await askNonBlankText(
      io,
      undefined,
      "modules",
      "Which modules? (comma or space separated ids, see 'jloom list')",
    );
    ids = typed
      .split(/[,\s]+/)
      .map((s) => s.trim())
      .filter((s) => s !== "");
  }

  const result = await apply(path.resolve(opts.project), ids, opts.set, opts.dryRun, undefined, undefined);

  switch (result.kind) {
    case "applied":
      console.log(output.ok(`Applied: [${ids.join(", ")}]`));
      break;
    case "dryRun":
      console.log(output.hint("Dry run — no changes written."));
      break;
    case "rejected":
      throw new Error(result.problems.join("\n  - "));
    case "failed":
      throw new Error(`Merge run failed:\n${result.output}`);
  }
}

// ===== new — the wizard (project name loop, service choice, capability wizard, etc.) =====

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

export async function runNew(io: ReplIo, options: NewOptions): Promise<void> {
  if (!options.quiet && !hasText(options.name) && isInteractive()) {
    console.log(`${output.hint("Let's set up your project — press Enter on any question to accept the default.")}\n`);
  }

  const target = await resolveTarget(io, askText, options.name);

  const serviceChoices = new Map([...catalog.services.values()].map((s) => [`${s.id} — ${s.displayName}`, s.id]));
  const serviceId = await askOptional(
    io,
    options.service,
    "service",
    "What would you like to create?",
    serviceChoices,
    "Just a base project",
  );

  let moduleIds =
    serviceId === undefined
      ? await buildCapabilityWizard(io, askChoice, askMultiple, options.database, options.capabilities, options.cacheProvider)
      : catalog.services.get(serviceId)!.modules;

  let archetypeAnswers: Record<string, string> = {};
  if (options.archetype !== undefined) {
    const manifest = catalog.archetypes.get(options.archetype);
    if (manifest === undefined) throw new Error(`No such archetype: ${options.archetype}`);
    moduleIds = [...moduleIds, ...manifest.modules];
    archetypeAnswers = manifest.answers;
  }

  const resolvedBasePackage = await askText(
    io,
    options.basePackage,
    "base-package",
    "Base package",
    DEFAULT_BASE_PACKAGE,
  );
  if (!BASE_PACKAGE_PATTERN.test(resolvedBasePackage)) {
    throw new Error(`--base-package must be a valid dotted Java package name, e.g. com.acme.myapp (got '${resolvedBasePackage}')`);
  }

  if (!options.quiet) {
    console.log(`${options.dryRun ? "Previewing " : "Setting up "}${output.accent(target)}...`);
  }

  const result = await apply(target, moduleIds, archetypeAnswers, options.dryRun, resolvedBasePackage, path.basename(target));

  switch (result.kind) {
    case "applied":
      console.log(
        [
          output.ok(`Created ${target}`),
          "",
          output.question("Next steps:"),
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

async function resolveTarget(io: ReplIo, askText: typeof import("./wizard.ts")["askText"], name: string | undefined): Promise<string> {
  let candidate = name;
  while (true) {
    const resolved = await askText(io, candidate, "name", "Project name", suggestProjectName());
    if (isEmptyProject(resolved)) return resolved;
    if (!isInteractive()) requireEmptyProject(resolved);
    console.log(output.err(`'${path.resolve(resolved)}' already exists and isn't empty.`));
    candidate = undefined;
  }
}

function suggestProjectName(): string {
  if (isEmptyProject(DEFAULT_PROJECT_NAME)) return DEFAULT_PROJECT_NAME;
  for (let i = 2; i < 1000; i++) {
    const candidate = `${DEFAULT_PROJECT_NAME}-${i}`;
    if (isEmptyProject(candidate)) return candidate;
  }
  return DEFAULT_PROJECT_NAME;
}

async function buildCapabilityWizard(
  io: ReplIo,
  askChoice: typeof import("./wizard.ts")["askChoice"],
  askMultiple: typeof import("./wizard.ts")["askMultiple"],
  database: string | undefined,
  capabilities: string | undefined,
  cacheProvider: string | undefined,
): Promise<string[]> {
  const moduleIds: string[] = ["base"];
  const databaseModule = await resolveDatabase(io, askChoice, database);
  if (databaseModule !== undefined) moduleIds.push(databaseModule);

  const capabilityIds = await resolveCapabilityIds(io, capabilities, databaseModule, askMultiple);
  const cacheModule = capabilityIds.includes("caching")
    ? await resolveCacheProvider(io, askChoice, cacheProvider)
    : undefined;

  for (const capability of capabilityIds) {
    const moduleId = mapCapability(capability, databaseModule, cacheModule);
    moduleIds.push(moduleId);
  }
  return moduleIds;
}

function mapCapability(capability: string, databaseModule: string | undefined, cacheModule: string | undefined): string {
  switch (capability) {
    case "validation":
      return "validation";
    case "migrations":
      return databaseModule === "mysql" || databaseModule === "mariadb" ? "flyway-mysql" : "flyway";
    case "security":
      return "jwt-auth";
    case "caching":
      return cacheModule!;
    case "aop":
      return "aop";
    case "scheduling":
      return "scheduling";
    case "async":
      return "async";
    case "auditing":
      return "auditing";
    case "observability":
      return "otel-tracing";
    case "openapi":
      return "openapi";
    case "testing":
      return "testcontainers";
    default:
      throw new Error(`Unknown capability '${capability}' — expected one of: validation, migrations, security, caching, aop, scheduling, async, auditing, observability, openapi, testing`);
  }
}

async function resolveDatabase(io: ReplIo, askChoice: typeof import("./wizard.ts")["askChoice"], database: string | undefined): Promise<string | undefined> {
  if (database?.toLowerCase() === "none") return undefined;
  if (hasText(database)) return database;
  const choices = new Map([
    ["PostgreSQL", "postgres"],
    ["MySQL", "mysql"],
    ["MariaDB", "mariadb"],
    ["H2 (in-memory — dev/test only)", "h2"],
  ]);
  return askOptional(io, undefined, "database", "Database", choices, "None");
}

async function resolveCapabilityIds(
  io: ReplIo,
  capabilities: string | undefined,
  databaseModule: string | undefined,
  askMultiple: typeof import("./wizard.ts")["askMultiple"],
): Promise<string[]> {
  if (hasText(capabilities)) {
    return capabilities.split(",").map((s) => s.trim()).filter((s) => s !== "");
  }
  const choices = new Map<string, string>();
  choices.set("Validation", "validation");
  if (databaseModule !== undefined) choices.set("Database migrations", "migrations");
  choices.set("Security (JWT)", "security");
  choices.set("Caching", "caching");
  choices.set("AOP", "aop");
  choices.set("Scheduling", "scheduling");
  choices.set("Async processing", "async");
  if (databaseModule !== undefined) choices.set("Auditing", "auditing");
  choices.set("Observability", "observability");
  choices.set("OpenAPI", "openapi");
  if (databaseModule !== undefined) choices.set("Testing infrastructure", "testing");
  return askMultiple(io, "Capabilities", choices);
}

async function resolveCacheProvider(io: ReplIo, askChoice: typeof import("./wizard.ts")["askChoice"], cacheProvider: string | undefined): Promise<string> {
  if (hasText(cacheProvider)) {
    return cacheProvider.toLowerCase() === "redis" ? "caching-redis" : "caching-caffeine";
  }
  if (!isInteractive()) return "caching-caffeine";
  const choices = new Map([
    ["Caffeine (in-process, no external service)", "caching-caffeine"],
    ["Redis", "caching-redis"],
  ]);
  return askChoice(
    io,
    undefined,
    "cache-provider",
    "Cache provider",
    choices,
    "Caffeine (in-process, no external service)",
  );
}
