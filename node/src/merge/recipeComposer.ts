import yaml from "js-yaml";
import { readText } from "../registry/catalogRoots.ts";
import { substitute } from "./tokens.ts";
import type { ModuleManifest } from "../registry/types.ts";
import type { MergeOperation } from "./types.ts";

export interface ModuleSelection {
  manifest: ModuleManifest;
  answers: Record<string, string>;
}

export interface UpgradeStep {
  moduleId: string;
  recipeResourcePath: string;
  answers: Record<string, string>;
}

interface Fragment {
  moduleId: string;
  resourcePaths: string[];
  answers: Record<string, string>;
}

/** Mirrors RecipeComposer.compose(...) / composeUpgrade(...) — returns the flat operation
 * list directly rather than re-serializing to a YAML document, since nothing external (no
 * Gradle/OpenRewrite subprocess) needs to read it back as text anymore. */
export function compose(modules: ModuleSelection[]): MergeOperation[] {
  return composeFragments(
    modules.map((s) => ({
      moduleId: s.manifest.id,
      resourcePaths: s.manifest.mergeRecipes,
      answers: s.answers,
    })),
  );
}

export function composeUpgrade(steps: UpgradeStep[]): MergeOperation[] {
  return composeFragments(
    steps.map((s) => ({
      moduleId: s.moduleId,
      resourcePaths: [s.recipeResourcePath],
      answers: s.answers,
    })),
  );
}

function composeFragments(fragments: Fragment[]): MergeOperation[] {
  const operations: MergeOperation[] = [];

  for (const fragment of fragments) {
    for (const resourcePath of fragment.resourcePaths) {
      const text = readFragment(fragment.moduleId, resourcePath);
      const substituted = substitute(text, fragment.answers);
      const parsed = yaml.load(substituted);
      if (!Array.isArray(parsed)) {
        throw new Error(
          `Fragment ${fragment.moduleId}/${resourcePath} is not a YAML list (got ${typeof parsed})`,
        );
      }
      for (const entry of parsed) {
        operations.push(parseOperation(fragment.moduleId, resourcePath, entry));
      }
    }
  }

  return operations;
}

function readFragment(moduleId: string, resourcePath: string): string {
  const text = readText(moduleId, resourcePath);
  if (text === undefined) {
    throw new Error(`Module '${moduleId}' declares recipe '${resourcePath}' but no such resource exists`);
  }
  return text;
}

function parseOperation(moduleId: string, resourcePath: string, entry: unknown): MergeOperation {
  if (entry === null || typeof entry !== "object") {
    throw new Error(`Fragment ${moduleId}/${resourcePath} contains a non-map entry: ${String(entry)}`);
  }
  const keys = Object.keys(entry as Record<string, unknown>);
  if (keys.length !== 1) {
    throw new Error(`Fragment ${moduleId}/${resourcePath} contains a malformed recipe entry: ${JSON.stringify(entry)}`);
  }
  const recipeType = keys[0]!;
  const params = (entry as Record<string, unknown>)[recipeType] as Record<string, unknown>;

  switch (recipeType) {
    case "org.openrewrite.gradle.AddDependency":
      return {
        type: recipeType,
        groupId: String(params.groupId),
        artifactId: String(params.artifactId),
        version: params.version === undefined ? undefined : String(params.version),
        configuration: String(params.configuration),
      };
    case "org.openrewrite.yaml.MergeYaml":
      return {
        type: recipeType,
        key: String(params.key),
        yaml: String(params.yaml),
        filePattern: String(params.filePattern),
      };
    case "org.openrewrite.yaml.ChangePropertyKey":
      return {
        type: recipeType,
        oldPropertyKey: String(params.oldPropertyKey),
        newPropertyKey: String(params.newPropertyKey),
        filePattern: String(params.filePattern),
      };
    case "org.openrewrite.text.CreateTextFile":
      return {
        type: recipeType,
        relativeFileName: String(params.relativeFileName),
        fileContents: String(params.fileContents),
        overwriteExisting: params.overwriteExisting === true,
      };
    default:
      throw new Error(`Fragment ${moduleId}/${resourcePath} uses unsupported recipe type: ${recipeType}`);
  }
}
