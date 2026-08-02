import { test } from "node:test";
import assert from "node:assert/strict";
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import yaml from "js-yaml";
import { applyOperations } from "../src/merge/executor.ts";

function tempProject(): string {
  const root = mkdtempSync(path.join(os.tmpdir(), "jloom-executor-"));
  writeFileSync(
    path.join(root, "build.gradle.kts"),
    'plugins {\n    id("java")\n}\n\ndependencies {\n}\n',
    "utf8",
  );
  writeFileSync(path.join(root, "application.yml"), "", "utf8");
  return root;
}

test("applies a mixed batch of operations end to end against real files", () => {
  const root = tempProject();
  try {
    applyOperations(root, [
      {
        type: "org.openrewrite.gradle.AddDependency",
        groupId: "org.postgresql",
        artifactId: "postgresql",
        configuration: "runtimeOnly",
      },
      {
        type: "org.openrewrite.yaml.MergeYaml",
        key: "$.spring.datasource",
        yaml: "url: jdbc:postgresql://localhost/demo\n",
        filePattern: "**/application.yml",
      },
      {
        type: "org.openrewrite.text.CreateTextFile",
        relativeFileName: "src/main/resources/db/migration/V1__init.sql",
        fileContents: "create table demo();\n",
        overwriteExisting: false,
      },
    ]);

    const buildFile = readFileSync(path.join(root, "build.gradle.kts"), "utf8");
    assert.ok(buildFile.includes("org.postgresql:postgresql"));

    const appYaml = yaml.load(readFileSync(path.join(root, "application.yml"), "utf8")) as any;
    assert.strictEqual(appYaml.spring.datasource.url, "jdbc:postgresql://localhost/demo");

    const migration = readFileSync(path.join(root, "src/main/resources/db/migration/V1__init.sql"), "utf8");
    assert.strictEqual(migration, "create table demo();\n");
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test("finds build.gradle when build.gradle.kts is absent (Micronaut variant)", () => {
  const root = mkdtempSync(path.join(os.tmpdir(), "jloom-executor-groovy-"));
  try {
    writeFileSync(path.join(root, "build.gradle"), 'plugins {\n    id("java")\n}\n\ndependencies {\n}\n', "utf8");
    applyOperations(root, [
      {
        type: "org.openrewrite.gradle.AddDependency",
        groupId: "io.micronaut",
        artifactId: "micronaut-http-client",
        configuration: "implementation",
      },
    ]);
    const buildFile = readFileSync(path.join(root, "build.gradle"), "utf8");
    assert.ok(buildFile.includes("io.micronaut:micronaut-http-client"));
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});
