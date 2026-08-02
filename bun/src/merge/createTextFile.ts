import { existsSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import type { CreateTextFileOp } from "./types.ts";

export function createTextFile(projectRoot: string, op: CreateTextFileOp): void {
  const target = path.join(projectRoot, op.relativeFileName);
  if (existsSync(target) && !op.overwriteExisting) {
    return;
  }
  mkdirSync(path.dirname(target), { recursive: true });
  writeFileSync(target, op.fileContents, "utf8");
}
