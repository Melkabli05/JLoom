import { chmodSync, mkdirSync, writeFileSync } from "node:fs";
import path from "node:path";
import { readBytes, readText, resolvePath } from "../registry/catalogRoots.ts";
import { substitute } from "../merge/tokens.ts";
import type { ModuleManifest } from "../registry/types.ts";

function isBinary(relativePath: string): boolean {
  return relativePath.endsWith(".jar") || relativePath.endsWith(".png") || relativePath.endsWith(".ico");
}

function isExecutableScript(relativePath: string): boolean {
  if (relativePath.endsWith(".bat") || relativePath.endsWith(".cmd")) {
    return false;
  }
  const fileName = path.basename(relativePath);
  return fileName === "gradlew" || fileName.endsWith(".sh") || fileName.endsWith(".command");
}

/** Mirrors FileTreeCopier.copy(...): copies a module's fileTemplates into the target project,
 * substituting {{tokens}} into both the destination path and (for text files) the content.
 * Binary files (.jar/.png/.ico) are copied byte-for-byte with no substitution. */
export function copy(manifest: ModuleManifest, targetRoot: string, tokens: Record<string, string>): void {
  for (const relativePath of manifest.fileTemplates) {
    const destinationRelativePath = substitute(relativePath, tokens);
    const destination = path.join(targetRoot, destinationRelativePath);
    const sourceRelative = `files/${relativePath}`;

    if (resolvePath(manifest.id, sourceRelative) === undefined) {
      throw new Error(
        `Module '${manifest.id}' declares fileTemplate '${relativePath}' but no such resource exists under files/`,
      );
    }

    mkdirSync(path.dirname(destination), { recursive: true });

    if (isBinary(relativePath)) {
      const bytes = readBytes(manifest.id, sourceRelative)!;
      writeFileSync(destination, bytes);
    } else {
      const content = readText(manifest.id, sourceRelative)!;
      writeFileSync(destination, substitute(content, tokens), "utf8");
    }

    if (isExecutableScript(relativePath)) {
      chmodSync(destination, 0o755);
    }
  }
}
