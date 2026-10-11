"use strict";

const fs = require("fs");
const path = require("path");
const {
  CLIENT_ID,
  IDENTITY_ID,
  MAX_TRANSITION_WINDOW_SECONDS,
} = require("../security/identity_protocol");

function fail(code) {
  const error = new Error(code);
  error.code = code;
  throw error;
}

/**
 * Load the immutable, pre-transition FCM routes used only to authorize a
 * one-time legacy alias migration. Runtime FCM registration must never mutate
 * this authority source.
 */
function loadIdentityMigrationRoutes({
  file,
  nowSeconds = () => Math.floor(Date.now() / 1000),
}) {
  if (!path.isAbsolute(file)) fail("invalid_identity_migration_routes_file");
  if (!fs.existsSync(file)) {
    return Object.freeze({ size: 0, get: () => null });
  }

  const stat = fs.lstatSync(file);
  if (!stat.isFile() || stat.isSymbolicLink() || (stat.mode & 0o077) !== 0) {
    fail("insecure_identity_migration_routes_file");
  }

  let parsed;
  try { parsed = JSON.parse(fs.readFileSync(file, "utf8")); }
  catch { fail("invalid_identity_migration_routes_file"); }
  if (!parsed || parsed.schema !== 1 || !Number.isSafeInteger(parsed.createdAt)
      || !Number.isSafeInteger(parsed.expiresAt) || parsed.expiresAt <= parsed.createdAt
      || parsed.expiresAt > parsed.createdAt + MAX_TRANSITION_WINDOW_SECONDS
      || !parsed.routes || typeof parsed.routes !== "object" || Array.isArray(parsed.routes)) {
    fail("invalid_identity_migration_routes_file");
  }

  const routes = new Map();
  for (const [legacyClientId, token] of Object.entries(parsed.routes)) {
    if (!CLIENT_ID.test(legacyClientId) || IDENTITY_ID.test(legacyClientId)
        || ["__proto__", "constructor", "prototype"].includes(legacyClientId)
        || typeof token !== "string" || token.length < 20 || token.length > 4096) {
      fail("invalid_identity_migration_routes_file");
    }
    routes.set(legacyClientId, token);
  }
  const active = () => {
    const current = nowSeconds();
    return Number.isSafeInteger(current) && current <= parsed.expiresAt;
  };
  return Object.freeze({
    size: active() ? routes.size : 0,
    expiresAt: parsed.expiresAt,
    get: legacyClientId => active() ? routes.get(legacyClientId) || null : null,
  });
}

module.exports = { loadIdentityMigrationRoutes };
