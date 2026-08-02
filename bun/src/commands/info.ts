import * as output from "../output.ts";
import * as prompts from "../prompts.ts";
import type { ReplIo } from "../lineSource.ts";
import { catalog } from "../catalog.ts";

export async function runInfo(io: ReplIo, moduleId: string | undefined): Promise<void> {
  const id = await prompts.requireNonBlankText(io, moduleId, "module", "Which module? (see 'jloom list' for ids)");
  const mod = catalog.modules.get(id);
  if (mod === undefined) {
    throw new Error(`No such module: '${id}'. Run 'jloom list' to see available modules.`);
  }

  const lines: string[] = [`${output.accent(mod.id)} ${mod.version}`];

  if (mod.requires.length > 0) {
    lines.push(`  requires: [${mod.requires.join(", ")}]`);
  }
  if (mod.fileTemplates.length > 0) {
    lines.push(output.heading("  adds new files:"));
    for (const t of mod.fileTemplates) {
      lines.push(`    + ${t}`);
    }
  }
  if (mod.mergeRecipes.length > 0) {
    lines.push(output.heading("  edits existing files via merge recipes:"));
    for (const t of mod.mergeRecipes) {
      lines.push(`    ~ ${t}`);
    }
  }

  console.log(lines.join("\n"));
}
