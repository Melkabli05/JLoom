import { existsSync, renameSync, rmSync } from "node:fs";
import path from "node:path";
import { fetchInitializrMetadata, isVersionCompatible } from "./initializrMetadata.ts";
const BOOT_VERSION = "4.1.0";
const JAVA_VERSION = "25";
export type FetchLike = (url: string, init?: RequestInit) => Promise<Response>;
export const INITIALIZR_DEPENDENCY_MAP: Record<string, string[]> = {
  postgres: ["data-jpa", "postgresql"],
  mysql: ["data-jpa", "mysql"],
  mariadb: ["data-jpa", "mariadb"],
  h2: ["data-jpa", "h2"],
  flyway: ["flyway"],
  "flyway-mysql": ["flyway"],
  validation: ["validation"],
  "caching-caffeine": ["cache"],
  "caching-redis": ["cache", "data-redis"],
  "otel-tracing": ["actuator", "distributed-tracing", "prometheus"],
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
export async function generateSpringBootProject(
  targetDir: string,
  opts: InitializrOptions,
  fetchImpl: FetchLike = fetch,
): Promise<{ warnings: string[] }> {
  const metadata = await fetchInitializrMetadata(fetchImpl);
  if (!metadata.bootVersionIds.has(BOOT_VERSION)) {
    throw new Error(
      `Spring Initializr no longer offers bootVersion ${BOOT_VERSION} - jloom needs a maintenance update to a currently supported version.`,
    );
  }
  const warnings: string[] = [];
  const compatibleDependencies = opts.dependencies.filter((dep) => {
    const range = metadata.dependencyRanges.get(dep);
    const compatible = isVersionCompatible(BOOT_VERSION, range);
    if (!compatible) {
      warnings.push(
        `'${dep}' isn't compatible with Spring Boot ${BOOT_VERSION} (requires ${range}) and was skipped.`,
      );
    }
    return compatible;
  });
  const url = buildInitializrUrl({ ...opts, dependencies: compatibleDependencies });
  const res = await fetchImpl(url);
  if (!res.ok) {
    throw new Error(`Spring Initializr request failed (${res.status} ${res.statusText}): ${url}`);
  }
  const buf = await res.arrayBuffer();
  const archive = new Bun.Archive(buf);
  await archive.extract(targetDir);
  const helpFile = path.join(targetDir, "HELP.md");
  if (existsSync(helpFile)) rmSync(helpFile);
  const yamlFile = path.join(targetDir, "src/main/resources/application.yaml");
  const ymlFile = path.join(targetDir, "src/main/resources/application.yml");
  if (existsSync(yamlFile) && !existsSync(ymlFile)) renameSync(yamlFile, ymlFile);
  return { warnings };
}
