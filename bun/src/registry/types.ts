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
  modules: string[];
}

export interface ArchetypeManifest {
  id: string;
  modules: string[];
  answers: Record<string, string>;
}
