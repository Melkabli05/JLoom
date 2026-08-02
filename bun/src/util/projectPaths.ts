import { existsSync, readdirSync, statSync } from "node:fs";
import path from "node:path";

export function isEmpty(dir: string): boolean {
  if (!existsSync(dir) || !statSync(dir).isDirectory()) {
    return true;
  }
  try {
    return readdirSync(dir).length === 0;
  } catch {
    return false;
  }
}

export function requireEmpty(target: string): void {
  if (!isEmpty(target)) {
    throw new Error(
      `'${path.resolve(target)}' already exists and isn't empty — pass a different --name or remove it first.`,
    );
  }
}
