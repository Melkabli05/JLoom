import yaml from "js-yaml";
import type { ChangePropertyKeyOp } from "./types.ts";

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function getAtPath(node: unknown, segments: string[]): unknown {
  let cursor = node;
  for (const segment of segments) {
    if (!isPlainObject(cursor)) {
      return undefined;
    }
    cursor = cursor[segment];
  }
  return cursor;
}

function deleteAtPath(node: Record<string, unknown>, segments: string[]): Record<string, unknown> {
  const [head, ...rest] = segments as [string, ...string[]];
  if (!(head in node)) {
    return node;
  }
  if (rest.length === 0) {
    const remainder = { ...node };
    delete remainder[head];
    return remainder;
  }
  const child = node[head];
  if (!isPlainObject(child)) {
    return node;
  }
  return { ...node, [head]: deleteAtPath(child, rest) };
}

function setAtPath(node: Record<string, unknown>, segments: string[], value: unknown): Record<string, unknown> {
  const [head, ...rest] = segments as [string, ...string[]];
  if (rest.length === 0) {
    return { ...node, [head]: value };
  }
  const child = node[head];
  const childObj = isPlainObject(child) ? child : {};
  return { ...node, [head]: setAtPath(childObj, rest, value) };
}

/** Renames a key at a dotted path. No-op (leaves the file untouched) if oldPropertyKey isn't
 * present — matches renaming semantics: nothing to rename. Doesn't prune an ancestor that
 * becomes empty as a result (a minor, deliberately-accepted cosmetic difference — this op is
 * used exactly once in the whole catalog, for a config-key bugfix, not a general-purpose tool). */
export function changePropertyKeyContent(targetContent: string, op: ChangePropertyKeyOp): string {
  const loaded = yaml.load(targetContent);
  const doc = isPlainObject(loaded) ? loaded : {};

  const oldSegments = op.oldPropertyKey.split(".");
  const newSegments = op.newPropertyKey.split(".");

  const value = getAtPath(doc, oldSegments);
  if (value === undefined) {
    return targetContent;
  }

  const withoutOld = deleteAtPath(doc, oldSegments);
  const withNew = setAtPath(withoutOld, newSegments, value);
  return yaml.dump(withNew, { lineWidth: -1 });
}
