import path from "node:path";
import * as clack from "@clack/prompts";
import { catalog } from "../catalog.ts";
import { apply } from "../apply.ts";
import { interactivePickProvider } from "../capabilities.ts";
import { appliedIds, loadState } from "../state.ts";
import { askConfirm, askMultiple, isInteractive, output } from "../wizard.ts";
export interface AddOpts {
  project: string;
  moduleIds: string[];
  set: Record<string, string>;
  dryRun: boolean;
  yes?: boolean;
}
export async function runAdd(opts: AddOpts): Promise<void> {
  const targetProject = path.resolve(opts.project);
  let ids = opts.moduleIds;
  if (ids.length === 0) {
    if (!isInteractive()) {
      throw new Error("Pass one or more module ids, e.g. 'jloom add postgres flyway' (no interactive terminal to prompt on).");
    }
    const applied = new Set(appliedIds(loadState(targetProject)));
    const choices = [...catalog.modules.values()]
      .filter((m) => !applied.has(m.id))
      .sort((a, b) => a.id.localeCompare(b.id))
      .map((m) => ({
        value: m.id,
        label: m.id,
        hint: m.provides !== undefined ? `provides=${m.provides}` : m.requires.length > 0 ? `requires=${m.requires.join(", ")}` : undefined,
      }));
    ids = await askMultiple("Which modules? (space to toggle, enter to confirm)", choices);
    if (ids.length === 0) {
      console.log(output.hint("No modules selected — nothing to do."));
      return;
    }
  }
  const showChrome = isInteractive() && !opts.dryRun && !opts.yes;
  if (showChrome) {
    clack.note(`Project: ${targetProject}\nAdding:  ${ids.join(", ")}`, "Summary");
    if (!(await askConfirm("Proceed?", true))) {
      clack.outro("Aborted — no changes were made.");
      return;
    }
  }
  const spin = isInteractive() ? clack.spinner() : undefined;
  spin?.start("Applying modules...");
  const result = await apply({
    targetProject,
    moduleIds: ids,
    overrides: opts.set,
    dryRun: opts.dryRun,
    basePackage: undefined,
    projectName: undefined,
    pickProvider: interactivePickProvider,
  });
  spin?.stop(result.kind === "applied" ? "Applied." : "Done.");
  switch (result.kind) {
    case "applied":
      if (result.autoAdded !== undefined) {
        console.log(output.hint(`Also added (to satisfy a dependency): ${result.autoAdded.join(", ")}`));
      }
      if (result.warnings !== undefined) {
        for (const warning of result.warnings) {
          if (isInteractive()) {
            clack.log.warn(warning);
          } else {
            console.log(output.err(warning));
          }
        }
      }
      console.log(output.ok(`Applied: [${ids.join(", ")}]`));
      break;
    case "dryRun":
      console.log(output.hint("Dry run — no changes written."));
      break;
    case "rejected":
      throw new Error(result.problems.join("\n  - "));
    case "failed":
      throw new Error(`Merge run failed:\n${result.output}`);
  }
}
