import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

// Mirrors com.jloom.registry.CatalogRoots: two catalog roots, tried in order.
const ROOTS = ["modules", "services"];

/** Locate the bundled catalog. Order matters and is the *fast* path on the left - we don't
 * fall back to anything fancy, just two plain directory lookups. In development, the catalog
 * lives at <repo>/bun/catalog (resolved via import.meta.dirname). In a compiled standalone
 * binary (bun build --compile), it lives next to the binary at <bin-dir>/catalog, copied by
 * the `compile` npm script. We can't rely on import.meta.dirname inside the compiled binary
 * because bun's --compile bundling rewrites that to /, so we derive it from the actual
 * binary's location via process.argv[0] there. */
function findCatalogRoot(): string {
  // In a compiled standalone binary (bun build --compile), process.execPath is the binary's
  // path and the catalog sits next to it (copied by the `compile` npm script). process.argv[0]
  // is just "bun" inside the bundle, not useful for locating the binary. In dev/runtime
  // mode, process.execPath is the Bun runtime, not the project, so we fall back to walking
  // up from the source file via import.meta.dirname.
  if (process.execPath && !process.execPath.endsWith("bun") && !process.execPath.endsWith("/bun")) {
    const besideBinary = path.join(path.dirname(process.execPath), "catalog");
    if (existsSync(besideBinary)) {
      return besideBinary;
    }
  }
  return path.join(import.meta.dirname, "..", "..", "catalog");
}

const CATALOG_ROOT = findCatalogRoot();

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

/** Raw bytes, no encoding - required for binary templates (gradle-wrapper.jar, icons, etc.). */
export function readBytes(id: string, relativePath: string): Buffer | undefined {
  const resolved = resolvePath(id, relativePath);
  return resolved === undefined ? undefined : readFileSync(resolved);
}

export function indexPaths(): string[] {
  return ROOTS.map((root) => path.join(CATALOG_ROOT, root, "modules.yml"));
}
