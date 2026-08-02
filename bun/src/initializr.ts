import { existsSync, renameSync, rmSync } from "node:fs";
import path from "node:path";

const BOOT_VERSION = "4.1.0";
const JAVA_VERSION = "25";

/** Just the callable shape of fetch, not `typeof fetch` (which also requires the `preconnect`
 * static property) - this is the part that's actually swappable for a test fake. */
export type FetchLike = (url: string) => Promise<Response>;

/** jloom module id -> Spring Initializr dependency id(s). Doesn't need to be exhaustive: any
 * module's own mergeRecipes still run after this (see moduleApplier.ts) and the existing
 * AddDependency merge op is idempotent per (configuration, coordinate), so it silently no-ops
 * for anything Initializr already added — this table only picks a good *starting* set. */
export const INITIALIZR_DEPENDENCY_MAP: Record<string, string[]> = {
  postgres: ["data-jpa", "postgresql"],
  mysql: ["data-jpa", "mysql"],
  mariadb: ["data-jpa", "mariadb"],
  h2: ["data-jpa", "h2"],
  flyway: ["flyway"],
  "flyway-mysql": ["flyway"],
  validation: ["validation"],
  "jwt-auth": ["security", "oauth2-resource-server"],
  "caching-caffeine": ["cache"],
  "caching-redis": ["cache", "data-redis"],
  "otel-tracing": ["actuator", "distributed-tracing", "prometheus"],
  openapi: ["springdoc-openapi"],
  testcontainers: ["testcontainers"],
};

export function initializrDependenciesFor(moduleIds: string[]): string[] {
  const deps = new Set<string>(["web"]);
  for (const id of moduleIds) {
    for (const dep of INITIALIZR_DEPENDENCY_MAP[id] ?? []) {
      deps.add(dep);
    }
  }
  return [...deps];
}

export interface InitializrOptions {
  groupId: string;
  artifactId: string;
  packageName: string;
  name: string;
  dependencies: string[];
}

export function buildInitializrUrl(opts: InitializrOptions): string {
  const params = new URLSearchParams({
    type: "gradle-project-kotlin",
    language: "java",
    bootVersion: BOOT_VERSION,
    javaVersion: JAVA_VERSION,
    packaging: "jar",
    configurationFileFormat: "yaml",
    groupId: opts.groupId,
    artifactId: opts.artifactId,
    name: opts.name,
    packageName: opts.packageName,
  });
  if (opts.dependencies.length > 0) {
    params.set("dependencies", opts.dependencies.join(","));
  }
  return `https://start.spring.io/starter.tgz?${params.toString()}`;
}

/** Calls the real Spring Initializr service to generate the project skeleton, replacing what
 * jloom used to hand-maintain as static file templates. fetchImpl is injectable so tests never
 * need a live network call (see initializr.test.ts). */
export async function generateSpringBootProject(
  targetDir: string,
  opts: InitializrOptions,
  fetchImpl: FetchLike = fetch,
): Promise<void> {
  const url = buildInitializrUrl(opts);
  const res = await fetchImpl(url);
  if (!res.ok) {
    throw new Error(`Spring Initializr request failed (${res.status} ${res.statusText}): ${url}`);
  }
  const buf = await res.arrayBuffer();
  const archive = new Bun.Archive(buf);
  await archive.extract(targetDir);

  // Spring Boot's own generic boilerplate help text — not something jloom's generated
  // projects want lingering alongside jloom's own README.
  const helpFile = path.join(targetDir, "HELP.md");
  if (existsSync(helpFile)) {
    rmSync(helpFile);
  }

  // Initializr's configurationFileFormat=yaml writes application.yaml, but every one of
  // jloom's own merge fragments targets application.yml (filePattern: "**/application.yml") -
  // normalize the extension rather than touching every fragment in the catalog.
  const yamlFile = path.join(targetDir, "src/main/resources/application.yaml");
  const ymlFile = path.join(targetDir, "src/main/resources/application.yml");
  if (existsSync(yamlFile) && !existsSync(ymlFile)) {
    renameSync(yamlFile, ymlFile);
  }
}
