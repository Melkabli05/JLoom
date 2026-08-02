import { ArchetypeRegistry } from "./registry/archetypeRegistry.ts";
import { ModuleRegistry } from "./registry/moduleRegistry.ts";
import { ServiceRegistry } from "./registry/serviceRegistry.ts";

export interface JloomContext {
  modules: ModuleRegistry;
  services: ServiceRegistry;
  archetypes: ArchetypeRegistry;
}

export function createContext(): JloomContext {
  return {
    modules: ModuleRegistry.loadBundled(),
    services: ServiceRegistry.loadBundled(),
    archetypes: ArchetypeRegistry.loadBundled(),
  };
}
