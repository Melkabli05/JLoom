import { existsSync, renameSync, rmSync } from "node:fs";
import path from "node:path";

const BOOT_VERSION = "4.1.0";
const JAVA_VERSION = "25";

export type FetchLike = (url: string) => Promise<Response>;

/** `jwt-auth` is intentionally NOT here: Initializr's "security" + "oauth2-resource-server"
 * combo bundles into spring-boot-starter-security-oauth2-resource-server, which is not
 * what jwt-auth's own SecurityConfig is wired against (real ./gradlew test runs of
 * user-service showed every security test returning 403 until this entry was removed).
 * jwt-auth's own merges/gradle.yml already adds the correct artifacts. */
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

  // Drop Spring Boot's generic HELP.md, then rename application.yaml to .yml so every merge
  // fragment's filePattern of "**/application.yml" matches.
  const helpFile = path.join(targetDir, "HELP.md");
  if (existsSync(helpFile)) rmSync(helpFile);

  const yamlFile = path.join(targetDir, "src/main/resources/application.yaml");
  const ymlFile = path.join(targetDir, "src/main/resources/application.yml");
  if (existsSync(yamlFile) && !existsSync(ymlFile)) renameSync(yamlFile, ymlFile);
}
