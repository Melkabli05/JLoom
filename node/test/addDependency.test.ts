import { test } from "node:test";
import assert from "node:assert/strict";
import { addDependencyContent } from "../src/merge/addDependency.ts";

const BUILD_FILE = `
plugins {
    id("java")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
}
`;

test("inserts a new dependency right after the dependencies block opens", () => {
  const result = addDependencyContent(BUILD_FILE, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "org.postgresql",
    artifactId: "postgresql",
    configuration: "runtimeOnly",
  });
  assert.ok(result.includes('runtimeOnly("org.postgresql:postgresql")'));
  // Existing dependency is preserved.
  assert.ok(result.includes('implementation("org.springframework.boot:spring-boot-starter-webmvc")'));
});

test("pins a version when one is given", () => {
  const result = addDependencyContent(BUILD_FILE, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "io.minio",
    artifactId: "minio",
    version: "8.6.0",
    configuration: "implementation",
  });
  assert.ok(result.includes('implementation("io.minio:minio:8.6.0")'));
});

test("is idempotent - does not duplicate an already-present coordinate", () => {
  const once = addDependencyContent(BUILD_FILE, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "org.postgresql",
    artifactId: "postgresql",
    configuration: "runtimeOnly",
  });
  const twice = addDependencyContent(once, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "org.postgresql",
    artifactId: "postgresql",
    configuration: "runtimeOnly",
  });
  assert.strictEqual(twice, once);
  assert.strictEqual(twice.split("org.postgresql:postgresql").length - 1, 1);
});

test("the same coordinate under two different configurations both land (e.g. Lombok needs compileOnly AND annotationProcessor)", () => {
  let content = addDependencyContent(BUILD_FILE, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "org.projectlombok",
    artifactId: "lombok",
    configuration: "compileOnly",
  });
  content = addDependencyContent(content, {
    type: "org.openrewrite.gradle.AddDependency",
    groupId: "org.projectlombok",
    artifactId: "lombok",
    configuration: "annotationProcessor",
  });
  assert.ok(content.includes('compileOnly("org.projectlombok:lombok")'));
  assert.ok(content.includes('annotationProcessor("org.projectlombok:lombok")'));
});

test("throws when there is no dependencies block to insert into", () => {
  assert.throws(() =>
    addDependencyContent("plugins { id(\"java\") }", {
      type: "org.openrewrite.gradle.AddDependency",
      groupId: "a",
      artifactId: "b",
      configuration: "implementation",
    }),
  );
});
