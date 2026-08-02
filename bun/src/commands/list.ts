import path from "node:path";
import { catalog, capabilityProviders } from "../catalog.ts";
import { loadState } from "../state.ts";
import { askNonBlankText, output } from "../wizard.ts";
import { javaList, pad, widthOf } from "../format.ts";
import { capabilityChoices } from "../capabilities.ts";
export function listModules(): void {
  const sorted = [...catalog.modules.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (m) => m.id);
  const versionWidth = widthOf(sorted, (m) => m.version);
  const body = sorted
    .map((m) => {
      const provides = m.provides === undefined ? "" : `  provides=${m.provides}`;
      const requires = m.requires.length === 0 ? "" : `  requires=${javaList(m.requires)}`;
      const paddedId = output.accent(pad(m.id, idWidth));
      return `  ${paddedId}  ${pad(m.version, versionWidth)}${provides}${requires}`;
    })
    .join("\n");
  console.log(`${output.question("Available modules:")}\n${body}`);
}
export function listServices(): void {
  const sorted = [...catalog.services.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (s) => s.id);
  const nameWidth = widthOf(sorted, (s) => s.displayName);
  const body = sorted
    .map((s) => {
      const paddedId = output.accent(pad(s.id, idWidth));
      return `  ${paddedId}  ${pad(s.displayName, nameWidth)}  modules=${javaList(s.modules)}`;
    })
    .join("\n");
  console.log(`${output.question("Available services:")}\n${body}`);
}
export function listArchetypes(): void {
  const sorted = [...catalog.archetypes.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (a) => a.id);
  const body = sorted
    .map((a) => {
      const paddedId = output.accent(pad(a.id, idWidth));
      return `  ${paddedId}  modules=${javaList(a.modules)}`;
    })
    .join("\n");
  console.log(`${output.question("Available archetypes:")}\n${body}`);
}
export function listCapabilities(): void {
  const rows = capabilityChoices().map((c) => {
    const requirement = `capability:${c.value}`;
    const providers = c.value === "migrations" ? ["flyway", "flyway-mysql"] : capabilityProviders(catalog, requirement).map((m) => m.id);
    return { id: c.value, label: c.label, providers };
  });
  const idWidth = widthOf(rows, (r) => r.id);
  const body = rows
    .map((r) => {
      const paddedId = output.accent(pad(r.id, idWidth));
      return `  ${paddedId}  ${r.label}  provider(s)=${javaList(r.providers)}`;
    })
    .join("\n");
  console.log(`${output.question("Available capabilities (for 'jloom new --capabilities'):")}\n${body}`);
}
export function runStatus(project: string): void {
  const projectPath = path.resolve(project);
  const state = loadState(projectPath);
  if (state.modules.length === 0) {
    console.log(`No applied modules in ${projectPath}.`);
    return;
  }
  const lines: string[] = [output.question(`Applied modules in ${projectPath}:`)];
  for (const applied of state.modules) {
    const latest = catalog.modules.get(applied.id);
    const note = latest !== undefined && latest.version !== applied.version
      ? `catalog has ${latest.version} — run 'jloom upgrade' to pick it up`
      : output.hint("up to date");
    const paddedId = output.accent(pad(applied.id, 25));
    lines.push(`  ${paddedId} ${pad(applied.version, 10)} ${note}`);
  }
  console.log(lines.join("\n"));
}
export async function runInfo(moduleId: string | undefined): Promise<void> {
  const id = await askNonBlankText(moduleId, "module", "Which module? (see 'jloom list' for ids)");
  const mod = catalog.modules.get(id);
  if (mod === undefined) {
    throw new Error(`No such module: '${id}'. Run 'jloom list' to see available modules.`);
  }
  const lines: string[] = [`${output.accent(mod.id)} ${mod.version}`];
  if (mod.requires.length > 0) lines.push(`  requires: [${mod.requires.join(", ")}]`);
  if (mod.fileTemplates.length > 0) {
    lines.push(output.question("  adds new files:"));
    for (const t of mod.fileTemplates) lines.push(`    + ${t}`);
  }
  if (mod.mergeRecipes.length > 0) {
    lines.push(output.question("  edits existing files via merge recipes:"));
    for (const t of mod.mergeRecipes) lines.push(`    ~ ${t}`);
  }
  console.log(lines.join("\n"));
}
export function runConfig(): void {
  console.log(
    [
      output.question("jloom config:"),
      `  color:       ${output.isColorOn ? "ON" : "OFF"} (tty=${process.stdout.isTTY === true})`,
      "  state dir:   <project>/.jloom (per-project; not configurable)",
      "",
    ].join("\n"),
  );
}
