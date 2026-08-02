import { applyOperations, composeUpgrade, type UpgradeStep } from "../merge.ts";
import { ModuleRegistry } from "../registry/moduleRegistry.ts";
import type { Upgrade } from "../registry/types.ts";
import { loadState, saveState, withApplied } from "../state/projectStateStore.ts";
import type { AppliedModule } from "../state/projectStateStore.ts";

export type UpgradeResult =
  | { kind: "upToDate" }
  | { kind: "upgraded"; changes: string[] }
  | { kind: "dryRun"; changes: string[] }
  | { kind: "blocked"; reasons: string[] }
  | { kind: "failed"; output: string };

interface PlannedUpgrade {
  applied: AppliedModule;
  newVersion: string;
  path: Upgrade[];
}

/** Mirrors UpgradeEngine.upgrade(...): any one module with no bridging upgrade path aborts the
 * whole run as Blocked before anything is executed. Reuses each module's already-recorded
 * answers (no fresh prompts/--set on upgrade) and never re-copies fileTemplates - only the
 * upgrade recipes' merge operations run. */
export function upgrade(
  registry: ModuleRegistry,
  targetProject: string,
  onlyModuleId: string | undefined,
  dryRun: boolean,
): UpgradeResult {
  const state = loadState(targetProject);
  const candidates =
    onlyModuleId === undefined ? state.modules : state.modules.filter((m) => m.id === onlyModuleId);
  if (onlyModuleId !== undefined && candidates.length === 0) {
    throw new Error(`Module '${onlyModuleId}' is not applied to this project. Run 'jloom status' to see what is.`);
  }

  const planned: PlannedUpgrade[] = [];
  const blocked: string[] = [];

  for (const applied of candidates) {
    const current = registry.find(applied.id);
    if (current === undefined || current.version === applied.version) {
      continue;
    }
    const path = registry.findUpgradePath(applied.id, applied.version);
    if (path.length === 0) {
      blocked.push(`${applied.id} is at ${applied.version}, catalog has ${current.version}, but no upgrade recipe bridges them`);
      continue;
    }
    planned.push({ applied, newVersion: current.version, path });
  }

  if (blocked.length > 0) {
    return { kind: "blocked", reasons: blocked };
  }
  if (planned.length === 0) {
    return { kind: "upToDate" };
  }

  const steps: UpgradeStep[] = [];
  for (const p of planned) {
    for (const step of p.path) {
      steps.push({ moduleId: p.applied.id, recipeResourcePath: step.recipe, answers: p.applied.answers });
    }
  }

  const operations = composeUpgrade(steps);
  const changes = planned.map((p) => `${p.applied.id}: ${p.applied.version} -> ${p.newVersion}`);

  if (dryRun) {
    return { kind: "dryRun", changes };
  }

  try {
    applyOperations(targetProject, operations);
  } catch (err) {
    return { kind: "failed", output: err instanceof Error ? err.message : String(err) };
  }

  let updated = state;
  for (const p of planned) {
    updated = withApplied(updated, {
      id: p.applied.id,
      version: p.newVersion,
      appliedAt: new Date().toISOString(),
      answers: p.applied.answers,
    });
  }
  saveState(targetProject, updated);
  return { kind: "upgraded", changes };
}
