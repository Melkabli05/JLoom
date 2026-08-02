import path from "node:path";
import type { ReplIo } from "../lineSource.ts";
import * as output from "../output.ts";
import * as prompts from "../prompts.ts";
import { apply } from "../orchestrate/moduleApplier.ts";
import type { JloomContext } from "../context.ts";

async function resolveModuleIds(io: ReplIo, moduleIds: string[]): Promise<string[]> {
  if (moduleIds.length > 0) {
    return moduleIds;
  }
  const typed = await prompts.requireNonBlankText(
    io,
    undefined,
    "modules",
    "Which modules? (comma or space separated ids, see 'jloom list')",
  );
  return typed
    .split(/[,\s]+/)
    .map((s) => s.trim())
    .filter((s) => s !== "");
}

export interface AddOptions {
  project: string;
  moduleIds: string[];
  set: Record<string, string>;
  dryRun: boolean;
}

export async function runAdd(ctx: JloomContext, io: ReplIo, options: AddOptions): Promise<void> {
  const ids = await resolveModuleIds(io, options.moduleIds);
  const result = await apply(ctx.modules, path.resolve(options.project), ids, options.set, options.dryRun, undefined, undefined);

  switch (result.kind) {
    case "applied":
      console.log(output.success(`Applied: [${ids.join(", ")}]`));
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
