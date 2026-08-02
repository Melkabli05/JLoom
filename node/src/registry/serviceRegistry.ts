import yaml from "js-yaml";
import { readFileSync } from "node:fs";
import path from "node:path";
import { catalogRoot } from "./catalogRoots.ts";
import type { ServiceManifest } from "./types.ts";

const INDEX_PATH = path.join(catalogRoot(), "services.yml");

function stringList(raw: unknown): string[] {
  return Array.isArray(raw) ? raw.map(String) : [];
}

function parseModules(raw: unknown): Record<string, string[]> {
  if (Array.isArray(raw)) {
    return { __default__: raw.map(String) };
  }
  if (raw !== null && typeof raw === "object") {
    const result: Record<string, string[]> = {};
    for (const [key, value] of Object.entries(raw as Record<string, unknown>)) {
      if (Array.isArray(value)) {
        result[key] = value.map(String);
      }
    }
    return result;
  }
  return {};
}

export class ServiceRegistry {
  private readonly servicesById: Map<string, ServiceManifest>;

  private constructor(servicesById: Map<string, ServiceManifest>) {
    this.servicesById = servicesById;
  }

  static loadBundled(): ServiceRegistry {
    const root = yaml.load(readFileSync(INDEX_PATH, "utf8")) as { services?: unknown[] } | undefined;
    const list = root?.services;
    const result = new Map<string, ServiceManifest>();

    if (Array.isArray(list)) {
      for (const item of list) {
        if (item !== null && typeof item === "object") {
          const m = item as Record<string, unknown>;
          const id = String(m.id);
          const displayName = String(m.displayName);
          const description = m.description === undefined || m.description === null ? "" : String(m.description);
          const framework = stringList(m.framework);
          const modulesPerFramework = parseModules(m.modules);
          result.set(id, { id, displayName, description, framework, modulesPerFramework });
        }
      }
    }

    return new ServiceRegistry(result);
  }

  all(): ServiceManifest[] {
    return [...this.servicesById.values()];
  }

  find(id: string): ServiceManifest | undefined {
    return this.servicesById.get(id);
  }

  require(id: string): ServiceManifest {
    const manifest = this.find(id);
    if (manifest === undefined) {
      throw new Error(`No such service: '${id}'. Run 'jloom list services' to see available service types.`);
    }
    return manifest;
  }
}
