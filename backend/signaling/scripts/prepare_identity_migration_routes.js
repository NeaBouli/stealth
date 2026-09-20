#!/usr/bin/env node
"use strict";

const fs = require("fs");
const path = require("path");
const {
  CLIENT_ID,
  IDENTITY_ID,
  MAX_TRANSITION_WINDOW_SECONDS,
} = require("../src/security/identity_protocol");

function fail(code) {
  const error = new Error(code);
  error.code = code;
  throw error;
}

function prepareIdentityMigrationRoutes({ sourceFile, outputFile, nowSeconds, expiresAt }) {
  if (!path.isAbsolute(sourceFile) || !path.isAbsolute(outputFile) || sourceFile === outputFile) {
    fail("invalid_identity_migration_route_paths");
  }
  const sourceStat = fs.lstatSync(sourceFile);
  if (!sourceStat.isFile() || sourceStat.isSymbolicLink()) fail("invalid_fcm_token_source");
  if (fs.existsSync(outputFile)) fail("identity_migration_routes_already_exist");

  let source;
  try { source = JSON.parse(fs.readFileSync(sourceFile, "utf8")); }
  catch { fail("invalid_fcm_token_source"); }
  if (!source || typeof source !== "object" || Array.isArray(source)) fail("invalid_fcm_token_source");

  const routes = Object.create(null);
  for (const [clientId, token] of Object.entries(source)) {
    if (!CLIENT_ID.test(clientId) || IDENTITY_ID.test(clientId)) continue;
    if (["__proto__", "constructor", "prototype"].includes(clientId)) {
      fail("invalid_fcm_token_source");
    }
    if (typeof token !== "string" || token.length < 20 || token.length > 4096) {
      fail("invalid_fcm_token_source");
    }
    routes[clientId] = token;
  }
  const createdAt = Number(nowSeconds);
  if (!Number.isSafeInteger(createdAt) || createdAt <= 0) fail("invalid_snapshot_time");
  const deadline = expiresAt === undefined
    ? createdAt + MAX_TRANSITION_WINDOW_SECONDS : Number(expiresAt);
  if (!Number.isSafeInteger(deadline) || deadline <= createdAt
      || deadline > createdAt + MAX_TRANSITION_WINDOW_SECONDS) {
    fail("invalid_snapshot_deadline");
  }

  fs.mkdirSync(path.dirname(outputFile), { recursive: true, mode: 0o700 });
  let descriptor;
  try {
    descriptor = fs.openSync(outputFile, fs.constants.O_CREAT | fs.constants.O_EXCL
      | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
    fs.writeFileSync(descriptor, JSON.stringify({
      schema: 1, createdAt, expiresAt: deadline, routes,
    }));
    fs.fsyncSync(descriptor);
  } catch (error) {
    if (descriptor !== undefined) {
      fs.closeSync(descriptor);
      try { fs.unlinkSync(outputFile); } catch { /* no partial file */ }
    }
    throw error;
  }
  fs.closeSync(descriptor);
  fs.chmodSync(outputFile, 0o600);
  const directory = fs.openSync(path.dirname(outputFile), fs.constants.O_RDONLY);
  try { fs.fsyncSync(directory); } finally { fs.closeSync(directory); }
  return Object.keys(routes).length;
}

if (require.main === module) {
  try {
    const count = prepareIdentityMigrationRoutes({
      sourceFile: process.env.FCM_TOKENS_FILE || "",
      outputFile: process.env.IDENTITY_MIGRATION_ROUTES_FILE || "",
      nowSeconds: Math.floor(Date.now() / 1000),
      expiresAt: process.env.IDENTITY_MIGRATION_DEADLINE_EPOCH_SECONDS || undefined,
    });
    console.log(`Prepared ${count} immutable legacy migration routes`);
  } catch (error) {
    console.error(`Identity migration route preparation failed: ${error.code || "unknown_error"}`);
    process.exitCode = 1;
  }
}

module.exports = { prepareIdentityMigrationRoutes };
