import path from "node:path";
import * as output from "../output.ts";
import { upgrade } from "../orchestrate/upgradeEngine.ts";
import type { JloomContext } from "../context.ts";

export function runUpgrade(ctx: JloomContext, project: string, moduleId: string | undefined, dryRun: boolean): void {
  const result = upgrade(ctx.modules, path.resolve(project), moduleId, dryRun);

  switch (result.kind) {
    case "upToDate":
      console.log(output.hint("Already up to date."));
      break;
    case "upgraded":
      console.log(output.success("Upgraded:"));
      for (const change of result.changes) {
        console.log(`  ${change}`);
      }
      break;
    case "dryRun":
      console.log(output.heading("Dry run — would upgrade:"));
      for (const change of result.changes) {
        console.log(`  ${change}`);
      }
      break;
    case "blocked":
      throw new Error(`Refusing to upgrade — no module was changed:\n  - ${result.reasons.join("\n  - ")}`);
    case "failed":
      throw new Error(`Merge run failed:\n${result.output}`);
  }
}
