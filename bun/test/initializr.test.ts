import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import path from "node:path";
import {
  buildInitializrUrl,
  generateSpringBootProject,
  initializrDependenciesFor,
} from "../src/initializr.ts";

test("initializrDependenciesFor always includes web and maps known capability modules", () => {
  const deps = initializrDependenciesFor(["base", "postgres", "flyway", "jwt-auth", "otel-tracing"]);
  assert.ok(deps.includes("web"));
  assert.ok(deps.includes("data-jpa"));
  assert.ok(deps.includes("postgresql"));
  assert.ok(deps.includes("flyway"));
  assert.ok(deps.includes("security"));
  assert.ok(deps.includes("oauth2-resource-server"));
  assert.ok(deps.includes("actuator"));
  assert.ok(deps.includes("distributed-tracing"));
  assert.ok(deps.includes("prometheus"));
});

test("initializrDependenciesFor ignores modules with no known mapping (e.g. aop, business-specific service modules)", () => {
  const deps = initializrDependenciesFor(["base", "aop", "user-service"]);
  assert.deepStrictEqual(deps, ["web"]);
});

test("initializrDependenciesFor de-duplicates across modules sharing a dependency", () => {
  const deps = initializrDependenciesFor(["caching-redis"]);
  assert.deepStrictEqual([...deps].sort(), ["cache", "data-redis", "web"].sort());
});

test("buildInitializrUrl produces the expected query parameters", () => {
  const url = buildInitializrUrl({
    groupId: "com.acme",
    artifactId: "demo",
    packageName: "com.acme.demo",
    name: "demo",
    dependencies: ["web", "data-jpa"],
  });
  const parsed = new URL(url);
  assert.strictEqual(parsed.origin + parsed.pathname, "https://start.spring.io/starter.tgz");
  assert.strictEqual(parsed.searchParams.get("type"), "gradle-project-kotlin");
  assert.strictEqual(parsed.searchParams.get("configurationFileFormat"), "yaml");
  assert.strictEqual(parsed.searchParams.get("groupId"), "com.acme");
  assert.strictEqual(parsed.searchParams.get("artifactId"), "demo");
  assert.strictEqual(parsed.searchParams.get("packageName"), "com.acme.demo");
  assert.strictEqual(parsed.searchParams.get("dependencies"), "web,data-jpa");
});

test("buildInitializrUrl omits the dependencies param when there are none", () => {
  const url = buildInitializrUrl({
    groupId: "com.acme",
    artifactId: "demo",
    packageName: "com.acme.demo",
    name: "demo",
    dependencies: [],
  });
  assert.strictEqual(new URL(url).searchParams.has("dependencies"), false);
});

const fixturePath = path.join(import.meta.dirname, "fixtures", "initializr-sample.tgz");
const fakeFetch = async (): Promise<Response> =>
  new Response(readFileSync(fixturePath), { status: 200, statusText: "OK" });

test("generateSpringBootProject extracts the archive and normalizes application.yaml to .yml", async () => {
  const { mkdtempSync, existsSync, rmSync } = await import("node:fs");
  const os = await import("node:os");
  const dir = mkdtempSync(path.join(os.tmpdir(), "jloom-initializr-"));
  try {
    await generateSpringBootProject(
      dir,
      { groupId: "com.acme", artifactId: "demo", packageName: "com.acme.demo", name: "demo", dependencies: ["web"] },
      fakeFetch,
    );
    assert.ok(existsSync(path.join(dir, "build.gradle.kts")));
    assert.ok(existsSync(path.join(dir, "src/main/resources/application.yml")));
    assert.strictEqual(existsSync(path.join(dir, "src/main/resources/application.yaml")), false);
    assert.strictEqual(existsSync(path.join(dir, "HELP.md")), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("generateSpringBootProject surfaces a clear error on a non-ok response", async () => {
  const failingFetch = async (): Promise<Response> => new Response("", { status: 500, statusText: "Internal Server Error" });
  await assert.rejects(
    () =>
      generateSpringBootProject(
        "/tmp/does-not-matter",
        { groupId: "com.acme", artifactId: "demo", packageName: "com.acme.demo", name: "demo", dependencies: [] },
        failingFetch,
      ),
    /Spring Initializr request failed \(500/,
  );
});
