import { describe, expect, test } from "bun:test";
import { mkdtempSync, readFileSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import path from "node:path";
import { generateSpringBootProject, type FetchLike, type InitializrOptions } from "../src/initializr.ts";

const METADATA_PATH = path.join(import.meta.dir, "fixtures/initializr-metadata-sample.json");
const TGZ_PATH = path.join(import.meta.dir, "fixtures/initializr-sample.tgz");

function fakeFetch(): FetchLike {
  const metadataBody = readFileSync(METADATA_PATH, "utf-8");
  const tgzBuf = readFileSync(TGZ_PATH);
  return async (url) => {
    if (url.includes("/metadata/client")) {
      return new Response(metadataBody, { status: 200, statusText: "OK" });
    }
    return new Response(tgzBuf, { status: 200, statusText: "OK" });
  };
}

const BASE_OPTS: InitializrOptions = {
  groupId: "com.example",
  artifactId: "my-app",
  packageName: "com.example.app",
  name: "my-app",
  dependencies: [],
};

describe("generateSpringBootProject", () => {
  test("drops an incompatible dependency and warns instead of failing", async () => {
    const targetDir = mkdtempSync(path.join(tmpdir(), "jloom-initializr-test-"));
    try {
      const result = await generateSpringBootProject(
        targetDir,
        { ...BASE_OPTS, dependencies: ["web", "springdoc-openapi"] },
        fakeFetch(),
      );
      expect(result.warnings).toHaveLength(1);
      expect(result.warnings[0]).toContain("springdoc-openapi");
    } finally {
      rmSync(targetDir, { recursive: true, force: true });
    }
  });

  test("passes compatible dependencies through with no warnings", async () => {
    const targetDir = mkdtempSync(path.join(tmpdir(), "jloom-initializr-test-"));
    try {
      const result = await generateSpringBootProject(targetDir, { ...BASE_OPTS, dependencies: ["web"] }, fakeFetch());
      expect(result.warnings).toEqual([]);
    } finally {
      rmSync(targetDir, { recursive: true, force: true });
    }
  });

  test("throws a clear error when the pinned boot version is no longer offered", async () => {
    const metadataBody = JSON.stringify({
      dependencies: { values: [] },
      bootVersion: { values: [{ id: "4.2.0" }] },
    });
    const fetchImpl: FetchLike = async () => new Response(metadataBody, { status: 200, statusText: "OK" });
    const targetDir = mkdtempSync(path.join(tmpdir(), "jloom-initializr-test-"));
    try {
      await expect(generateSpringBootProject(targetDir, BASE_OPTS, fetchImpl)).rejects.toThrow(/4\.1\.0/);
    } finally {
      rmSync(targetDir, { recursive: true, force: true });
    }
  });
});
