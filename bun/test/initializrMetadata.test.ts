import { describe, expect, test } from "bun:test";
import { readFileSync } from "node:fs";
import path from "node:path";
import { fetchInitializrMetadata, isVersionCompatible } from "../src/initializrMetadata.ts";
import type { FetchLike } from "../src/initializr.ts";

const FIXTURE_PATH = path.join(import.meta.dir, "fixtures/initializr-metadata-sample.json");

function fakeMetadataFetch(): FetchLike {
  const body = readFileSync(FIXTURE_PATH, "utf-8");
  return async () => new Response(body, { status: 200, statusText: "OK" });
}

describe("fetchInitializrMetadata", () => {
  test("parses the recorded metadata fixture", async () => {
    const metadata = await fetchInitializrMetadata(fakeMetadataFetch());
    expect(metadata.bootVersionIds.has("4.1.0")).toBe(true);
    expect(metadata.dependencyRanges.get("springdoc-openapi")).toBe("[4.0.0,4.1.0-M1)");
    expect(metadata.dependencyRanges.get("web")).toBeUndefined();
  });

  test("throws a clear error on a non-ok response", async () => {
    const fetchImpl: FetchLike = async () => new Response("", { status: 500, statusText: "Internal Server Error" });
    await expect(fetchInitializrMetadata(fetchImpl)).rejects.toThrow(/500/);
  });
});

describe("isVersionCompatible", () => {
  test("undefined/blank range is always compatible", () => {
    expect(isVersionCompatible("4.1.0", undefined)).toBe(true);
    expect(isVersionCompatible("4.1.0", "")).toBe(true);
    expect(isVersionCompatible("4.1.0", "   ")).toBe(true);
  });

  test("bare version means 'this version or later'", () => {
    expect(isVersionCompatible("4.1.0", "4.1.0")).toBe(true);
    expect(isVersionCompatible("4.1.0", "4.0.0")).toBe(true);
    expect(isVersionCompatible("4.0.0", "4.1.0")).toBe(false);
  });

  test("the real springdoc-openapi range rejects Boot 4.1.0 and accepts 4.0.5", () => {
    const range = "[4.0.0,4.1.0-M1)";
    expect(isVersionCompatible("4.1.0", range)).toBe(false);
    expect(isVersionCompatible("4.0.5", range)).toBe(true);
    expect(isVersionCompatible("4.0.0", range)).toBe(true);
  });

  test("inclusive vs exclusive bound edges", () => {
    expect(isVersionCompatible("4.1.0", "[4.0.0,4.1.0]")).toBe(true);
    expect(isVersionCompatible("4.1.0", "[4.0.0,4.1.0)")).toBe(false);
    expect(isVersionCompatible("4.0.0", "(4.0.0,4.2.0]")).toBe(false);
    expect(isVersionCompatible("4.0.0", "[4.0.0,4.2.0]")).toBe(true);
  });

  test("qualifier precedence: M < RC < SNAPSHOT < RELEASE", () => {
    expect(isVersionCompatible("4.1.0", "[4.1.0-M1,4.1.0-RC1)")).toBe(false);
    expect(isVersionCompatible("4.1.0", "[4.1.0-M1,4.1.0]")).toBe(true);
  });

  test(".x wildcard patch matches any concrete patch within the same major.minor line", () => {
    expect(isVersionCompatible("4.1.0", "[4.1.x,4.2.x)")).toBe(true);
    expect(isVersionCompatible("4.1.5", "[4.1.x,4.1.x]")).toBe(true);
  });

  test("throws on an unparseable version string", () => {
    expect(() => isVersionCompatible("not-a-version", "4.0.0")).toThrow();
  });
});
