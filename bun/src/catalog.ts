import yaml from "js-yaml";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
const ROOTS = ["modules", "services"];




function findCatalogRoot(): string {
  const isCompiled = process.execPath && !process.execPath.endsWith("bun") && !process.execPath.endsWith("/bun");
  if (isCompiled) {
    const besideBinary = path.join(path.dirname(process.execPath), "catalog");
    if (existsSync(besideBinary)) return besideBinary;
  }
  return path.join(import.meta.dirname, "..", "catalog");
}
export const CATALOG_ROOT = findCatalogRoot();
export function resolvePath(id: string, relativePath: string): string | undefined {
  for (const root of ROOTS) {
    const candidate = path.join(CATALOG_ROOT, root, id, relativePath);
    if (existsSync(candidate)) return candidate;
  }
  return undefined;
}
export function readText(id: string, relativePath: string): string | undefined {
  const resolved = resolvePath(id, relativePath);
  return resolved === undefined ? undefined : readFileSync(resolved, "utf8");
}
export function readBytes(id: string, relativePath: string): Buffer | undefined {
  const resolved = resolvePath(id, relativePath);
  return resolved === undefined ? undefined : readFileSync(resolved);
}
export function catalogRoot(): string {
  return CATALOG_ROOT;
}
export interface Prompt {
  key: string;
  type: "string" | "secret";
  defaultValue?: string;
}
export interface Upgrade {
  from: string;
  to: string;
  recipe: string;
}
export interface ModuleManifest {
  id: string;
  version: string;
  requires: string[];
  conflicts: string[];
  provides?: string;
  description?: string;
  prompts: Prompt[];
  mergeRecipes: string[];
  fileTemplates: string[];
  upgrades: Upgrade[];
  scaffold: boolean;
}
export interface ServiceManifest {
  id: string;
  displayName: string;
  description: string;
  modules: string[];
}
export interface ArchetypeManifest {
  id: string;
  modules: string[];
  answers: Record<string, string>;
}
export class ManifestParseError extends Error {}
function requireString(raw: Record<string, unknown>, key: string): string {
  const value = raw[key];
  if (value === undefined || value === null) {
    throw new ManifestParseError(`module.yml missing required field: ${key}`);
  }
  return String(value);
}
function stringList(raw: unknown): string[] {
  if (raw === undefined || raw === null) return [];
  if (Array.isArray(raw)) return raw.map(String);
  throw new ManifestParseError(`expected a YAML list, got: ${typeof raw}`);
}
function parseModuleManifest(raw: unknown): ModuleManifest {
  if (raw === null || raw === undefined || typeof raw !== "object") {
    throw new ManifestParseError("module.yml is empty");
  }
  const obj = raw as Record<string, unknown>;
  const id = requireString(obj, "id");
  const version = requireString(obj, "version");
  const requires = stringList(obj.requires);
  const conflicts = stringList(obj.conflicts);
  const provides = obj.provides === undefined || obj.provides === null ? undefined : String(obj.provides);
  const description = obj.description === undefined || obj.description === null ? undefined : String(obj.description);
  const prompts: Prompt[] = [];
  if (Array.isArray(obj.prompts)) {
    for (const item of obj.prompts) {
      if (item !== null && typeof item === "object") {
        const m = item as Record<string, unknown>;
        const key = String(m.key);
        const type = m.type === undefined || m.type === null ? "string" : String(m.type);
        if (type !== "string" && type !== "secret") {
          throw new ManifestParseError(
            `module.yml prompt '${key}' has unsupported type '${type}' (supported: string, secret)`,
          );
        }
        const defaultValue = m.default === undefined || m.default === null ? undefined : String(m.default);
        prompts.push({ key, type, defaultValue });
      }
    }
  }
  const mergeRecipes = stringList(obj.mergeRecipes);
  const fileTemplates = stringList(obj.fileTemplates);
  const upgrades: Upgrade[] = [];
  if (Array.isArray(obj.upgrades)) {
    for (const item of obj.upgrades) {
      if (item !== null && typeof item === "object") {
        const m = item as Record<string, unknown>;
        upgrades.push({
          from: requireString(m, "from"),
          to: requireString(m, "to"),
          recipe: requireString(m, "recipe"),
        });
      }
    }
  }
  const scaffold = obj.scaffold === true;
  return { id, version, requires, conflicts, provides, description, prompts, mergeRecipes, fileTemplates, upgrades, scaffold };
}
export interface Catalog {
  modules: Map<string, ModuleManifest>;
  services: Map<string, ServiceManifest>;
  archetypes: Map<string, ArchetypeManifest>;
}
function loadIndexModules(): Map<string, ModuleManifest> {
  const byId = new Map<string, ModuleManifest>();
  for (const root of ROOTS) {
    const indexPath = path.join(CATALOG_ROOT, root, "modules.yml");
    if (!existsSync(indexPath)) continue;
    const ids = (yaml.load(readFileSync(indexPath, "utf8")) as { modules?: string[] } | undefined)?.modules ?? [];
    for (const id of ids) {
      const manifestPath = resolvePath(id, "module.yml");
      if (manifestPath === undefined) {
        throw new Error(`Listed in ${indexPath} but missing manifest for: ${id}`);
      }
      const manifest = parseModuleManifest(yaml.load(readFileSync(manifestPath, "utf8")));
      if (manifest.id !== id) {
        throw new Error(`${indexPath} lists id '${id}' but manifest declares id '${manifest.id}'`);
      }
      if (byId.has(id)) {
        throw new Error(`Module id '${id}' is declared in more than one catalog index`);
      }
      byId.set(id, manifest);
    }
  }
  return byId;
}
function loadServices(): Map<string, ServiceManifest> {
  const byId = new Map<string, ServiceManifest>();
  const indexPath = path.join(CATALOG_ROOT, "services.yml");
  if (!existsSync(indexPath)) return byId;
  const list = (yaml.load(readFileSync(indexPath, "utf8")) as { services?: unknown[] } | undefined)?.services;
  if (!Array.isArray(list)) return byId;
  for (const item of list) {
    if (item !== null && typeof item === "object") {
      const m = item as Record<string, unknown>;
      const modules = Array.isArray(m.modules) ? m.modules.map(String) : [];
      byId.set(String(m.id), {
        id: String(m.id),
        displayName: String(m.displayName),
        description: m.description === undefined || m.description === null ? "" : String(m.description),
        modules,
      });
    }
  }
  return byId;
}
function loadArchetypes(): Map<string, ArchetypeManifest> {
  const byId = new Map<string, ArchetypeManifest>();
  const dir = path.join(CATALOG_ROOT, "archetypes");
  const indexPath = path.join(dir, "archetypes.yml");
  if (!existsSync(indexPath)) return byId;
  const ids = (yaml.load(readFileSync(indexPath, "utf8")) as { archetypes?: string[] } | undefined)?.archetypes ?? [];
  for (const id of ids) {
    const filePath = path.join(dir, `${id}.yml`);
    if (!existsSync(filePath)) {
      throw new Error(`Listed in archetypes.yml but missing: ${filePath}`);
    }
    const raw = yaml.load(readFileSync(filePath, "utf8")) as
      | { modules?: string[]; answers?: Record<string, unknown> }
      | undefined;
    const answers: Record<string, string> = {};
    if (raw?.answers !== undefined && raw.answers !== null && typeof raw.answers === "object") {
      for (const [key, value] of Object.entries(raw.answers)) {
        answers[key] = String(value);
      }
    }
    byId.set(id, { id, modules: raw?.modules ?? [], answers });
  }
  return byId;
}
export function loadCatalog(): Catalog {
  return {
    modules: loadIndexModules(),
    services: loadServices(),
    archetypes: loadArchetypes(),
  };
}
export const catalog = loadCatalog();
export function findUpgradePath(catalog: Catalog, moduleId: string, fromVersion: string): Upgrade[] {
  const manifest = catalog.modules.get(moduleId);
  if (manifest === undefined) {
    throw new Error(`No such module: '${moduleId}'. Run 'jloom list' to see available modules.`);
  }
  const byFromVersion = new Map<string, Upgrade>();
  for (const upgrade of manifest.upgrades) {
    byFromVersion.set(upgrade.from, upgrade);
  }
  const path: Upgrade[] = [];
  let current = fromVersion;
  while (current !== manifest.version) {
    const step = byFromVersion.get(current);
    if (step === undefined) return [];
    path.push(step);
    current = step.to;
  }
  return path;
}
export function validate(catalog: Catalog, alreadyApplied: string[], toApply: string[]): string[] {
  const problems: string[] = [];
  for (let i = 0; i < toApply.length; i++) {
    const id = toApply[i]!;
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) {
      problems.push(`Unknown module: ${id}`);
      continue;
    }
    const satisfiedSoFar = [...alreadyApplied, ...toApply.slice(0, i)];
    for (const required of manifest.requires) {
      if (!isSatisfied(catalog, required, satisfiedSoFar)) {
        const satisfiedLaterInBatch = isSatisfied(catalog, required, toApply.slice(i + 1));
        problems.push(
          satisfiedLaterInBatch
            ? `${id} requires '${required}' which is in this request but listed AFTER ${id} — list it earlier, e.g.: jloom add <provider>,${id}`
            : `${id} requires '${required}' which is not applied and not being applied now`,
        );
      }
    }
    const effective = [...alreadyApplied, ...toApply];
    for (const conflict of manifest.conflicts) {
      if (effective.includes(conflict)) {
        problems.push(`${id} conflicts with '${conflict}', which is already applied or in this request`);
      }
    }
  }
  return problems;
}
function isSatisfied(catalog: Catalog, requirement: string, effectiveModuleIds: string[]): boolean {
  if (requirement.startsWith("capability:")) {
    return effectiveModuleIds.some((id) => {
      const m = catalog.modules.get(id);
      return m !== undefined && m.provides === requirement;
    });
  }
  return effectiveModuleIds.includes(requirement);
}

export function capabilityProviders(catalog: Catalog, capability: string): ModuleManifest[] {
  return [...catalog.modules.values()].filter((m) => m.provides === capability);
}

export interface ResolutionResult {
  moduleIds: string[];
  added: string[];
  problems: string[];
}

// undefined = couldn't resolve (non-interactive, or the user declined) — the caller reports the
// candidates as a problem instead of guessing.
export type ProviderPicker = (capability: string, candidates: ModuleManifest[]) => Promise<string | undefined>;

// Auto-resolves + auto-orders a requested module list against the catalog's own
// requires/provides/conflicts metadata: a post-order DFS over requestedIds resolves each
// module's requirements before the module itself, which alone gives correct topological
// ordering (no more "list it earlier" errors). A capability requirement with more than one
// provider is delegated to pickProvider (asked at most once per capability per resolution
// pass — the answer is cached and reused for every other module needing the same capability).
export async function resolveModules(
  catalog: Catalog,
  alreadyApplied: string[],
  requestedIds: string[],
  pickProvider: ProviderPicker,
  preResolved: Map<string, string> = new Map(),
): Promise<ResolutionResult> {
  const resolved = new Set(alreadyApplied);
  const inProgress = new Set<string>();
  const capabilityAnswers = new Map(preResolved);
  const ordered: string[] = [];
  const problems: string[] = [];

  // If the user explicitly requested a module that provides some capability, treat that as the
  // answer for that capability regardless of where it's listed in the batch — this is what
  // makes e.g. `jloom add flyway-mysql mariadb` (dependency listed AFTER its dependent) just
  // work instead of asking to disambiguate among every catalog provider of capability:
  // relational-db. preResolved (explicit --database/--cache-provider style answers) still wins
  // if both are somehow set.
  for (const id of requestedIds) {
    const manifest = catalog.modules.get(id);
    if (manifest?.provides !== undefined && !capabilityAnswers.has(manifest.provides)) {
      capabilityAnswers.set(manifest.provides, id);
    }
  }

  async function resolveRequirement(requirement: string): Promise<void> {
    if (isSatisfied(catalog, requirement, [...resolved])) return;
    if (!requirement.startsWith("capability:")) {
      await resolveId(requirement);
      return;
    }
    const cached = capabilityAnswers.get(requirement);
    if (cached !== undefined) {
      await resolveId(cached);
      return;
    }
    const candidates = capabilityProviders(catalog, requirement);
    if (candidates.length === 0) {
      problems.push(`No module in the catalog provides '${requirement}'`);
      return;
    }
    if (candidates.length === 1) {
      capabilityAnswers.set(requirement, candidates[0]!.id);
      await resolveId(candidates[0]!.id);
      return;
    }
    const chosen = await pickProvider(requirement, candidates);
    if (chosen === undefined) {
      problems.push(
        `'${requirement}' has multiple providers [${candidates.map((c) => c.id).join(", ")}] — pass one explicitly, e.g. jloom add ${candidates[0]!.id}`,
      );
      return;
    }
    capabilityAnswers.set(requirement, chosen);
    await resolveId(chosen);
  }

  async function resolveId(id: string): Promise<void> {
    if (resolved.has(id)) return;
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) {
      problems.push(`Unknown module: ${id}`);
      return;
    }
    if (inProgress.has(id)) {
      problems.push(`Circular requirement detected involving '${id}'`);
      return;
    }
    inProgress.add(id);
    for (const requirement of manifest.requires) {
      await resolveRequirement(requirement);
    }
    inProgress.delete(id);
    if (resolved.has(id)) return;
    ordered.push(id);
    resolved.add(id);
  }

  for (const id of requestedIds) {
    await resolveId(id);
  }

  const effective = [...alreadyApplied, ...ordered];
  for (const id of ordered) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) continue;
    for (const conflict of manifest.conflicts) {
      if (conflict !== id && effective.includes(conflict)) {
        problems.push(`${id} conflicts with '${conflict}', which is already applied or in this request`);
      }
    }
  }

  // "added" = resolved beyond what was literally requested — computed at the end (not tracked
  // during resolution) so it's correct regardless of the order dependencies were resolved in,
  // e.g. `jloom add flyway-mysql mariadb` must never report "mariadb" as auto-added just
  // because it happened to get pulled in while resolving flyway-mysql's requirement first.
  const requestedSet = new Set(requestedIds);
  const added = ordered.filter((id) => !requestedSet.has(id));

  return { moduleIds: ordered, added, problems };
}
