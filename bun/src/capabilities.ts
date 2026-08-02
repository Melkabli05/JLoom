import { catalog, capabilityProviders, type ModuleManifest } from "./catalog.ts";
import { askChoice, isInteractive } from "./wizard.ts";
// Every list below is derived live from the catalog's own requires/provides metadata instead of
// a hand-maintained literal — adding a module to the catalog makes it show up here (and in the
// wizard) with zero code changes. The trade-off: these are plain string[] rather than literal
// tuples, so --database/--capabilities/--cache-provider validate their choices at runtime
// (Commander's .choices() already does this) rather than via a TS literal union.
export const DATABASE_IDS: string[] = [
  ...capabilityProviders(catalog, "capability:relational-db").map((m) => m.id),
  "none",
];
export const CACHE_PROVIDER_IDS: string[] = capabilityProviders(catalog, "capability:caching").map((m) => m.id);
export const CAPABILITY_LABELS: Record<string, string> = {
  validation: "Validation",
  migrations: "Database migrations",
  auth: "Security (JWT)",
  caching: "Caching",
  aop: "AOP",
  scheduling: "Scheduling",
  async: "Async processing",
  auditing: "Auditing",
  tracing: "Observability",
  "api-docs": "OpenAPI",
  "integration-testing": "Testing infrastructure",
};
export function formatCapabilityLabel(value: string): string {
  return value
    .split("-")
    .map((word) => word.charAt(0).toUpperCase() + word.slice(1))
    .join(" ");
}
export function capabilityChoices(): { value: string; label: string }[] {
  const choices: { value: string; label: string }[] = [
    { value: "validation", label: CAPABILITY_LABELS.validation! },
    // "migrations" is synthetic: flyway/flyway-mysql provide nothing distinguishable — the
    // choice between them is purely "which SQL dialect", resolved by resolveMigrationsModule()
    // once the database is known (asking live if the user picks Migrations before a database).
    { value: "migrations", label: CAPABILITY_LABELS.migrations! },
  ];
  const seen = new Set(["validation"]);
  for (const m of catalog.modules.values()) {
    if (m.provides === undefined) continue;
    if (m.provides === "capability:http-api" || m.provides === "capability:relational-db") continue;
    const capability = m.provides.replace(/^capability:/, "");
    if (seen.has(capability)) continue;
    seen.add(capability);
    choices.push({ value: capability, label: CAPABILITY_LABELS[capability] ?? formatCapabilityLabel(capability) });
  }
  return choices;
}
export const CAPABILITY_IDS: string[] = capabilityChoices().map((c) => c.value);
export async function interactivePickProvider(capability: string, candidates: ModuleManifest[]): Promise<string | undefined> {
  if (!isInteractive()) return undefined;
  const label = CAPABILITY_LABELS[capability.replace(/^capability:/, "")] ?? formatCapabilityLabel(capability.replace(/^capability:/, ""));
  return askChoice(
    undefined,
    capability,
    `Which ${label.toLowerCase()}?`,
    candidates.map((m) => ({ value: m.id, label: m.description ?? formatCapabilityLabel(m.id) })),
    candidates[0]!.id,
  );
}
