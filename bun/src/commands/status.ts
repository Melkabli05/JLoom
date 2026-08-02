import path from "node:path";
import * as output from "../output.ts";
import { loadState } from "../state/projectStateStore.ts";
import type { JloomContext } from "../context.ts";

function pad(text: string, width: number): string {
  return text + " ".repeat(Math.max(0, width - text.length));
}

export function runStatus(ctx: JloomContext, project: string): void {
  const projectPath = path.resolve(project);
  const state = loadState(projectPath);

  if (state.modules.length === 0) {
    console.log(`No applied modules in ${projectPath}.`);
    return;
  }

  const lines: string[] = [output.heading(`Applied modules in ${projectPath}:`)];
  for (const applied of state.modules) {
    const latest = ctx.modules.find(applied.id);
    const note =
      latest !== undefined && latest.version !== applied.version
        ? `catalog has ${latest.version} — run 'jloom upgrade' to pick it up`
        : output.hint("up to date");
    const paddedId = output.accent(pad(applied.id, 25));
    lines.push(`  ${paddedId} ${pad(applied.version, 10)} ${note}`);
  }
  console.log(lines.join("\n"));
}
