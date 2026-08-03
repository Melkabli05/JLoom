import path from "node:path";
import { existsSync, readdirSync, statSync } from "node:fs";
import * as clack from "@clack/prompts";
import { catalog, capabilityProviders } from "../catalog.ts";
import { apply, DEFAULT_BASE_PACKAGE } from "../apply.ts";
import { capabilityChoices, formatCapabilityLabel, interactivePickProvider } from "../capabilities.ts";
import { askConfirm, askMultiple, askOptional, askText, isInteractive, output } from "../wizard.ts";
const DEFAULT_PROJECT_NAME = "my-app";
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
export interface NewOptions {
  name?: string;
  service?: string;
  basePackage?: string;
  archetype?: string;
  database?: string;
  capabilities?: string[];
  cacheProvider?: string;
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
export async function runNew(options: NewOptions): Promise<void> {
  const interactiveWizard = isInteractive() && !options.quiet;
  if (interactiveWizard && !hasText(options.name)) {
    clack.intro("jloom — create a new project");
  }
  const target = await resolveTarget(options.name);
  const serviceChoices = [
    ...[...catalog.services.values()].map((s) => ({ value: s.id, label: `${s.id} — ${s.displayName}` })),
    { value: undefined, label: "Just a base project" },
  ];
  const serviceId = await askOptional(options.service, "What would you like to create?", serviceChoices);
  let moduleIds: string[];
  let preResolved: Map<string, string>;
  if (serviceId === undefined) {
    const wizardResult = await buildCapabilityWizard(options.database, options.capabilities, options.cacheProvider);
    moduleIds = wizardResult.moduleIds;
    preResolved = wizardResult.preResolved;
  } else {
    moduleIds = catalog.services.get(serviceId)!.modules;
    preResolved = new Map();
  }
  let archetypeAnswers: Record<string, string> = {};
  if (options.archetype !== undefined) {
    const manifest = catalog.archetypes.get(options.archetype);
    if (manifest === undefined) throw new Error(`No such archetype: ${options.archetype}`);
    moduleIds = [...moduleIds, ...manifest.modules];
    archetypeAnswers = manifest.answers;
  }
  const basePackageInput = isInteractive() ? options.basePackage : (options.basePackage ?? DEFAULT_BASE_PACKAGE);
  const resolvedBasePackage = await askText(
    basePackageInput,
    "base-package",
    "Base package",
    DEFAULT_BASE_PACKAGE,
    validateBasePackage,
  );
  if (interactiveWizard && !options.dryRun && !options.yes) {
    clack.note(
      `Project:      ${path.basename(target)}\nLocation:     ${target}\nBase package: ${resolvedBasePackage}\nModules:      ${moduleIds.join(", ")}`,
      "Summary",
    );
    if (!(await askConfirm("Proceed?", true))) {
      clack.outro("Aborted — no changes were made.");
      return;
    }
  }
  const spin = interactiveWizard ? clack.spinner() : undefined;
  if (interactiveWizard) {
    spin?.start(options.dryRun ? "Previewing..." : "Setting up...");
  } else if (!options.quiet) {
    console.log(`${options.dryRun ? "Previewing " : "Setting up "}${output.accent(target)}...`);
  }
  const result = await apply({
    targetProject: target,
    moduleIds,
    overrides: archetypeAnswers,
    dryRun: options.dryRun,
    basePackage: resolvedBasePackage,
    projectName: path.basename(target),
    pickProvider: interactivePickProvider,
    preResolved,
  });
  spin?.stop("Done.");
  switch (result.kind) {
    case "applied":
      if (result.autoAdded !== undefined) {
        const line = `Also added (to satisfy a dependency): ${result.autoAdded.join(", ")}`;
        if (interactiveWizard) {
          clack.log.info(line);
        } else {
          console.log(output.hint(line));
        }
      }
      if (result.warnings !== undefined) {
        for (const warning of result.warnings) {
          if (interactiveWizard) {
            clack.log.warn(warning);
          } else {
            console.log(output.err(warning));
          }
        }
      }
      if (interactiveWizard) {
        clack.outro(`Created ${target}. Next: cd ${target} && ./gradlew test`);
      } else {
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
      }
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
async function resolveTarget(name: string | undefined): Promise<string> {
  let candidate = name;
  while (true) {
    const resolved = await askText(candidate, "name", "Project name", suggestProjectName());
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
interface CapabilityWizardResult {
  moduleIds: string[];
  preResolved: Map<string, string>;
}
async function buildCapabilityWizard(
  database: string | undefined,
  capabilities: string[] | undefined,
  cacheProvider: string | undefined,
): Promise<CapabilityWizardResult> {
  const moduleIds: string[] = ["base"];
  const preResolved = new Map<string, string>();
  let databaseModule = await resolveDatabase(database);
  if (databaseModule !== undefined) {
    moduleIds.push(databaseModule);
    preResolved.set("capability:relational-db", databaseModule);
  }
  const capabilityIds = await resolveCapabilityIds(capabilities);
  if (capabilityIds.includes("migrations") && databaseModule === undefined) {
    const providers = capabilityProviders(catalog, "capability:relational-db");
    databaseModule = await interactivePickProvider("capability:relational-db", providers);
    if (databaseModule !== undefined) {
      moduleIds.push(databaseModule);
      preResolved.set("capability:relational-db", databaseModule);
    }
  }
  for (const capability of capabilityIds) {
    if (capability === "migrations") {
      if (databaseModule !== undefined) moduleIds.push(resolveMigrationsModule(databaseModule));
      continue;
    }
    const requirement = `capability:${capability}`;
    const providers = capabilityProviders(catalog, requirement);
    if (providers.length === 0) continue;
    if (providers.length === 1) {
      moduleIds.push(providers[0]!.id);
      continue;
    }
    const preseeded = capability === "caching" && cacheProvider !== undefined ? cacheProvider : undefined;
    const chosen = preseeded ?? (await interactivePickProvider(requirement, providers));
    if (chosen !== undefined) {
      preResolved.set(requirement, chosen);
      moduleIds.push(chosen);
    }
  }
  return { moduleIds, preResolved };
}
function resolveMigrationsModule(databaseModuleId: string): string {
  return databaseModuleId === "mysql" || databaseModuleId === "mariadb" ? "flyway-mysql" : "flyway";
}
async function resolveDatabase(database: string | undefined): Promise<string | undefined> {
  if (database === "none") return undefined;
  if (database !== undefined) return database;
  const providers = capabilityProviders(catalog, "capability:relational-db");
  return askOptional(undefined, "Database", [
    ...providers.map((m) => ({ value: m.id, label: m.description ?? formatCapabilityLabel(m.id) })),
    { value: undefined, label: "None" },
  ]);
}
async function resolveCapabilityIds(capabilities: string[] | undefined): Promise<string[]> {
  if (capabilities !== undefined) return capabilities;
  return askMultiple("Capabilities (space to toggle, a to select all)", capabilityChoices());
}
