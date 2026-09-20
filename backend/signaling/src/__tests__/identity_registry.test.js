"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { createIdentityRegistry } = require("../services/identity_registry");
const { parseIdentityPublicKey } = require("../security/identity_protocol");

const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-identity-registry-"));
const file = path.join(root, "identity_registry.json");
let clock = 2_000_000_000;

try {
  const registry = createIdentityRegistry({ file, now: () => clock++ });
  assert.strictEqual(fs.statSync(file).mode & 0o777, 0o600);

  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const publicKey = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  const parsed = parseIdentityPublicKey(publicKey);

  const enrolled = registry.bind({
    requestedClientId: parsed.identityId,
    identityId: parsed.identityId,
    publicKey,
  });
  assert.strictEqual(enrolled.identityId, parsed.identityId);
  assert.strictEqual(registry.resolve(parsed.identityId), parsed.identityId);

  assert.throws(() => registry.bind({
    requestedClientId: "android-legacy01",
    identityId: parsed.identityId,
    publicKey,
  }), /identity_migration_required/);

  registry.bind({
    requestedClientId: "android-legacy01",
    identityId: parsed.identityId,
    publicKey,
    migrationAuthorized: true,
  });
  assert.strictEqual(registry.resolve("android-legacy01"), parsed.identityId);
  assert.deepStrictEqual(registry.aliasesFor(parsed.identityId), ["android-legacy01"]);

  const otherPair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const otherPublic = otherPair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  const other = parseIdentityPublicKey(otherPublic);
  assert.throws(() => registry.bind({
    requestedClientId: "android-legacy01",
    identityId: other.identityId,
    publicKey: otherPublic,
    migrationAuthorized: true,
  }), /identity_alias_conflict/);

  const persisted = createIdentityRegistry({ file, now: () => clock++ });
  assert.strictEqual(persisted.resolve("android-legacy01"), parsed.identityId);
  assert.strictEqual(persisted.getIdentity(parsed.identityId).publicKey, publicKey);

  fs.writeFileSync(file, "not-json", { mode: 0o600 });
  assert.throws(() => createIdentityRegistry({ file }), /identity_registry_unavailable/);
} finally {
  fs.rmSync(root, { recursive: true, force: true });
}

console.log("identity_registry.test.js: PASS");
