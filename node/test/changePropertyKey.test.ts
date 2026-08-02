import { test } from "node:test";
import assert from "node:assert/strict";
import yaml from "js-yaml";
import { changePropertyKeyContent } from "../src/merge/changePropertyKey.ts";

test("renames a nested key, preserving its value", () => {
  const target = "identity:\n  issuer: jloom-app\n  token-ttl-seconds: 3600\n";
  const result = changePropertyKeyContent(target, {
    type: "org.openrewrite.yaml.ChangePropertyKey",
    oldPropertyKey: "identity.issuer",
    newPropertyKey: "jwt.issuer",
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(result) as any;
  assert.strictEqual(doc.jwt.issuer, "jloom-app");
  assert.strictEqual(doc.identity.issuer, undefined);
});

test("is a no-op when the old key is not present", () => {
  const target = "server:\n  port: 8080\n";
  const result = changePropertyKeyContent(target, {
    type: "org.openrewrite.yaml.ChangePropertyKey",
    oldPropertyKey: "identity.issuer",
    newPropertyKey: "jwt.issuer",
    filePattern: "**/application.yml",
  });
  assert.deepStrictEqual(yaml.load(result), yaml.load(target));
});

test("chains two renames from the same real upgrade fragment", () => {
  let content = "identity:\n  issuer: jloom-app\n  token-ttl-seconds: 3600\n";
  content = changePropertyKeyContent(content, {
    type: "org.openrewrite.yaml.ChangePropertyKey",
    oldPropertyKey: "identity.issuer",
    newPropertyKey: "jwt.issuer",
    filePattern: "**/application.yml",
  });
  content = changePropertyKeyContent(content, {
    type: "org.openrewrite.yaml.ChangePropertyKey",
    oldPropertyKey: "identity.token-ttl-seconds",
    newPropertyKey: "jwt.token-ttl-seconds",
    filePattern: "**/application.yml",
  });
  const doc = yaml.load(content) as any;
  assert.strictEqual(doc.jwt.issuer, "jloom-app");
  assert.strictEqual(doc.jwt["token-ttl-seconds"], 3600);
});
