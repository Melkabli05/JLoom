import type { ModuleManifest, Prompt, Upgrade } from "./types.ts";

export class ManifestParseError extends Error {}

function requireString(raw: Record<string, unknown>, key: string): string {
  const value = raw[key];
  if (value === undefined || value === null) {
    throw new ManifestParseError(`module.yml missing required field: ${key}`);
  }
  return String(value);
}

function stringList(raw: unknown): string[] {
  if (raw === undefined || raw === null) {
    return [];
  }
  if (Array.isArray(raw)) {
    return raw.map(String);
  }
  throw new ManifestParseError(`expected a YAML list, got: ${typeof raw}`);
}

/** Mirrors ManifestLoader.load(InputStream) — takes an already-yaml.load()'d value. */
export function parseModuleManifest(raw: unknown): ModuleManifest {
  if (raw === null || raw === undefined || typeof raw !== "object") {
    throw new ManifestParseError("module.yml is empty");
  }
  const obj = raw as Record<string, unknown>;

  const id = requireString(obj, "id");
  const version = requireString(obj, "version");
  const requires = stringList(obj.requires);
  const conflicts = stringList(obj.conflicts);
  const provides = obj.provides === undefined || obj.provides === null ? undefined : String(obj.provides);

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

  return { id, version, requires, conflicts, provides, prompts, mergeRecipes, fileTemplates, upgrades, scaffold };
}
