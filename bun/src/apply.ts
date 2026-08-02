import path from "node:path";
import { chmodSync, existsSync, mkdirSync, readdirSync, statSync, writeFileSync } from "node:fs";
import type { ReplIo } from "./lineSource.ts";
import { catalog, validate, findUpgradePath, readBytes, readText, resolvePath, type ModuleManifest, type Upgrade } from "./catalog.ts";
import { generateSpringBootProject, initializrDependenciesFor, type FetchLike } from "./initializr.ts";
import { applyOperations, compose, composeUpgrade, substitute, type ModuleSelection, type UpgradeStep } from "./merge.ts";
import { askChoice, askConfirm, askMultiple, askNonBlankText, askOptional, askText, isInteractive, output, sectionHeader } from "./wizard.ts";
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

function isEmptyProject(dir: string): boolean {
  if (!existsSync(dir) || !statSync(dir).isDirectory()) return true;
  try {
    return readdirSync(dir).length === 0;
  } catch {
    return false;
  }
}

function requireEmptyProject(target: string): void {
  if (!isEmptyProject(target)) {
    throw new Error(
      `'${path.resolve(target)}' already exists and isn't empty — pass a different --name or remove it first.`,
    );
  }
}

function isBinary(relativePath: string): boolean {
  return relativePath.endsWith(".jar") || relativePath.endsWith(".png") || relativePath.endsWith(".ico");
}

function isExecutableScript(relativePath: string): boolean {
  if (relativePath.endsWith(".bat") || relativePath.endsWith(".cmd")) return false;
  const fileName = path.basename(relativePath);
  return fileName === "gradlew" || fileName.endsWith(".sh") || fileName.endsWith(".command");
}

function copyModuleFiles(manifest: ModuleManifest, targetRoot: string, tokens: Record<string, string>): void {
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

function seedStateForFreshApply(
  state: ProjectState,
  basePackage: string | undefined,
  projectName: string | undefined,
): ProjectState {
  if (appliedIds(state).length > 0) return state;
  if (basePackage === undefined && projectName === undefined) return state;
  return withBasePackage(
    withProjectName(state, projectName !== undefined ? projectName : state.projectName),
    basePackage !== undefined ? basePackage : state.basePackage,
  );
}

interface FinishOpts {
  targetProject: string;
  state: ProjectState;
  moduleIds: string[];
  answersByModule: Map<string, Record<string, string>>;
  dryRun: boolean;
  out: string;
  basePackage: string | undefined;
  projectName: string | undefined;
}

function finish(opts: FinishOpts): ApplyResult {
  if (opts.dryRun) return { kind: "dryRun", diff: opts.out };

  let seeded = opts.state;
  if (appliedIds(opts.state).length === 0) {
    const name = opts.projectName !== undefined ? opts.projectName : path.basename(opts.targetProject);
    seeded = withProjectName(seeded, name);
    if (opts.basePackage !== undefined) seeded = withBasePackage(seeded, opts.basePackage);
  }

  let updated = seeded;
  for (const id of opts.moduleIds) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) throw new Error(`Unknown module: '${id}'`);
    updated = withApplied(updated, {
      id,
      version: manifest.version,
      appliedAt: new Date().toISOString(),
      answers: opts.answersByModule.get(id) ?? {},
    });
  }
  saveState(opts.targetProject, updated);
  return { kind: "applied", output: opts.out };
}

export interface ApplyOpts {
  targetProject: string;
  moduleIds: string[];
  overrides: Record<string, string>;
  dryRun: boolean;
  basePackage: string | undefined;
  projectName: string | undefined;
  fetchImpl?: FetchLike;
}

export async function apply(opts: ApplyOpts): Promise<ApplyResult> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const state = loadState(opts.targetProject);

  // Validate first so an unknown/invalid batch fails fast without iterating moduleIds twice.
  const problems = validate(catalog, appliedIds(state), opts.moduleIds);
  if (problems.length > 0) return { kind: "rejected", problems };

  const tokenState = seedStateForFreshApply(state, opts.basePackage, opts.projectName);
  const projectTokens = projectLevelTokens(tokenState);

  const selections: ModuleSelection[] = [];
  const answersByModule = new Map<string, Record<string, string>>();
  for (const id of opts.moduleIds) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) {
      return { kind: "rejected", problems: [`Unknown module: '${id}'`] };
    }
    const answers = resolveDefaultsOnly(manifest, opts.overrides);
    answersByModule.set(id, answers);
    if (manifest.mergeRecipes.length > 0) {
      selections.push({ manifest, answers });
    }
  }

  if (!opts.dryRun) {
    for (const id of opts.moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (manifest.scaffold) {
        if (manifest.id === "base") {
          const resolvedBasePackage = tokenState.basePackage ?? DEFAULT_BASE_PACKAGE;
          const resolvedProjectName = tokenState.projectName ?? path.basename(opts.targetProject);
          try {
            await generateSpringBootProject(
              opts.targetProject,
              {
                groupId: deriveGroupId(resolvedBasePackage),
                artifactId: resolvedProjectName,
                packageName: resolvedBasePackage,
                name: resolvedProjectName,
                dependencies: initializrDependenciesFor(opts.moduleIds),
              },
              fetchImpl,
            );
          } catch (err) {
            return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
          }
        }
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        try {
          copyModuleFiles(manifest, opts.targetProject, fileTokens);
        } catch (err) {
          return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
        }
      }
    }
  }

  let recipeOutput = "no merges required";
  if (selections.length > 0) {
    const operations = compose(selections);
    if (opts.dryRun) {
      return finish({
        targetProject: opts.targetProject,
        state,
        moduleIds: opts.moduleIds,
        answersByModule,
        dryRun: opts.dryRun,
        out: "Dry run — no changes written.",
        basePackage: opts.basePackage,
        projectName: opts.projectName,
      });
    }
    try {
      applyOperations(opts.targetProject, operations);
    } catch (err) {
      return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
    }
    recipeOutput = `Applied ${operations.length} merge operation(s).`;
  }

  if (!opts.dryRun) {
    for (const id of opts.moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (!manifest.scaffold && manifest.fileTemplates.length > 0) {
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        try {
          copyModuleFiles(manifest, opts.targetProject, fileTokens);
        } catch (err) {
          return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
        }
      }
    }
  }

  return finish({
    targetProject: opts.targetProject,
    state,
    moduleIds: opts.moduleIds,
    answersByModule,
    dryRun: opts.dryRun,
    out: recipeOutput,
    basePackage: opts.basePackage,
    projectName: opts.projectName,
  });
}

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

export interface AddOpts {
  project: string;
  moduleIds: string[];
  set: Record<string, string>;
  dryRun: boolean;
  yes?: boolean;
}

export async function runAdd(io: ReplIo, opts: AddOpts): Promise<void> {
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

  const targetProject = path.resolve(opts.project);

  if (isInteractive() && !opts.dryRun && !opts.yes) {
    console.log(
      [
        "",
        output.question("Summary:"),
        `  Project: ${output.accent(targetProject)}`,
        `  Adding:  ${output.accent(ids.join(", "))}`,
        "",
      ].join("\n"),
    );
    if (!(await askConfirm(io, "Proceed?", true))) {
      console.log(output.hint("Aborted — no changes were made."));
      return;
    }
  }

  const result = await apply({
    targetProject,
    moduleIds: ids,
    overrides: opts.set,
    dryRun: opts.dryRun,
    basePackage: undefined,
    projectName: undefined,
  });

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

export const DATABASE_IDS = ["postgres", "mysql", "mariadb", "h2", "none"] as const;
export const CACHE_PROVIDER_IDS = ["caffeine", "redis"] as const;
export const CAPABILITY_IDS = [
  "validation",
  "migrations",
  "security",
  "caching",
  "aop",
  "scheduling",
  "async",
  "auditing",
  "observability",
  "openapi",
  "testing",
] as const;

export interface NewOptions {
  name?: string;
  service?: string;
  basePackage?: string;
  archetype?: string;
  database?: (typeof DATABASE_IDS)[number];
  capabilities?: (typeof CAPABILITY_IDS)[number][];
  cacheProvider?: (typeof CACHE_PROVIDER_IDS)[number];
  dryRun: boolean;
  quiet: boolean;
  yes?: boolean;
}

function hasText(value: string | undefined): value is string {
  return value !== undefined && value.trim() !== "";
}

function validateBasePackage(value: string): string | undefined {
  return BASE_PACKAGE_PATTERN.test(value)
    ? undefined
    : `must be a valid dotted Java package name, e.g. com.acme.myapp (got '${value}')`;
}

export async function runNew(io: ReplIo, options: NewOptions): Promise<void> {
  if (!options.quiet && !hasText(options.name) && isInteractive()) {
    console.log(`${output.hint("Let's set up your project — press Enter on any question to accept the default.")}\n`);
  }

  if (!hasText(options.name) && isInteractive()) sectionHeader("Project");
  const target = await resolveTarget(io, options.name);

  if (!hasText(options.service) && isInteractive()) sectionHeader("Type");
  const serviceChoices = new Map([...catalog.services.values()].map((s) => [`${s.id} — ${s.displayName}`, s.id]));
  const serviceId = await askOptional(
    io,
    options.service,
    "What would you like to create?",
    serviceChoices,
    "Just a base project",
  );

  let moduleIds =
    serviceId === undefined
      ? await buildCapabilityWizard(io, options.database, options.capabilities, options.cacheProvider)
      : catalog.services.get(serviceId)!.modules;

  let archetypeAnswers: Record<string, string> = {};
  if (options.archetype !== undefined) {
    const manifest = catalog.archetypes.get(options.archetype);
    if (manifest === undefined) throw new Error(`No such archetype: ${options.archetype}`);
    moduleIds = [...moduleIds, ...manifest.modules];
    archetypeAnswers = manifest.answers;
  }

  if (!hasText(options.basePackage) && isInteractive()) sectionHeader("Package");
  // Only prompt interactively when the flag was actually omitted (isInteractive() branch below);
  // non-interactive runs keep silently defaulting to DEFAULT_BASE_PACKAGE exactly as before.
  const basePackageInput = isInteractive() ? options.basePackage : (options.basePackage ?? DEFAULT_BASE_PACKAGE);
  const resolvedBasePackage = await askText(
    io,
    basePackageInput,
    "base-package",
    "Base package",
    DEFAULT_BASE_PACKAGE,
    validateBasePackage,
  );

  if (isInteractive() && !options.quiet && !options.dryRun && !options.yes) {
    console.log(
      [
        "",
        output.question("Summary:"),
        `  Project:      ${output.accent(path.basename(target))}`,
        `  Location:     ${output.accent(target)}`,
        `  Base package: ${output.accent(resolvedBasePackage)}`,
        `  Modules:      ${output.accent(moduleIds.join(", "))}`,
        "",
      ].join("\n"),
    );
    if (!(await askConfirm(io, "Proceed?", true))) {
      console.log(output.hint("Aborted — no changes were made."));
      return;
    }
  }

  if (!options.quiet) {
    console.log(`${options.dryRun ? "Previewing " : "Setting up "}${output.accent(target)}...`);
  }

  const result = await apply({
    targetProject: target,
    moduleIds,
    overrides: archetypeAnswers,
    dryRun: options.dryRun,
    basePackage: resolvedBasePackage,
    projectName: path.basename(target),
  });

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

async function resolveTarget(io: ReplIo, name: string | undefined): Promise<string> {
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
  database: string | undefined,
  capabilities: string[] | undefined,
  cacheProvider: string | undefined,
): Promise<string[]> {
  const moduleIds: string[] = ["base"];
  const databaseModule = await resolveDatabase(io, database);
  if (databaseModule !== undefined) moduleIds.push(databaseModule);

  const capabilityIds = await resolveCapabilityIds(io, capabilities, databaseModule);
  const cacheModule = capabilityIds.includes("caching")
    ? await resolveCacheProvider(io, cacheProvider)
    : undefined;

  for (const capability of capabilityIds) {
    moduleIds.push(CAPABILITY_TO_MODULE(capability, databaseModule, cacheModule));
  }
  return moduleIds;
}

const CAPABILITY_TO_MODULE = (capability: string, databaseModule: string | undefined, cacheModule: string | undefined): string => {
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
      throw new Error(`Unknown capability '${capability}' — expected one of: ${CAPABILITY_IDS.join(", ")}`);
  }
};

// database/capabilities/cacheProvider are already validated against DATABASE_IDS/
// CAPABILITY_IDS/CACHE_PROVIDER_IDS by Commander's .choices() (see program.ts) whenever the
// caller passed them via a CLI flag - these functions only need to fill in the interactive
// prompt for whichever ones were omitted.

async function resolveDatabase(io: ReplIo, database: string | undefined): Promise<string | undefined> {
  if (database === "none") return undefined;
  if (database !== undefined) return database;
  if (isInteractive()) sectionHeader("Database");
  const choices = new Map([
    ["PostgreSQL", "postgres"],
    ["MySQL", "mysql"],
    ["MariaDB", "mariadb"],
    ["H2 (in-memory — dev/test only)", "h2"],
  ]);
  return askOptional(io, undefined, "Database", choices, "None");
}

async function resolveCapabilityIds(
  io: ReplIo,
  capabilities: string[] | undefined,
  databaseModule: string | undefined,
): Promise<string[]> {
  if (capabilities !== undefined) return capabilities;
  if (isInteractive()) sectionHeader("Capabilities");
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

async function resolveCacheProvider(io: ReplIo, cacheProvider: string | undefined): Promise<string> {
  if (cacheProvider !== undefined) return cacheProvider === "redis" ? "caching-redis" : "caching-caffeine";
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
