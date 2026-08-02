import type { FetchLike } from "./initializr.ts";

export interface InitializrMetadata {
  bootVersionIds: Set<string>;
  dependencyRanges: Map<string, string | undefined>;
}

export async function fetchInitializrMetadata(fetchImpl: FetchLike): Promise<InitializrMetadata> {
  const res = await fetchImpl("https://start.spring.io/metadata/client", {
    headers: { Accept: "application/vnd.initializr.v2.3+json" },
  });
  if (!res.ok) {
    throw new Error(`Spring Initializr metadata request failed (${res.status} ${res.statusText})`);
  }
  const data = (await res.json()) as {
    dependencies: { values: { values: { id: string; versionRange?: string }[] }[] };
    bootVersion: { values: { id: string }[] };
  };

  const dependencyRanges = new Map<string, string | undefined>();
  for (const group of data.dependencies.values) {
    for (const item of group.values) {
      dependencyRanges.set(item.id, item.versionRange);
    }
  }
  const bootVersionIds = new Set(data.bootVersion.values.map((v) => v.id));
  return { bootVersionIds, dependencyRanges };
}

// ===== Maven-style version range comparison =====
//
// Spring Initializr's own metadata server does not filter dependencies by bootVersion (verified
// directly: passing ?bootVersion=X to /metadata/client returns the same unfiltered list) - per
// its official docs, the client is responsible for comparing each dependency's `versionRange`
// against the selected bootVersion before requesting it. No established npm package exists for
// this Maven/Java-ecosystem-specific range format (confirmed via search), so this is a small,
// focused comparator scoped to exactly what jloom needs: comparing one known, always-unqualified
// pinned version (e.g. "4.1.0") against range strings in the modern SemVer-ish V2 format
// Initializr's v2.3 metadata returns - "[4.0.0,4.1.0-M1)", bare "1.2.0" (this-or-later), and
// ".x" wildcard patch segments.

const WILDCARD = -1;

interface ParsedVersion {
  major: number;
  minor: number;
  patch: number; // WILDCARD if the segment was "x"
  qualifierTier: number; // 0=M(ilestone), 1=RC, 2=SNAPSHOT, 3=RELEASE (no qualifier)
  qualifierNum: number; // numeric suffix on the qualifier, e.g. "M2" -> 2; 0 if none
}

const QUALIFIER_TIERS: Record<string, number> = { M: 0, RC: 1, SNAPSHOT: 2 };

function parseVersion(raw: string): ParsedVersion {
  // Accepts both the V2 dash-qualifier form ("4.1.0-M1") and the older V1 dot form
  // ("4.1.0.M1") defensively, even though jloom only ever requests v2.3 metadata (dash form).
  const match = raw.trim().match(/^(\d+)\.(\d+)\.(x|\d+)(?:[-.]([A-Za-z]+)(\d*))?$/);
  if (match === null) {
    throw new Error(`Cannot parse version: '${raw}'`);
  }
  const [, majorStr, minorStr, patchStr, qualifierRaw, qualifierNumStr] = match as unknown as [
    string,
    string,
    string,
    string,
    string | undefined,
    string | undefined,
  ];
  const qualifier = (qualifierRaw ?? "RELEASE").toUpperCase();
  return {
    major: Number(majorStr),
    minor: Number(minorStr),
    patch: patchStr === "x" ? WILDCARD : Number(patchStr),
    qualifierTier: qualifier === "RELEASE" ? 3 : (QUALIFIER_TIERS[qualifier] ?? 3),
    qualifierNum: qualifierNumStr ? Number(qualifierNumStr) : 0,
  };
}

function compareVersions(a: ParsedVersion, b: ParsedVersion): number {
  if (a.major !== b.major) return a.major - b.major;
  if (a.minor !== b.minor) return a.minor - b.minor;
  if (a.patch === WILDCARD || b.patch === WILDCARD) {
    // A ".x" patch wildcard matches any concrete patch/qualifier within the same major.minor
    // line - a best-effort approximation; jloom has not observed this form in current
    // Boot-4-era metadata (it appears to be a legacy Boot 1.x/2.x-era pattern per the docs).
    return 0;
  }
  if (a.patch !== b.patch) return a.patch - b.patch;
  if (a.qualifierTier !== b.qualifierTier) return a.qualifierTier - b.qualifierTier;
  return a.qualifierNum - b.qualifierNum;
}

interface Bound {
  version: ParsedVersion;
  inclusive: boolean;
}

function parseRange(range: string): { lower?: Bound; upper?: Bound } {
  const trimmed = range.trim();
  const bracketMatch = trimmed.match(/^([[(])\s*([^,]*)\s*,\s*([^\])]*)\s*([\])])$/);
  if (bracketMatch !== null) {
    const [, openBracket, lowerStr, upperStr, closeBracket] = bracketMatch as unknown as [
      string,
      string,
      string,
      string,
      string,
    ];
    const lower = lowerStr.trim() === "" ? undefined : { version: parseVersion(lowerStr.trim()), inclusive: openBracket === "[" };
    const upper = upperStr.trim() === "" ? undefined : { version: parseVersion(upperStr.trim()), inclusive: closeBracket === "]" };
    return { lower, upper };
  }
  // Bare version (no brackets): "this version or later" - an inclusive lower bound, no upper.
  return { lower: { version: parseVersion(trimmed), inclusive: true } };
}

/** True if `pinnedVersion` (always a plain "X.Y.Z", no qualifier - jloom's own BOOT_VERSION)
 * satisfies `range`. `undefined`/blank range means the dependency has no restriction at all. */
export function isVersionCompatible(pinnedVersion: string, range: string | undefined): boolean {
  if (range === undefined || range.trim() === "") return true;
  const target = parseVersion(pinnedVersion);
  const { lower, upper } = parseRange(range);
  if (lower !== undefined) {
    const cmp = compareVersions(target, lower.version);
    if (lower.inclusive ? cmp < 0 : cmp <= 0) return false;
  }
  if (upper !== undefined) {
    const cmp = compareVersions(target, upper.version);
    if (upper.inclusive ? cmp > 0 : cmp >= 0) return false;
  }
  return true;
}
