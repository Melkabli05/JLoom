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
  framework: string[];
  modulesPerFramework: Record<string, string[]>;
}

/** Mirrors ServiceManifest.modulesFor(frameworkId) — falls back to the "__default__" bucket. */
export function modulesFor(manifest: ServiceManifest, frameworkId: string): string[] {
  return manifest.modulesPerFramework[frameworkId] ?? manifest.modulesPerFramework.__default__ ?? [];
}

export interface ArchetypeManifest {
  id: string;
  modules: string[];
  answers: Record<string, string>;
}
