import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

// Mirrors com.jloom.registry.CatalogRoots: two catalog roots, tried in order.
const ROOTS = ["modules", "services"];

const CATALOG_ROOT = path.join(import.meta.dirname, "..", "..", "catalog");

export function catalogRoot(): string {
  return CATALOG_ROOT;
}

export function resolvePath(id: string, relativePath: string): string | undefined {
  for (const root of ROOTS) {
    const candidate = path.join(CATALOG_ROOT, root, id, relativePath);
    if (existsSync(candidate)) {
      return candidate;
    }
  }
  return undefined;
}

export function readText(id: string, relativePath: string): string | undefined {
  const resolved = resolvePath(id, relativePath);
  return resolved === undefined ? undefined : readFileSync(resolved, "utf8");
}

export function indexPaths(): string[] {
  return ROOTS.map((root) => path.join(CATALOG_ROOT, root, "modules.yml"));
}
