import path from "node:path";
import { chmodSync, existsSync, mkdirSync, writeFileSync } from "node:fs";
import {
  catalog,
  validate,
  resolveModules,
  readBytes,
  readText,
  resolvePath,
  type ModuleManifest,
  type ProviderPicker,
} from "./catalog.ts";
import { generateSpringBootProject, initializrDependenciesFor, type FetchLike } from "./initializr.ts";
import { applyOperations, compose, substitute, type ModuleSelection } from "./merge.ts";
import { appliedIds, loadState, saveState, withApplied, withBasePackage, withProjectName } from "./state.ts";
import type { ProjectState } from "./state.ts";
export const DEFAULT_BASE_PACKAGE = "com.example.app";
function isBinary(relativePath: string): boolean {
  return relativePath.endsWith(".jar") || relativePath.endsWith(".png") || relativePath.endsWith(".ico");
}
function isExecutableScript(relativePath: string): boolean {
  if (relativePath.endsWith(".bat") || relativePath.endsWith(".cmd")) return false;
  const fileName = path.basename(relativePath);
  return fileName === "gradlew" || fileName.endsWith(".sh") || fileName.endsWith(".command");
}
function copyModuleFiles(manifest: ModuleManifest, targetRoot: string, tokens: Record<string, string>): void {
  for (const relativePath of manifest.fileTemplates) {
    const destinationRelativePath = substitute(relativePath, tokens);
    const destination = path.join(targetRoot, destinationRelativePath);
    const sourceRelative = `files/${relativePath}`;
    if (resolvePath(manifest.id, sourceRelative) === undefined) {
      throw new Error(`Module '${manifest.id}' declares fileTemplate '${relativePath}' but no such resource exists under files/`);
    }
    mkdirSync(path.dirname(destination), { recursive: true });
    if (isBinary(relativePath)) {
      writeFileSync(destination, readBytes(manifest.id, sourceRelative)!);
    } else {
      writeFileSync(destination, substitute(readText(manifest.id, sourceRelative)!, tokens), "utf8");
    }
    if (isExecutableScript(relativePath)) {
      chmodSync(destination, 0o755);
    }
  }
}
export type ApplyResult =
  | { kind: "applied"; output: string; warnings?: string[]; autoAdded?: string[] }
  | { kind: "dryRun"; diff: string; autoAdded?: string[] }
  | { kind: "rejected"; problems: string[] }
  | { kind: "failed"; output: string };
function nonEmpty(arr: string[] | undefined): string[] | undefined {
  return arr !== undefined && arr.length > 0 ? arr : undefined;
}
function deriveGroupId(basePackage: string): string {
  const segments = basePackage.split(".");
  return segments.length > 1 ? segments.slice(0, -1).join(".") : basePackage;
}
function projectLevelTokens(state: ProjectState): Record<string, string> {
  if (state.basePackage === undefined && state.projectName === undefined) return {};
  const tokens: Record<string, string> = {};
  if (state.projectName !== undefined) tokens.project_name = state.projectName;
  if (state.basePackage !== undefined) {
    tokens.package = state.basePackage;
    tokens.package_path = state.basePackage.split(".").join("/");
  }
  return tokens;
}
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
function seedStateForFreshApply(
  state: ProjectState,
  basePackage: string | undefined,
  projectName: string | undefined,
): ProjectState {
  if (appliedIds(state).length > 0) return state;
  if (basePackage === undefined && projectName === undefined) return state;
  return withBasePackage(
    withProjectName(state, projectName !== undefined ? projectName : state.projectName),
    basePackage !== undefined ? basePackage : state.basePackage,
  );
}
interface FinishOpts {
  targetProject: string;
  state: ProjectState;
  moduleIds: string[];
  answersByModule: Map<string, Record<string, string>>;
  dryRun: boolean;
  out: string;
  basePackage: string | undefined;
  projectName: string | undefined;
  warnings?: string[];
  autoAdded?: string[];
}
function finish(opts: FinishOpts): ApplyResult {
  if (opts.dryRun) return { kind: "dryRun", diff: opts.out, autoAdded: nonEmpty(opts.autoAdded) };
  let seeded = opts.state;
  if (appliedIds(opts.state).length === 0) {
    const name = opts.projectName !== undefined ? opts.projectName : path.basename(opts.targetProject);
    seeded = withProjectName(seeded, name);
    if (opts.basePackage !== undefined) seeded = withBasePackage(seeded, opts.basePackage);
  }
  let updated = seeded;
  for (const id of opts.moduleIds) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) throw new Error(`Unknown module: '${id}'`);
    updated = withApplied(updated, {
      id,
      version: manifest.version,
      appliedAt: new Date().toISOString(),
      answers: opts.answersByModule.get(id) ?? {},
    });
  }
  saveState(opts.targetProject, updated);
  return {
    kind: "applied",
    output: opts.out,
    warnings: nonEmpty(opts.warnings),
    autoAdded: nonEmpty(opts.autoAdded),
  };
}
export interface ApplyOpts {
  targetProject: string;
  moduleIds: string[];
  overrides: Record<string, string>;
  dryRun: boolean;
  basePackage: string | undefined;
  projectName: string | undefined;
  fetchImpl?: FetchLike;
  pickProvider?: ProviderPicker;
  preResolved?: Map<string, string>;
}
export async function apply(opts: ApplyOpts): Promise<ApplyResult> {
  const fetchImpl = opts.fetchImpl ?? fetch;
  const state = loadState(opts.targetProject);

  const pickProvider = opts.pickProvider ?? (async () => undefined);
  const resolution = await resolveModules(catalog, appliedIds(state), opts.moduleIds, pickProvider, opts.preResolved);
  if (resolution.problems.length > 0) return { kind: "rejected", problems: resolution.problems };
  const moduleIds = resolution.moduleIds;
  const autoAdded = resolution.added;

  const problems = validate(catalog, appliedIds(state), moduleIds);
  if (problems.length > 0) return { kind: "rejected", problems };
  const tokenState = seedStateForFreshApply(state, opts.basePackage, opts.projectName);
  const projectTokens = projectLevelTokens(tokenState);
  const selections: ModuleSelection[] = [];
  const answersByModule = new Map<string, Record<string, string>>();
  for (const id of moduleIds) {
    const manifest = catalog.modules.get(id);
    if (manifest === undefined) {
      return { kind: "rejected", problems: [`Unknown module: '${id}'`] };
    }
    const answers = resolveDefaultsOnly(manifest, opts.overrides);
    answersByModule.set(id, answers);
    if (manifest.mergeRecipes.length > 0) {
      selections.push({ manifest, answers });
    }
  }
  let initializrWarnings: string[] = [];
  if (!opts.dryRun) {
    for (const id of moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (manifest.scaffold) {
        if (manifest.id === "base") {
          const resolvedBasePackage = tokenState.basePackage ?? DEFAULT_BASE_PACKAGE;
          const resolvedProjectName = tokenState.projectName ?? path.basename(opts.targetProject);
          try {
            const result = await generateSpringBootProject(
              opts.targetProject,
              {
                groupId: deriveGroupId(resolvedBasePackage),
                artifactId: resolvedProjectName,
                packageName: resolvedBasePackage,
                name: resolvedProjectName,
                dependencies: initializrDependenciesFor(moduleIds),
              },
              fetchImpl,
            );
            initializrWarnings = result.warnings;
          } catch (err) {
            return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
          }
        }
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        try {
          copyModuleFiles(manifest, opts.targetProject, fileTokens);
        } catch (err) {
          return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
        }
      }
    }
  }
  let recipeOutput = "no merges required";
  if (selections.length > 0) {
    const operations = compose(selections);
    if (opts.dryRun) {
      return finish({
        targetProject: opts.targetProject,
        state,
        moduleIds,
        answersByModule,
        dryRun: opts.dryRun,
        out: "Dry run — no changes written.",
        basePackage: opts.basePackage,
        projectName: opts.projectName,
        warnings: initializrWarnings,
        autoAdded,
      });
    }
    try {
      applyOperations(opts.targetProject, operations);
    } catch (err) {
      return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
    }
    recipeOutput = `Applied ${operations.length} merge operation(s).`;
  }
  if (!opts.dryRun) {
    for (const id of moduleIds) {
      const manifest = catalog.modules.get(id);
      if (manifest === undefined) continue;
      if (!manifest.scaffold && manifest.fileTemplates.length > 0) {
        const fileTokens = { ...projectTokens, ...answersByModule.get(id) };
        try {
          copyModuleFiles(manifest, opts.targetProject, fileTokens);
        } catch (err) {
          return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
        }
      }
    }
  }
  return finish({
    targetProject: opts.targetProject,
    state,
    moduleIds,
    answersByModule,
    dryRun: opts.dryRun,
    out: recipeOutput,
    basePackage: opts.basePackage,
    projectName: opts.projectName,
    warnings: initializrWarnings,
    autoAdded,
  });
}
