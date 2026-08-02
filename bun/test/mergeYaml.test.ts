import { test } from "node:test";
import assert from "node:assert/strict";
import yaml from "js-yaml";
import { mergeYamlContent } from "../src/merge/mergeYaml.ts";

test("merges at the root ($) into an existing document", () => {
  const target = "server:\n  port: 8080\n";
  const result = mergeYamlContent(target, {
    type: "org.openrewrite.yaml.MergeYaml",
    key: "$",
    yaml: "spring:\n  application:\n    name: demo\n",
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(result) as any;
  assert.strictEqual(doc.server.port, 8080);
  assert.strictEqual(doc.spring.application.name, "demo");
});

test("merges at a nested path, creating intermediate maps as needed", () => {
  const result = mergeYamlContent("", {
    type: "org.openrewrite.yaml.MergeYaml",
    key: "$.jwt",
    yaml: 'secret: "s3cr3t"\nissuer: jloom-app\n',
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(result) as any;
  assert.strictEqual(doc.jwt.secret, "s3cr3t");
  assert.strictEqual(doc.jwt.issuer, "jloom-app");
});

test("merges two fragments at different nested paths without clobbering each other", () => {
  let content = mergeYamlContent("", {
    type: "org.openrewrite.yaml.MergeYaml",
    key: "$.spring",
    yaml: "datasource:\n  url: jdbc:postgresql://localhost/db\n",
    filePattern: "**/application.yml",
  });
  content = mergeYamlContent(content, {
    type: "org.openrewrite.yaml.MergeYaml",
    key: "$.spring.jpa",
    yaml: "open-in-view: false\n",
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(content) as any;
  assert.strictEqual(doc.spring.datasource.url, "jdbc:postgresql://localhost/db");
  assert.strictEqual(doc.spring.jpa["open-in-view"], false);
});

test("a scalar in the fragment overwrites a scalar already at that key", () => {
  const target = "jwt:\n  issuer: old-issuer\n";
  const result = mergeYamlContent(target, {
    type: "org.openrewrite.yaml.MergeYaml",
    key: "$.jwt",
    yaml: "issuer: new-issuer\n",
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(result) as any;
  assert.strictEqual(doc.jwt.issuer, "new-issuer");
});
