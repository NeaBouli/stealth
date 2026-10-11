"use strict";

// #144 review F2, owner decision 2026-10-11: legacy ids are kept via alias migration, which
// requires an audit trail and an operator revoke. Synthetic keys and tokens, temp dirs only.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const { createMigrationAudit } = require("../services/identity_migration_audit");
const { setupIdentityAliasAdminRoutes } = require("../services/identity_alias_admin");
const { parseIdentityPublicKey } = require("../security/identity_protocol");

function mockWs() {
  return { messages: [], send(v) { this.messages.push(JSON.parse(v)); }, close() {}, once() {} };
}
const last = ws => ws.messages.at(-1);
function keyMaterial() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  return { pair, ...parseIdentityPublicKey(encoded) };
}
const sign = (pair, challenge) =>
  crypto.sign("sha256", Buffer.from(challenge), pair.privateKey).toString("base64url");
function resetState() {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
}
function makeContext(registry, fcm, nowSeconds, overrides) {
  return buildContext({
    identityRegistry: registry, identityProtocolMode: "enforce", nowSeconds, fcm,
    pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
    getIceServers: () => [], ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
    saveFcmTokens: () => true,
    ...overrides,
  });
}
const flush = () => new Promise(resolve => setImmediate(resolve));

async function migrate(ctx, pushes, connId, legacyId, key) {
  const ws = mockWs();
  ctx.clients.set(connId, { ws, clientId: null, ip: "192.0.2.10" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(ws, connId, { protocol: 2, clientId: legacyId,
    identityId: key.identityId, publicKey: key.publicKeyBase64Url });
  await flush();
  const payload = pushes.at(-1)?.payload;
  if (last(ws).type !== "IDENTITY_MIGRATION_PENDING" || !payload) return ws;
  ctx.handlers.IDENTITY_REGISTER_COMPLETE(ws, connId, { challengeId: payload.challengeId,
    signature: sign(key.pair, payload.challenge) });
  return ws;
}

(async () => {
  resetState();
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-alias-revoke-"));
  const now = 2_000_000_000;
  const registry = createIdentityRegistry({ file: path.join(root, "identity_registry.json"), now: () => now });
  const pushes = [];
  const fcm = { isInitialized: () => true,
    sendIdentityMigrationChallenge: async (token, payload) => { pushes.push({ token, payload }); return true; } };
  const routes = new Map([
    ["android-noaudit1", "synthetic-route-token-noaudit-0001"],
    ["android-auditfail", "synthetic-route-token-auditfail-001"],
    ["android-victim01", "synthetic-route-token-victim-000001"],
  ]);

  // No audit trail configured: migration fails closed and binds nothing.
  const noAuditCtx = makeContext(registry, fcm, () => now, { identityMigrationRoutes: routes });
  const k1 = keyMaterial();
  const ws1 = await migrate(noAuditCtx, pushes, "c-noaudit", "android-noaudit1", k1);
  assert.strictEqual(last(ws1).error, "identity_service_unavailable");
  assert.strictEqual(registry.resolve("android-noaudit1"), null);

  // Audit write fails: same.
  const failingAudit = { append() { throw Object.assign(new Error("x"), { code: "identity_migration_audit_unavailable" }); }, read: () => [] };
  const failCtx = makeContext(registry, fcm, () => now, { identityMigrationRoutes: routes, identityMigrationAudit: failingAudit });
  const k2 = keyMaterial();
  const ws2 = await migrate(failCtx, pushes, "c-auditfail", "android-auditfail", k2);
  assert.strictEqual(last(ws2).error, "identity_service_unavailable");
  assert.strictEqual(registry.resolve("android-auditfail"), null);

  // Audited migration, then operator revoke.
  const audit = createMigrationAudit({ file: path.join(root, "audit.jsonl"), now: () => now });
  const ctx = makeContext(registry, fcm, () => now, { identityMigrationRoutes: routes, identityMigrationAudit: audit });
  const claimant = keyMaterial();
  const ws3 = await migrate(ctx, pushes, "c-claim", "android-victim01", claimant);
  assert.strictEqual(last(ws3).type, "REGISTERED", JSON.stringify(last(ws3)));
  assert.strictEqual(registry.resolve("android-victim01"), claimant.identityId);
  assert.strictEqual(ctx.fcmTokens.get(claimant.identityId), "synthetic-route-token-victim-000001");

  const routesByPath = {};
  const app = { post(route, guard, handler) { routesByPath[route] = { guard, handler }; } };
  let saved = 0;
  const requireAdmin = (req, res, next) => next();
  setupIdentityAliasAdminRoutes(app, requireAdmin, { identityRegistry: registry, identityMigrationAudit: audit,
    fcmTokens: ctx.fcmTokens, saveFcmTokens: () => { saved++; return true; } });
  const route = routesByPath["/admin/identity/alias/revoke"];
  assert.strictEqual(route.guard, requireAdmin, "route is admin-guarded");
  const call = body => { const res = { code: 200, body: null,
    status(c) { this.code = c; return this; }, json(b) { this.body = b; return this; } };
    route.handler({ body }, res); return res; };

  assert.strictEqual(call({ alias: "android-victim01" }).code, 400, "reason required");
  assert.strictEqual(call({ alias: "android-unknown1", reason: "test" }).code, 404);
  const revoked = call({ alias: "android-victim01", reason: "owner dispute" });
  assert.strictEqual(revoked.code, 200, JSON.stringify(revoked.body));
  assert.strictEqual(revoked.body.changed, true);
  assert.strictEqual(revoked.body.inheritedRouteRemoved, true);
  assert.strictEqual(revoked.body.entitlementsReviewRequired, true);
  assert.strictEqual(registry.resolve("android-victim01"), null, "revoked alias resolves to nothing");
  assert.strictEqual(ctx.fcmTokens.has(claimant.identityId), false, "inherited push route removed");
  assert.strictEqual(saved, 1);
  assert.strictEqual(registry.resolve(claimant.identityId), claimant.identityId, "claimant keeps only its own identity");
  assert.deepStrictEqual(audit.read().map(r => r.event), ["alias_bind_authorized", "alias_revoked"]);

  // Survives restart and cannot be re-migrated automatically.
  const reloaded = createIdentityRegistry({ file: path.join(root, "identity_registry.json"), now: () => now });
  assert.strictEqual(reloaded.resolve("android-victim01"), null);
  assert.throws(() => reloaded.bind({ requestedClientId: "android-victim01", identityId: claimant.identityId,
    publicKey: claimant.publicKeyBase64Url, migrationAuthorized: true }), e => e.code === "identity_alias_conflict");
  assert.strictEqual(call({ alias: "android-victim01", reason: "again" }).body.changed, false, "idempotent");

  console.log("identity_alias_revoke.test.js: PASS");
})().catch(error => { console.error(error); process.exit(1); });
