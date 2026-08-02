import * as output from "../output.ts";
import { catalog, type ModuleManifest, type ServiceManifest, type ArchetypeManifest } from "../catalog.ts";

function javaList(items: string[]): string {
  return `[${items.join(", ")}]`;
}

function widthOf<T>(items: T[], field: (item: T) => string): number {
  return items.reduce((max, item) => Math.max(max, field(item).length), 0);
}

function pad(text: string, width: number): string {
  return text + " ".repeat(Math.max(0, width - text.length));
}

function formatModuleLine(m: ModuleManifest, idWidth: number, versionWidth: number): string {
  const provides = m.provides === undefined ? "" : `  provides=${m.provides}`;
  const requires = m.requires.length === 0 ? "" : `  requires=${javaList(m.requires)}`;
  const paddedId = output.accent(pad(m.id, idWidth));
  return `  ${paddedId}  ${pad(m.version, versionWidth)}${provides}${requires}`;
}

function formatServiceLine(s: ServiceManifest, idWidth: number, nameWidth: number): string {
  const paddedId = output.accent(pad(s.id, idWidth));
  return `  ${paddedId}  ${pad(s.displayName, nameWidth)}  modules=${javaList(s.modules)}`;
}

function formatArchetypeLine(a: ArchetypeManifest, idWidth: number): string {
  const paddedId = output.accent(pad(a.id, idWidth));
  return `  ${paddedId}  modules=${javaList(a.modules)}`;
}

export function listModules(): void {
  const sorted = [...catalog.modules.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (m) => m.id);
  const versionWidth = widthOf(sorted, (m) => m.version);
  const body = sorted.map((m) => formatModuleLine(m, idWidth, versionWidth)).join("\n");
  console.log(`${output.heading("Available modules:")}\n${body}`);
}

export function listServices(): void {
  const sorted = [...catalog.services.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (s) => s.id);
  const nameWidth = widthOf(sorted, (s) => s.displayName);
  const body = sorted.map((s) => formatServiceLine(s, idWidth, nameWidth)).join("\n");
  console.log(`${output.heading("Available services:")}\n${body}`);
}

export function listArchetypes(): void {
  const sorted = [...catalog.archetypes.values()].sort((a, b) => a.id.localeCompare(b.id));
  const idWidth = widthOf(sorted, (a) => a.id);
  const body = sorted.map((a) => formatArchetypeLine(a, idWidth)).join("\n");
  console.log(`${output.heading("Available archetypes:")}\n${body}`);
}

export function runList(what: string): void {
  switch (what.toLowerCase()) {
    case "archetypes":
      listArchetypes();
      break;
    case "services":
      listServices();
      break;
    default:
      listModules();
  }
}
