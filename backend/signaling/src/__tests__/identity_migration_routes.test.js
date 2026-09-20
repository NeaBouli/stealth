"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { loadIdentityMigrationRoutes } = require("../services/identity_migration_routes");
const { prepareIdentityMigrationRoutes } = require("../../scripts/prepare_identity_migration_routes");

const root = fs.mkdtempSync(path.join(os.tmpdir(), "identity-migration-routes-"));
try {
  const missing = loadIdentityMigrationRoutes({ file: path.join(root, "missing.json") });
  assert.strictEqual(missing.size, 0);
  assert.strictEqual(missing.get("legacy-01"), null);

  const file = path.join(root, "routes.json");
  const token = "trusted-fcm-token-that-is-long-enough";
  const source = path.join(root, "fcm_tokens.json");
  fs.writeFileSync(source, JSON.stringify({
    "legacy-01": token,
    "sc-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA": "canonical-token-is-not-migrated",
  }), { mode: 0o600 });
  assert.strictEqual(prepareIdentityMigrationRoutes({
    sourceFile: source, outputFile: file, nowSeconds: 2_000_000_000,
  }), 1);
  assert.strictEqual(fs.statSync(file).mode & 0o777, 0o600);
  assert.throws(
    () => prepareIdentityMigrationRoutes({
      sourceFile: source, outputFile: file, nowSeconds: 2_000_000_001,
    }),
    error => error?.code === "identity_migration_routes_already_exist",
  );

  let now = 2_000_000_100;
  const first = loadIdentityMigrationRoutes({ file, nowSeconds: () => now });
  const afterRestart = loadIdentityMigrationRoutes({ file, nowSeconds: () => now });
  assert.strictEqual(first.get("legacy-01"), token);
  assert.strictEqual(afterRestart.get("legacy-01"), token);
  assert.strictEqual(first.get("sc-AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"), null);
  assert.strictEqual(first.get("unknown"), null);
  now = first.expiresAt + 1;
  assert.strictEqual(first.get("legacy-01"), null);

  fs.chmodSync(file, 0o644);
  assert.throws(
    () => loadIdentityMigrationRoutes({ file }),
    error => error?.code === "insecure_identity_migration_routes_file",
  );
  console.log("identity_migration_routes.test.js: PASS");
} finally {
  fs.rmSync(root, { recursive: true, force: true });
}
