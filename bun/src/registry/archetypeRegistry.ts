import yaml from "js-yaml";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { catalogRoot } from "./catalogRoots.ts";
import type { ArchetypeManifest } from "./types.ts";

const ARCHETYPES_DIR = path.join(catalogRoot(), "archetypes");
const INDEX_PATH = path.join(ARCHETYPES_DIR, "archetypes.yml");

export class ArchetypeRegistry {
  private readonly byId: Map<string, ArchetypeManifest>;

  private constructor(byId: Map<string, ArchetypeManifest>) {
    this.byId = byId;
  }

  static loadBundled(): ArchetypeRegistry {
    if (!existsSync(INDEX_PATH)) {
      return new ArchetypeRegistry(new Map());
    }

    const index = yaml.load(readFileSync(INDEX_PATH, "utf8")) as { archetypes?: string[] } | undefined;
    const ids = index?.archetypes ?? [];
    const result = new Map<string, ArchetypeManifest>();

    for (const id of ids) {
      const filePath = path.join(ARCHETYPES_DIR, `${id}.yml`);
      if (!existsSync(filePath)) {
        throw new Error(`Listed in archetypes.yml but missing: ${filePath}`);
      }
      const raw = yaml.load(readFileSync(filePath, "utf8")) as
        | { modules?: string[]; answers?: Record<string, unknown> }
        | undefined;
      const modules = raw?.modules ?? [];
      const answers: Record<string, string> = {};
      if (raw?.answers !== undefined && raw.answers !== null && typeof raw.answers === "object") {
        for (const [key, value] of Object.entries(raw.answers)) {
          answers[key] = String(value);
        }
      }
      result.set(id, { id, modules, answers });
    }

    return new ArchetypeRegistry(result);
  }

  all(): ArchetypeManifest[] {
    return [...this.byId.values()];
  }

  find(id: string): ArchetypeManifest | undefined {
    return this.byId.get(id);
  }
}
