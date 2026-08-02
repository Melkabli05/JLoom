import type { AddDependencyOp } from "./types.ts";

const DEPENDENCIES_BLOCK = /dependencies\s*\{/;

/**
 * Anchor-based text insertion into a `dependencies { }` block — not a general Kotlin/Groovy
 * DSL parser. This is safe specifically because jloom controls the shape of every build file
 * it generates (via its own base/base-micronaut scaffold templates), which always use
 * `configuration("group:artifact[:version]")` syntax. Idempotent per (configuration, coordinate)
 * pair, not per bare coordinate: the same library legitimately appears under two different
 * configurations for some modules (e.g. Lombok needs both `compileOnly` and
 * `annotationProcessor` entries for the same groupId:artifactId - deduping on the coordinate
 * alone would silently drop the second one, which is exactly what happened here until this
 * fix: MapStruct couldn't see Lombok-generated getters because the `annotationProcessor`
 * entry was skipped as "already present" due to the `compileOnly` entry sharing the substring).
 */
export function addDependencyContent(buildFileContent: string, op: AddDependencyOp): string {
  const coordinate = `${op.groupId}:${op.artifactId}`;
  const configuredCoordinate = `${op.configuration}("${coordinate}`;
  if (buildFileContent.includes(configuredCoordinate)) {
    return buildFileContent;
  }

  const match = buildFileContent.match(DEPENDENCIES_BLOCK);
  if (match === null || match.index === undefined) {
    throw new Error("Could not find a 'dependencies {' block to insert into");
  }

  const insertAt = match.index + match[0].length;
  const versionSuffix = op.version === undefined ? "" : `:${op.version}`;
  const line = `\n    ${op.configuration}("${coordinate}${versionSuffix}")`;
  return buildFileContent.slice(0, insertAt) + line + buildFileContent.slice(insertAt);
}
