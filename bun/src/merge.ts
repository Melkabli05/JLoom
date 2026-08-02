import yaml from "js-yaml";
import { existsSync, globSync, readFileSync, writeFileSync } from "node:fs";
import { mkdirSync } from "node:fs";
import path from "node:path";
import { readText } from "./registry/catalogRoots.ts";
import type { ModuleManifest } from "./registry/types.ts";

// ===== Operations (the entire "what we do to files" surface) =====

export type MergeOp =
  | { kind: "AddDependency"; groupId: string; artifactId: string; version?: string; configuration: string }
  | { kind: "MergeYaml"; key: string; yaml: string; filePattern: string }
  | { kind: "ChangePropertyKey"; oldPropertyKey: string; newPropertyKey: string; filePattern: string }
  | { kind: "CreateTextFile"; relativeFileName: string; fileContents: string; overwriteExisting: boolean };

// ===== Token substitution (the {{key}} token -> value primitive) =====

export function substitute(text: string, tokens: Record<string, string>): string {
  let out = text;
  for (const [k, v] of Object.entries(tokens)) out = out.split(`{{${k}}}`).join(v);
  return out;
}

// ===== Compose: read fragment YAML, substitute tokens, parse to MergeOp[] =====

export interface ModuleSelection {
  manifest: ModuleManifest;
  answers: Record<string, string>;
}

export interface UpgradeStep {
  moduleId: string;
  recipeResourcePath: string;
  answers: Record<string, string>;
}

export function compose(modules: ModuleSelection[]): MergeOp[] {
  return composeFragments(
    modules.map((s) => ({
      moduleId: s.manifest.id,
      resourcePaths: s.manifest.mergeRecipes,
      answers: s.answers,
    })),
  );
}

export function composeUpgrade(steps: UpgradeStep[]): MergeOp[] {
  return composeFragments(
    steps.map((s) => ({
      moduleId: s.moduleId,
      resourcePaths: [s.recipeResourcePath],
      answers: s.answers,
    })),
  );
}

function composeFragments(fragments: { moduleId: string; resourcePaths: string[]; answers: Record<string, string> }[]): MergeOp[] {
  const operations: MergeOp[] = [];
  for (const fragment of fragments) {
    for (const resourcePath of fragment.resourcePaths) {
      const text = readFragment(fragment.moduleId, resourcePath);
      const substituted = substitute(text, fragment.answers);
      const parsed = yaml.load(substituted);
      if (!Array.isArray(parsed)) {
        throw new Error(`Fragment ${fragment.moduleId}/${resourcePath} is not a YAML list (got ${typeof parsed})`);
      }
      for (const entry of parsed) {
        operations.push(parseOperation(fragment.moduleId, resourcePath, entry));
      }
    }
  }
  return operations;
}

function readFragment(moduleId: string, resourcePath: string): string {
  const text = readText(moduleId, resourcePath);
  if (text === undefined) {
    throw new Error(`Module '${moduleId}' declares recipe '${resourcePath}' but no such resource exists`);
  }
  return text;
}

function parseOperation(moduleId: string, resourcePath: string, entry: unknown): MergeOp {
  if (entry === null || typeof entry !== "object") {
    throw new Error(`Fragment ${moduleId}/${resourcePath} contains a non-map entry: ${String(entry)}`);
  }
  const keys = Object.keys(entry as Record<string, unknown>);
  if (keys.length !== 1) {
    throw new Error(`Fragment ${moduleId}/${resourcePath} contains a malformed recipe entry: ${JSON.stringify(entry)}`);
  }
  const recipeType = keys[0]!;
  const params = (entry as Record<string, unknown>)[recipeType] as Record<string, unknown>;

  switch (recipeType) {
    case "org.openrewrite.gradle.AddDependency":
      return {
        kind: "AddDependency",
        groupId: String(params.groupId),
        artifactId: String(params.artifactId),
        version: params.version === undefined ? undefined : String(params.version),
        configuration: String(params.configuration),
      };
    case "org.openrewrite.yaml.MergeYaml":
      return {
        kind: "MergeYaml",
        key: String(params.key),
        yaml: String(params.yaml),
        filePattern: String(params.filePattern),
      };
    case "org.openrewrite.yaml.ChangePropertyKey":
      return {
        kind: "ChangePropertyKey",
        oldPropertyKey: String(params.oldPropertyKey),
        newPropertyKey: String(params.newPropertyKey),
        filePattern: String(params.filePattern),
      };
    case "org.openrewrite.text.CreateTextFile":
      return {
        kind: "CreateTextFile",
        relativeFileName: String(params.relativeFileName),
        fileContents: String(params.fileContents),
        overwriteExisting: params.overwriteExisting === true,
      };
    default:
      throw new Error(`Fragment ${moduleId}/${resourcePath} uses unsupported recipe type: ${recipeType}`);
  }
}

// ===== applyOps: single dispatch over MergeOp[] =====

export function applyOperations(root: string, ops: MergeOp[]): void {
  for (const op of ops) {
    switch (op.kind) {
      case "AddDependency":
        applyAddDep(root, op);
        break;
      case "MergeYaml":
        applyMergeYaml(root, op);
        break;
      case "ChangePropertyKey":
        applyChangeKey(root, op);
        break;
      case "CreateTextFile":
        applyCreateFile(root, op);
        break;
    }
  }
}

// ===== 4 apply helpers + JsonPath-lite + token substitution =====

const DEPENDENCIES_BLOCK = /dependencies\s*\{/;

function applyAddDep(root: string, op: { groupId: string; artifactId: string; version?: string; configuration: string }): void {
  const buildFile = findBuildFile(root);
  const content = readFileSync(buildFile, "utf8");
  const coordinate = `${op.groupId}:${op.artifactId}`;
  const configuredCoordinate = `${op.configuration}("${coordinate}`;
  if (content.includes(configuredCoordinate)) {
    return;
  }
  const match = content.match(DEPENDENCIES_BLOCK);
  if (match === null || match.index === undefined) {
    throw new Error("Could not find a 'dependencies {' block to insert into");
  }
  const insertAt = match.index + match[0].length;
  const versionSuffix = op.version === undefined ? "" : `:${op.version}`;
  const line = `\n    ${op.configuration}("${coordinate}${versionSuffix}")`;
  writeFileSync(buildFile, content.slice(0, insertAt) + line + content.slice(insertAt), "utf8");
}

function findBuildFile(root: string): string {
  for (const candidate of ["build.gradle.kts", "build.gradle"]) {
    const full = path.join(root, candidate);
    if (existsSync(full)) {
      return full;
    }
  }
  throw new Error(`No build.gradle.kts or build.gradle found under ${root}`);
}

function applyMergeYaml(root: string, op: { key: string; yaml: string; filePattern: string }): void {
  for (const file of filesMatching(root, op.filePattern)) {
    const content = existsSync(file) ? readFileSync(file, "utf8") : "";
    writeFileSync(file, mergeYamlContent(content, op), "utf8");
  }
}

function filesMatching(root: string, filePattern: string): string[] {
  return globSync(filePattern, { cwd: root }).map((rel) => path.join(root, rel));
}

function isPlainObject(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === "object" && !Array.isArray(value);
}

function pathSegments(key: string): string[] {
  if (key === "$") return [];
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
  return source;
}

function mergeAtPath(target: Record<string, unknown>, segments: string[], fragment: unknown): Record<string, unknown> {
  if (segments.length === 0) return deepMerge(target, fragment) as Record<string, unknown>;
  const [head, ...rest] = segments as [string, ...string[]];
  const existing = target[head];
  const child = isPlainObject(existing) ? existing : {};
  return { ...target, [head]: mergeAtPath(child, rest, fragment) };
}

function mergeYamlContent(targetContent: string, op: { key: string; yaml: string }): string {
  const loaded = yaml.load(targetContent);
  const targetDoc = isPlainObject(loaded) ? loaded : {};
  const fragmentDoc = yaml.load(op.yaml);
  const merged = mergeAtPath(targetDoc, pathSegments(op.key), fragmentDoc);
  return yaml.dump(merged, { lineWidth: -1 });
}

function applyChangeKey(root: string, op: { oldPropertyKey: string; newPropertyKey: string; filePattern: string }): void {
  for (const file of filesMatching(root, op.filePattern)) {
    const content = readFileSync(file, "utf8");
    writeFileSync(file, changeKeyContent(content, op), "utf8");
  }
}

function changeKeyContent(targetContent: string, op: { oldPropertyKey: string; newPropertyKey: string }): string {
  const loaded = yaml.load(targetContent);
  const doc = isPlainObject(loaded) ? loaded : {};
  const oldSegments = op.oldPropertyKey.split(".");
  const newSegments = op.newPropertyKey.split(".");
  const value = getAtPath(doc, oldSegments);
  if (value === undefined) return targetContent;
  const withoutOld = deleteAtPath(doc, oldSegments);
  const withNew = setAtPath(withoutOld, newSegments, value);
  return yaml.dump(withNew, { lineWidth: -1 });
}

function getAtPath(node: unknown, segments: string[]): unknown {
  let cursor = node;
  for (const segment of segments) {
    if (!isPlainObject(cursor)) return undefined;
    cursor = cursor[segment];
  }
  return cursor;
}

function deleteAtPath(node: Record<string, unknown>, segments: string[]): Record<string, unknown> {
  const [head, ...rest] = segments as [string, ...string[]];
  if (!(head in node)) return node;
  if (rest.length === 0) {
    const remainder = { ...node };
    delete remainder[head];
    return remainder;
  }
  const child = node[head];
  if (!isPlainObject(child)) return node;
  return { ...node, [head]: deleteAtPath(child, rest) };
}

function setAtPath(node: Record<string, unknown>, segments: string[], value: unknown): Record<string, unknown> {
  const [head, ...rest] = segments as [string, ...string[]];
  if (rest.length === 0) return { ...node, [head]: value };
  const child = node[head];
  const childObj = isPlainObject(child) ? child : {};
  return { ...node, [head]: setAtPath(childObj, rest, value) };
}

function applyCreateFile(root: string, op: { relativeFileName: string; fileContents: string; overwriteExisting: boolean }): void {
  const target = path.join(root, op.relativeFileName);
  if (existsSync(target) && !op.overwriteExisting) return;
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, op.fileContents, "utf8");
}
