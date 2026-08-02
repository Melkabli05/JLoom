import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import path from "node:path";
export interface AppliedModule {
  id: string;
  version: string;
  appliedAt: string;
  answers: Record<string, string>;
}
export interface ProjectState {
  modules: AppliedModule[];
  basePackage?: string;
  projectName?: string;
}
const STATE_DIR = ".jloom";
const STATE_FILE = "state.json";
function statePath(projectRoot: string): string {
  return path.join(projectRoot, STATE_DIR, STATE_FILE);
}
export function emptyState(): ProjectState {
  return { modules: [] };
}
export function appliedIds(state: ProjectState): string[] {
  return state.modules.map((m) => m.id);
}
export function withApplied(state: ProjectState, module: AppliedModule): ProjectState {
  const modules = state.modules.filter((m) => m.id !== module.id);
  modules.push(module);
  return { ...state, modules };
}
export function withBasePackage(state: ProjectState, basePackage: string | undefined): ProjectState {
  return { ...state, basePackage };
}
export function withProjectName(state: ProjectState, projectName: string | undefined): ProjectState {
  return { ...state, projectName };
}



export function loadState(projectRoot: string): ProjectState {
  const file = statePath(projectRoot);
  if (!existsSync(file)) return emptyState();
  const raw = JSON.parse(readFileSync(file, "utf8")) as Partial<ProjectState>;
  return {
    modules: raw.modules ?? [],
    basePackage: raw.basePackage ?? undefined,
    projectName: raw.projectName ?? undefined,
  };
}
export function saveState(projectRoot: string, state: ProjectState): void {
  const file = statePath(projectRoot);
  mkdirSync(path.dirname(file), { recursive: true });
  writeFileSync(file, `${JSON.stringify(state, null, 2)}\n`, "utf8");
}
