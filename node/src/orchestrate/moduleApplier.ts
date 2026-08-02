import path from "node:path";
import { applyOperations } from "../merge/executor.ts";
import { compose } from "../merge/recipeComposer.ts";
import type { ModuleSelection } from "../merge/recipeComposer.ts";
import { ModuleRegistry } from "../registry/moduleRegistry.ts";
import type { ModuleManifest } from "../registry/types.ts";
import { copy } from "../scaffold/fileTreeCopier.ts";
import {
  appliedIds,
  loadState,
  saveState,
  withApplied,
  withBasePackage,
  withProjectName,
} from "../state/projectStateStore.ts";
import type { ProjectState } from "../state/projectStateStore.ts";

export type ApplyResult =
  | { kind: "applied"; output: string }
  | { kind: "dryRun"; diff: string }
  | { kind: "rejected"; problems: string[] }
  | { kind: "failed"; output: string };

function resolveDefaultsOnly(module: ModuleManifest, overrides: Record<string, string>): Record<string, string> {
  const answers: Record<string, string> = {};
  for (const prompt of module.prompts) {
    const overrideKey = `${module.id}.${prompt.key}`;
    if (overrideKey in overrides) {
      answers[prompt.key] = overrides[overrideKey]!;
      continue;
    }
    if (prompt.defaultValue !== undefined) {
      answers[prompt.key] = prompt.defaultValue;
      continue;
    }
    throw new Error(`Module '${module.id}' requires --set ${overrideKey}=<value> (no default)`);
  }
  return answers;
}

function computeTokenState(
  state: ProjectState,
  basePackage: string | undefined,
  projectName: string | undefined,
): ProjectState {
  const isFirstApply = appliedIds(state).length === 0;
  if (!isFirstApply || (basePackage === undefined && projectName === undefined)) {
    return state;
  }
  const withName = withProjectName(state, projectName !== undefined ? projectName : state.projectName);
  return withBasePackage(withName, basePackage !== undefined ? basePackage : state.basePackage);
}

function projectLevelTokens(state: ProjectState): Record<string, string> {
  if (state.basePackage === undefined && state.projectName === undefined) {
    return {};
  }
  const tokens: Record<string, string> = {};
  if (state.projectName !== undefined) {
    tokens.project_name = state.projectName;
  }
  if (state.basePackage !== undefined) {
    tokens.package = state.basePackage;
    tokens.package_path = state.basePackage.split(".").join("/");
  }
  return tokens;
}

function finish(
  registry: ModuleRegistry,
  targetProject: string,
  state: ProjectState,
  moduleIds: string[],
  answersByModule: Map<string, Record<string, string>>,
  dryRun: boolean,
  output: string,
  basePackage: string | undefined,
  projectName: string | undefined,
): ApplyResult {
  if (dryRun) {
    return { kind: "dryRun", diff: output };
  }

  let seeded = state;
  if (appliedIds(state).length === 0) {
    const name = projectName !== undefined ? projectName : path.basename(targetProject);
    seeded = withProjectName(seeded, name);
    if (basePackage !== undefined) {
      seeded = withBasePackage(seeded, basePackage);
    }
  }

  let updated = seeded;
  for (const id of moduleIds) {
    const manifest = registry.require(id);
    updated = withApplied(updated, {
      id,
      version: manifest.version,
      appliedAt: new Date().toISOString(),
      answers: answersByModule.get(id) ?? {},
    });
  }
  saveState(targetProject, updated);
  return { kind: "applied", output };
}

/** Mirrors ModuleApplier.apply(...). Note on dry-run: the Java version could still shell out
 * to a real `gradlew rewriteDryRun` when a wrapper existed on disk, but its own CLI callers
 * never actually displayed that diff text to the user (always a static "Dry run — ..."
 * message regardless) - so dropping OpenRewrite loses no user-visible behavior here. This
 * version still calls compose(...) during a dry run (cheap, in-memory) so a malformed merge
 * fragment is still caught early, but never touches disk when dryRun is true. */
export function apply(
  registry: ModuleRegistry,
  targetProject: string,
  moduleIds: string[],
  overrides: Record<string, string>,
  dryRun: boolean,
  basePackage: string | undefined,
  projectName: string | undefined,
): ApplyResult {
  const state = loadState(targetProject);

  const problems = registry.validate(appliedIds(state), moduleIds);
  if (problems.length > 0) {
    return { kind: "rejected", problems };
  }

  const tokenState = computeTokenState(state, basePackage, projectName);
  const projectTokens = projectLevelTokens(tokenState);

  const selections: ModuleSelection[] = [];
  const answersByModule = new Map<string, Record<string, string>>();

  for (const id of moduleIds) {
    const manifest = registry.require(id);
    const answers = resolveDefaultsOnly(manifest, overrides);
    answersByModule.set(id, answers);
    if (manifest.mergeRecipes.length > 0) {
      selections.push({ manifest, answers });
    }
  }

  if (!dryRun) {
    for (const id of moduleIds) {
      const manifest = registry.require(id);
      if (manifest.scaffold) {
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        copy(manifest, targetProject, fileTokens);
      }
    }
  }

  let recipeOutput = "no merges required";
  if (selections.length > 0) {
    const operations = compose(selections);
    if (dryRun) {
      return finish(registry, targetProject, state, moduleIds, answersByModule, dryRun, "Dry run — no changes written.", basePackage, projectName);
    }
    try {
      applyOperations(targetProject, operations);
    } catch (err) {
      return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
    }
    recipeOutput = `Applied ${operations.length} merge operation(s).`;
  }

  if (!dryRun) {
    for (const id of moduleIds) {
      const manifest = registry.require(id);
      if (!manifest.scaffold && manifest.fileTemplates.length > 0) {
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        copy(manifest, targetProject, fileTokens);
      }
    }
  }

  return finish(registry, targetProject, state, moduleIds, answersByModule, dryRun, recipeOutput, basePackage, projectName);
}
