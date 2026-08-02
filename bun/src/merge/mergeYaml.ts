import yaml from "js-yaml";
import type { MergeYamlOp } from "./types.ts";

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

/** "$" -> [], "$.spring.jpa" -> ["spring", "jpa"] — the JsonPath-lite subset this catalog uses. */
function pathSegments(key: string): string[] {
  if (key === "$") {
    return [];
  }
  if (!key.startsWith("$.")) {
    throw new Error(`Unsupported MergeYaml key (expected '$' or '$.a.b'): ${key}`);
  }
  return key.slice(2).split(".");
}

function deepMerge(target: unknown, source: unknown): unknown {
  if (isPlainObject(target) && isPlainObject(source)) {
    const result: Record<string, unknown> = { ...target };
    for (const [key, value] of Object.entries(source)) {
      result[key] = key in result ? deepMerge(result[key], value) : value;
    }
    return result;
  }
  // Scalars/arrays: the fragment's value wins, matching MergeYaml's overwrite-on-conflict.
  return source;
}

function mergeAtPath(target: Record<string, unknown>, segments: string[], fragment: unknown): Record<string, unknown> {
  if (segments.length === 0) {
    return deepMerge(target, fragment) as Record<string, unknown>;
  }
  const [head, ...rest] = segments as [string, ...string[]];
  const existing = target[head];
  const child = isPlainObject(existing) ? existing : {};
  return { ...target, [head]: mergeAtPath(child, rest, fragment) };
}

export function mergeYamlContent(targetContent: string, op: MergeYamlOp): string {
  const loaded = yaml.load(targetContent);
  const targetDoc = isPlainObject(loaded) ? loaded : {};
  const fragmentDoc = yaml.load(op.yaml);
  const merged = mergeAtPath(targetDoc, pathSegments(op.key), fragmentDoc);
  return yaml.dump(merged, { lineWidth: -1 });
}
