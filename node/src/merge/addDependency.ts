import type { AddDependencyOp } from "./types.ts";

const DEPENDENCIES_BLOCK = /dependencies\s*\{/;

/**
 * Anchor-based text insertion into a `dependencies { }` block — not a general Kotlin/Groovy
 * DSL parser. This is safe specifically because jloom controls the shape of every build file
 * it generates (via its own base/base-micronaut scaffold templates), which always use
 * `configuration("group:artifact[:version]")` syntax. Idempotent: skips if the same
 * groupId:artifactId coordinate is already present anywhere in the file.
 */
export function addDependencyContent(buildFileContent: string, op: AddDependencyOp): string {
  const coordinate = `${op.groupId}:${op.artifactId}`;
  if (buildFileContent.includes(coordinate)) {
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
