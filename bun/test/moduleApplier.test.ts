import { test } from "node:test";
import assert from "node:assert/strict";
import { existsSync, mkdtempSync, readFileSync, rmSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import yaml from "js-yaml";
import { apply } from "../src/orchestrate/moduleApplier.ts";
import { ModuleRegistry } from "../src/registry/moduleRegistry.ts";
import { loadState } from "../src/state/projectStateStore.ts";

const registry = ModuleRegistry.loadBundled();

function tempDir(): string {
  return mkdtempSync(path.join(os.tmpdir(), "jloom-apply-"));
}

// A real Spring Initializr response recorded once (test/fixtures/initializr-sample.tgz) so
// these tests never make a live network call - the fake fetch below always returns it,
// regardless of the requested URL. That means the *generated file layout* it produces is
// fixed (com/example/demo/DemoApplication.java) no matter what basePackage/projectName a test
// passes to apply() - state.json seeding is independent of that and still reflects the real
// arguments. Actually calling the live service end to end is covered separately (Phase 4).
const fixturePath = path.join(import.meta.dirname, "fixtures", "initializr-sample.tgz");
const fakeFetch = async (): Promise<Response> =>
  new Response(readFileSync(fixturePath), { status: 200, statusText: "OK" });

test("dry run on a brand-new project writes nothing to disk", async () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    const result = await apply(registry, target, ["base"], {}, true, "com.acme.demo", "my-app", fakeFetch);

    assert.strictEqual(result.kind, "dryRun");
    assert.strictEqual(existsSync(path.join(target, ".jloom", "state.json")), false);
    assert.strictEqual(existsSync(target), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("rejection leaves no state file on disk", async () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "half-baked");
    const result = await apply(registry, target, ["not-a-real-module"], {}, false, "com.acme.demo", "half-baked", fakeFetch);

    assert.strictEqual(result.kind, "rejected");
    assert.strictEqual(existsSync(path.join(target, ".jloom", "state.json")), false);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

// The following exercise the actual merge-into-real-files path end to end - coverage the Java
// suite never had in isolation, since it only ever reached this transitively through a real
// Gradle+OpenRewrite subprocess (network-dependent, ~seconds per test). Here it's in-process
// (aside from the scaffold step, which uses the recorded fixture above) and needs no live
// network or subprocess.

test("applying base scaffolds a real project directory via Initializr, plus jloom's own ArchitectureTest", async () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    const result = await apply(registry, target, ["base"], {}, false, "com.acme.demo", "my-app", fakeFetch);

    assert.strictEqual(result.kind, "applied");
    assert.ok(existsSync(path.join(target, "build.gradle.kts")));
    // The fixture's own fixed package layout (see comment above) - real parameterization by
    // groupId/artifactId/packageName is verified in initializr.test.ts and Phase 4's live check.
    assert.ok(existsSync(path.join(target, "src/main/java/com/example/demo/DemoApplication.java")));
    assert.ok(existsSync(path.join(target, "src/test/java/com/acme/demo/ArchitectureTest.java")));

    const buildFile = readFileSync(path.join(target, "build.gradle.kts"), "utf8");
    assert.ok(buildFile.includes("com.tngtech.archunit:archunit-junit5"));

    const state = loadState(target);
    assert.strictEqual(state.projectName, "my-app");
    assert.strictEqual(state.basePackage, "com.acme.demo");
    assert.deepStrictEqual(state.modules.map((m) => m.id), ["base"]);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});

test("applying postgres after base merges the dependency and datasource config", async () => {
  const dir = tempDir();
  try {
    const target = path.join(dir, "my-app");
    await apply(registry, target, ["base"], {}, false, "com.acme.demo", "my-app", fakeFetch);

    const result = await apply(registry, target, ["postgres"], { "postgres.db_name": "demo" }, false, undefined, undefined, fakeFetch);
    assert.strictEqual(result.kind, "applied");

    const buildFile = readFileSync(path.join(target, "build.gradle.kts"), "utf8");
    assert.ok(buildFile.includes("org.postgresql:postgresql"));
    assert.ok(buildFile.includes("spring-boot-starter-data-jpa"));

    const appYaml = yaml.load(readFileSync(path.join(target, "src/main/resources/application.yml"), "utf8")) as any;
    assert.strictEqual(appYaml.spring.datasource.url, "jdbc:postgresql://localhost:5432/demo");

    const state = loadState(target);
    assert.deepStrictEqual(state.modules.map((m) => m.id), ["base", "postgres"]);
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
});
