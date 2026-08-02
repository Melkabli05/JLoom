import { existsSync, globSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
import { addDependencyContent } from "./addDependency.ts";
import { changePropertyKeyContent } from "./changePropertyKey.ts";
import { createTextFile } from "./createTextFile.ts";
import { mergeYamlContent } from "./mergeYaml.ts";
import type { MergeOperation } from "./types.ts";

const BUILD_FILE_CANDIDATES = ["build.gradle.kts", "build.gradle"];

function findBuildFile(projectRoot: string): string {
  for (const candidate of BUILD_FILE_CANDIDATES) {
    const full = path.join(projectRoot, candidate);
    if (existsSync(full)) {
      return full;
    }
  }
  throw new Error(`No build.gradle.kts or build.gradle found under ${projectRoot}`);
}

function filesMatching(projectRoot: string, filePattern: string): string[] {
  return globSync(filePattern, { cwd: projectRoot }).map((relative) => path.join(projectRoot, relative));
}

/** Applies one parsed merge operation directly to files on disk — this in-process execution
 * is what replaces shelling out to the target project's own ./gradlew rewriteRun. */
export function applyOperation(projectRoot: string, op: MergeOperation): void {
  switch (op.type) {
    case "org.openrewrite.gradle.AddDependency": {
      const buildFile = findBuildFile(projectRoot);
      const content = readFileSync(buildFile, "utf8");
      writeFileSync(buildFile, addDependencyContent(content, op), "utf8");
      break;
    }
    case "org.openrewrite.yaml.MergeYaml": {
      for (const file of filesMatching(projectRoot, op.filePattern)) {
        const content = existsSync(file) ? readFileSync(file, "utf8") : "";
        writeFileSync(file, mergeYamlContent(content, op), "utf8");
      }
      break;
    }
    case "org.openrewrite.yaml.ChangePropertyKey": {
      for (const file of filesMatching(projectRoot, op.filePattern)) {
        const content = readFileSync(file, "utf8");
        writeFileSync(file, changePropertyKeyContent(content, op), "utf8");
      }
      break;
    }
    case "org.openrewrite.text.CreateTextFile": {
      createTextFile(projectRoot, op);
      break;
    }
  }
}

export function applyOperations(projectRoot: string, operations: MergeOperation[]): void {
  for (const op of operations) {
    applyOperation(projectRoot, op);
  }
}
