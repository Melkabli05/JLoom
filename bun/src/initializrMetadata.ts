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













const WILDCARD = -1;
interface ParsedVersion {
  major: number;
  minor: number;
  patch: number;
  qualifierTier: number;
  qualifierNum: number;
}
const QUALIFIER_TIERS: Record<string, number> = { M: 0, RC: 1, SNAPSHOT: 2 };
function parseVersion(raw: string): ParsedVersion {


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
  return { lower: { version: parseVersion(trimmed), inclusive: true } };
}


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
