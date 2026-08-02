import yaml from "js-yaml";
import { readFileSync } from "node:fs";
import { indexPaths, resolvePath } from "./catalogRoots.ts";
import { parseModuleManifest } from "./manifestLoader.ts";
import type { ModuleManifest, Upgrade } from "./types.ts";

export class ModuleRegistry {
  private readonly modulesById: Map<string, ModuleManifest>;

  private constructor(modulesById: Map<string, ModuleManifest>) {
    this.modulesById = modulesById;
  }

  static loadBundled(): ModuleRegistry {
    const byId = new Map<string, ModuleManifest>();

    for (const indexPath of indexPaths()) {
      const index = yaml.load(readFileSync(indexPath, "utf8")) as { modules?: string[] } | undefined;
      const moduleIds = index?.modules ?? [];

      for (const id of moduleIds) {
        const manifestPath = resolvePath(id, "module.yml");
        if (manifestPath === undefined) {
          throw new Error(`Listed in ${indexPath} but missing manifest for: ${id}`);
        }
        const raw = yaml.load(readFileSync(manifestPath, "utf8"));
        const manifest = parseModuleManifest(raw);
        if (manifest.id !== id) {
          throw new Error(`${indexPath} lists id '${id}' but manifest declares id '${manifest.id}'`);
        }
        if (byId.has(id)) {
          throw new Error(`Module id '${id}' is declared in more than one catalog index`);
        }
        byId.set(id, manifest);
      }
    }

    return new ModuleRegistry(byId);
  }

  all(): ModuleManifest[] {
    return [...this.modulesById.values()];
  }

  find(id: string): ModuleManifest | undefined {
    return this.modulesById.get(id);
  }

  require(id: string): ModuleManifest {
    const manifest = this.find(id);
    if (manifest === undefined) {
      throw new Error(`No such module: '${id}'. Run 'jloom list' to see available modules.`);
    }
    return manifest;
  }

  findUpgradePath(moduleId: string, fromVersion: string): Upgrade[] {
    const manifest = this.require(moduleId);
    const byFromVersion = new Map<string, Upgrade>();
    for (const upgrade of manifest.upgrades) {
      byFromVersion.set(upgrade.from, upgrade);
    }

    const path: Upgrade[] = [];
    let current = fromVersion;
    while (current !== manifest.version) {
      const step = byFromVersion.get(current);
      if (step === undefined) {
        return [];
      }
      path.push(step);
      current = step.to;
    }
    return path;
  }

  validate(alreadyApplied: string[], toApply: string[]): string[] {
    const problems: string[] = [];

    for (let i = 0; i < toApply.length; i++) {
      const id = toApply[i]!;
      const manifest = this.modulesById.get(id);
      if (manifest === undefined) {
        problems.push(`Unknown module: ${id}`);
        continue;
      }

      const satisfiedSoFar = [...alreadyApplied, ...toApply.slice(0, i)];
      for (const required of manifest.requires) {
        if (!this.isSatisfied(required, satisfiedSoFar)) {
          const satisfiedLaterInBatch = this.isSatisfied(required, toApply.slice(i + 1));
          if (satisfiedLaterInBatch) {
            problems.push(
              `${id} requires '${required}' which is in this request but listed AFTER ${id} — list it earlier, e.g.: jloom add <provider>,${id}`,
            );
          } else {
            problems.push(`${id} requires '${required}' which is not applied and not being applied now`);
          }
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

  private isSatisfied(requirement: string, effectiveModuleIds: string[]): boolean {
    if (requirement.startsWith("capability:")) {
      return effectiveModuleIds.some((id) => {
        const m = this.modulesById.get(id);
        return m !== undefined && m.provides === requirement;
      });
    }
    return effectiveModuleIds.includes(requirement);
  }
}
