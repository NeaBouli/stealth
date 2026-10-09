"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const { parseIdentityPublicKey } = require("../security/identity_protocol");

function mockWs() {
  return {
    readyState: 1, messages: [], closed: false,
    send(value) { this.messages.push(JSON.parse(value)); },
    close(code, reason) { this.closed = true; this.closeCode = code; this.closeReason = reason; },
    onceClose: [],
    once(event, listener) { if (event === "close") this.onceClose.push(listener); },
    emitClose() { const run = this.onceClose.splice(0); for (const listener of run) listener(); },
  };
}
function last(ws) { return ws.messages.at(-1); }
function keyMaterial() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  return { pair, ...parseIdentityPublicKey(encoded) };
}
function sign(pair, challenge) {
  return crypto.sign("sha256", Buffer.from(challenge), pair.privateKey).toString("base64url");
}
function resetState() {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
}
function makeContext(registry, fcm, nowSeconds, overrides = {}) {
  return buildContext({
    identityRegistry: registry, identityProtocolMode: "enforce", nowSeconds, fcm,
    identityMigrationRoutes: { size: 0, get: () => null },
    pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
    getIceServers: () => [{ urls: "stun:synthetic.invalid" }],
    ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
    saveFcmTokens: () => true,
    ...overrides,
  });
}
async function flushPromises() { await new Promise(resolve => setImmediate(resolve)); }

(async () => {
  resetState();
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-registration-"));
  const file = path.join(root, "identity_registry.json");
  let now = 2_000_000_000;
  const registry = createIdentityRegistry({ file, now: () => now });
  const pushes = [];
  const fcm = {
    isInitialized: () => true,
    sendIdentityMigrationChallenge: async (token, payload) => { pushes.push({ token, payload }); return true; },
  };
  const trustedMigrationRoutes = new Map();
  const ctx = makeContext(registry, fcm, () => now, {
    identityMigrationRoutes: trustedMigrationRoutes,
  });

  try {
    const alice = keyMaterial();
    const wsAlice = mockWs();
    ctx.clients.set("alice-conn", { ws: wsAlice, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsAlice, "alice-conn", {
      protocol: 2, clientId: alice.identityId, identityId: alice.identityId,
      publicKey: alice.publicKeyBase64Url,
    });
    const aliceChallenge = last(wsAlice);
    assert.strictEqual(aliceChallenge.type, "IDENTITY_REGISTER_CHALLENGE", JSON.stringify(aliceChallenge));
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsAlice, "alice-conn", {
      challengeId: aliceChallenge.challengeId,
      signature: sign(alice.pair, aliceChallenge.challenge),
    });
    assert.strictEqual(last(wsAlice).type, "REGISTERED");
    assert.strictEqual(last(wsAlice).protocol, 2);
    assert.strictEqual(ctx.clientIds.get(alice.identityId), "alice-conn");
    assert.strictEqual(ctx.clients.get("alice-conn").authVersion, 2);

    const replacement = keyMaterial();
    const wsReplacement = mockWs();
    ctx.clients.set("replacement", { ws: wsReplacement, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsReplacement, "replacement", {
      protocol: 2, clientId: replacement.identityId, identityId: replacement.identityId,
      publicKey: replacement.publicKeyBase64Url,
    });
    const supersededChallenge = last(wsReplacement);
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsReplacement, "replacement", {
      protocol: 2, clientId: replacement.identityId, identityId: replacement.identityId,
      publicKey: replacement.publicKeyBase64Url,
    });
    const currentChallenge = last(wsReplacement);
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsReplacement, "replacement", {
      challengeId: supersededChallenge.challengeId,
      signature: sign(replacement.pair, supersededChallenge.challenge),
    });
    assert.strictEqual(last(wsReplacement).error, "invalid_identity_proof");

    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsReplacement, "replacement", {
      challengeId: currentChallenge.challengeId,
      signature: sign(alice.pair, currentChallenge.challenge),
    });
    assert.strictEqual(last(wsReplacement).error, "invalid_identity_proof");

    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsReplacement, "replacement", {
      protocol: 2, clientId: replacement.identityId, identityId: replacement.identityId,
      publicKey: replacement.publicKeyBase64Url,
    });
    const validReplacementChallenge = last(wsReplacement);
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsReplacement, "replacement", {
      challengeId: validReplacementChallenge.challengeId,
      signature: sign(replacement.pair, validReplacementChallenge.challenge),
    });
    assert.strictEqual(last(wsReplacement).type, "REGISTERED");

    const wsReplay = mockWs();
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsReplay, "alice-conn", {
      challengeId: aliceChallenge.challengeId,
      signature: sign(alice.pair, aliceChallenge.challenge),
    });
    assert.strictEqual(last(wsReplay).error, "invalid_identity_proof");

    const wsReconnect = mockWs();
    ctx.clients.set("alice-reconnect", { ws: wsReconnect, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsReconnect, "alice-reconnect", {
      protocol: 2, clientId: alice.identityId, identityId: alice.identityId,
      publicKey: alice.publicKeyBase64Url,
    });
    const reconnectChallenge = last(wsReconnect);
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsReconnect, "alice-reconnect", {
      challengeId: reconnectChallenge.challengeId,
      signature: sign(alice.pair, reconnectChallenge.challenge),
    });
    assert.strictEqual(last(wsReconnect).type, "REGISTERED");
    assert.strictEqual(wsAlice.closed, true);
    assert.strictEqual(ctx.clientIds.get(alice.identityId), "alice-reconnect");

    const wsConcurrent = mockWs();
    ctx.clients.set("alice-concurrent", { ws: wsConcurrent, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsConcurrent, "alice-concurrent", {
      protocol: 2, clientId: alice.identityId, identityId: alice.identityId,
      publicKey: alice.publicKeyBase64Url,
    });
    const concurrentChallenge = last(wsConcurrent);
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsConcurrent, "alice-concurrent", {
      challengeId: concurrentChallenge.challengeId,
      signature: sign(alice.pair, concurrentChallenge.challenge),
    });
    assert.strictEqual(last(wsConcurrent).type, "REGISTERED");
    assert.strictEqual(wsReconnect.closed, true);
    assert.strictEqual(ctx.clientIds.get(alice.identityId), "alice-concurrent");

    const legacy = keyMaterial();
    const wsNoRoute = mockWs();
    ctx.clients.set("legacy-no-route", { ws: wsNoRoute, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsNoRoute, "legacy-no-route", {
      protocol: 2, clientId: "android-no-route", identityId: legacy.identityId,
      publicKey: legacy.publicKeyBase64Url,
    });
    assert.strictEqual(last(wsNoRoute).type, "IDENTITY_MIGRATION_REQUIRED");
    assert.strictEqual(registry.resolve("android-no-route"), null);

    const legacyId = "android-owned01";
    const oldToken = "synthetic-fcm-token-that-is-long-enough-001";
    const attackerToken = "attacker-controlled-fcm-token-long-enough-001";
    trustedMigrationRoutes.set(legacyId, oldToken);
    // Simulate the exact reviewed attack: the mutable runtime FCM store was
    // overwritten. Migration authority must still use the frozen route.
    ctx.fcmTokens.set(legacyId, attackerToken);

    const claimant = keyMaterial();
    const wsClaimant = mockWs();
    ctx.clients.set("claimant", { ws: wsClaimant, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsClaimant, "claimant", {
      protocol: 2, clientId: legacyId, identityId: claimant.identityId,
      publicKey: claimant.publicKeyBase64Url,
    });
    await flushPromises();
    const claimantPending = last(wsClaimant);
    assert.strictEqual(claimantPending.type, "IDENTITY_MIGRATION_PENDING");
    assert.strictEqual(Object.hasOwn(claimantPending, "challenge"), false);
    assert.strictEqual(Object.hasOwn(claimantPending, "challengeId"), false);
    assert.strictEqual(pushes.length, 1);
    assert.strictEqual(pushes[0].token, oldToken);
    assert.notStrictEqual(pushes[0].token, attackerToken);
    assert.strictEqual(pushes[0].payload.identityId, claimant.identityId);
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsClaimant, "claimant", {
      challengeId: crypto.randomUUID(),
      signature: sign(claimant.pair, "securecall-unavailable-fcm-challenge"),
    });
    assert.strictEqual(last(wsClaimant).error, "invalid_identity_proof");
    assert.strictEqual(registry.resolve(legacyId), null);

    const wsLegacy = mockWs();
    ctx.clients.set("legacy-conn", { ws: wsLegacy, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsLegacy, "legacy-conn", {
      protocol: 2, clientId: legacyId, identityId: legacy.identityId,
      publicKey: legacy.publicKeyBase64Url,
    });
    await flushPromises();
    assert.strictEqual(pushes.length, 2);
    assert.strictEqual(last(wsLegacy).type, "IDENTITY_MIGRATION_PENDING");
    const migration = pushes[1].payload;
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsLegacy, "legacy-conn", {
      challengeId: migration.challengeId,
      signature: sign(legacy.pair, migration.challenge),
    });
    assert.strictEqual(last(wsLegacy).type, "REGISTERED");
    assert.strictEqual(last(wsLegacy).legacyAlias, legacyId);
    assert.strictEqual(registry.resolve(legacyId), legacy.identityId);
    assert.strictEqual(ctx.fcmTokens.get(legacy.identityId), oldToken);
    assert.strictEqual(ctx.fcmTokens.has(legacyId), false);

    const failedAlias = "android-persist-failure";
    const failedToken = "synthetic-fcm-token-that-is-long-enough-failure";
    trustedMigrationRoutes.set(failedAlias, failedToken);
    const persistFailure = keyMaterial();
    const wsPersistFailure = mockWs();
    const failureCtx = makeContext(registry, fcm, () => now, {
      identityMigrationRoutes: trustedMigrationRoutes,
      saveFcmTokens: () => false,
    });
    failureCtx.clients.set("persist-failure", {
      ws: wsPersistFailure, clientId: null, lastSeen: Date.now(),
    });
    failureCtx.handlers.IDENTITY_REGISTER_BEGIN(wsPersistFailure, "persist-failure", {
      protocol: 2, clientId: failedAlias, identityId: persistFailure.identityId,
      publicKey: persistFailure.publicKeyBase64Url,
    });
    await flushPromises();
    const failedMigration = pushes.at(-1).payload;
    failureCtx.handlers.IDENTITY_REGISTER_COMPLETE(wsPersistFailure, "persist-failure", {
      challengeId: failedMigration.challengeId,
      signature: sign(persistFailure.pair, failedMigration.challenge),
    });
    assert.strictEqual(last(wsPersistFailure).error, "identity_service_unavailable");
    assert.strictEqual(registry.resolve(failedAlias), null);
    assert.strictEqual(failureCtx.fcmTokens.has(persistFailure.identityId), false);

    const attacker = keyMaterial();
    const wsAttacker = mockWs();
    ctx.clients.set("attacker", { ws: wsAttacker, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsAttacker, "attacker", {
      protocol: 2, clientId: legacyId, identityId: attacker.identityId,
      publicKey: attacker.publicKeyBase64Url,
    });
    assert.strictEqual(last(wsAttacker).error, "identity_alias_conflict");

    const wsUnsigned = mockWs();
    ctx.clients.set("unsigned", { ws: wsUnsigned, clientId: null, lastSeen: Date.now() });
    ctx.handlers.REGISTER(wsUnsigned, "unsigned", { clientId: "android-unsigned" });
    assert.strictEqual(last(wsUnsigned).error, "identity_protocol_required");
    assert.strictEqual(wsUnsigned.closed, true);

    const wsFcm = mockWs();
    ctx.clients.set("not-authenticated", { ws: wsFcm, clientId: "fake", authVersion: null });
    ctx.handlers.REGISTER_FCM_TOKEN(wsFcm, "not-authenticated", {
      fcmToken: "synthetic-fcm-token-that-is-long-enough-002",
    });
    assert.strictEqual(last(wsFcm).error, "not_authenticated");

    const rateLimitedCtx = makeContext(registry, fcm, () => now);
    for (let index = 0; index < 5; index++) {
      const material = keyMaterial();
      const ws = mockWs();
      const connId = `challenge-cap-${index}`;
      rateLimitedCtx.clients.set(connId, {
        ws, clientId: null, lastSeen: Date.now(), ip: "192.0.2.55",
      });
      rateLimitedCtx.handlers.IDENTITY_REGISTER_BEGIN(ws, connId, {
        protocol: 2, clientId: material.identityId, identityId: material.identityId,
        publicKey: material.publicKeyBase64Url,
      });
      if (index < 4) assert.strictEqual(last(ws).type, "IDENTITY_REGISTER_CHALLENGE");
      else assert.strictEqual(last(ws).error, "identity_challenge_rate_limited");
    }

    const expiring = keyMaterial();
    const wsExpired = mockWs();
    ctx.clients.set("expired", { ws: wsExpired, clientId: null });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(wsExpired, "expired", {
      protocol: 2, clientId: expiring.identityId, identityId: expiring.identityId,
      publicKey: expiring.publicKeyBase64Url,
    });
    const expiredChallenge = last(wsExpired);
    now += 301;
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(wsExpired, "expired", {
      challengeId: expiredChallenge.challengeId,
      signature: sign(expiring.pair, expiredChallenge.challenge),
    });
    assert.strictEqual(last(wsExpired).error, "invalid_identity_proof");

    // F1: legacy REGISTER in transition mode must not claim canonical ids or
    // registry-bound aliases (owner offline); unbound legacy ids still work.
    const transitionCtx = makeContext(registry, fcm, () => now, {
      identityProtocolMode: "transition", identityTransitionDeadline: now + 1000,
    });
    transitionCtx.clientIds.clear(); // shared singleton state: owners go offline
    for (const [name, claimed] of [
      ["canonical", alice.identityId], ["bound-alias", legacyId],
    ]) {
      assert.strictEqual(transitionCtx.clientIds.has(claimed), false, "owner must be offline");
      const wsClaim = mockWs();
      transitionCtx.clients.set(`legacy-${name}`, { ws: wsClaim, clientId: null, lastSeen: Date.now() });
      transitionCtx.handlers.REGISTER(wsClaim, `legacy-${name}`, { clientId: claimed });
      assert.strictEqual(last(wsClaim).error, "identity_alias_conflict", name);
      assert.strictEqual(transitionCtx.clientIds.has(claimed), false, name);
      assert.strictEqual(transitionCtx.clients.get(`legacy-${name}`).clientId, null, name);
    }
    const wsUnbound = mockWs();
    transitionCtx.clients.set("legacy-unbound", { ws: wsUnbound, clientId: null, lastSeen: Date.now() });
    transitionCtx.handlers.REGISTER(wsUnbound, "legacy-unbound", { clientId: "android-unbound01" });
    assert.strictEqual(last(wsUnbound).type, "REGISTERED");
    assert.strictEqual(last(wsUnbound).protocol, 1);

    // F2: pending challenges are released when their socket closes; repeated
    // disconnecting clients from one IP never exhaust the cap, and an unrelated
    // live connection keeps its pending proof.
    const closeCtx = makeContext(registry, fcm, () => now);
    const liveMaterial = keyMaterial();
    const wsLive = mockWs();
    closeCtx.clients.set("close-live", { ws: wsLive, clientId: null, lastSeen: Date.now(), ip: "192.0.2.77" });
    closeCtx.handlers.IDENTITY_REGISTER_BEGIN(wsLive, "close-live", {
      protocol: 2, clientId: liveMaterial.identityId, identityId: liveMaterial.identityId,
      publicKey: liveMaterial.publicKeyBase64Url,
    });
    const liveChallenge = last(wsLive);
    assert.strictEqual(liveChallenge.type, "IDENTITY_REGISTER_CHALLENGE");
    for (let index = 0; index < 12; index++) {
      const material = keyMaterial();
      const ws = mockWs();
      const connId = `close-loop-${index}`;
      closeCtx.clients.set(connId, { ws, clientId: null, lastSeen: Date.now(), ip: "192.0.2.77" });
      closeCtx.handlers.IDENTITY_REGISTER_BEGIN(ws, connId, {
        protocol: 2, clientId: material.identityId, identityId: material.identityId,
        publicKey: material.publicKeyBase64Url,
      });
      assert.strictEqual(last(ws).type, "IDENTITY_REGISTER_CHALLENGE", `loop ${index}`);
      closeCtx.handlers.IDENTITY_REGISTER_BEGIN(ws, connId, {
        protocol: 2, clientId: material.identityId, identityId: material.identityId,
        publicKey: material.publicKeyBase64Url,
      });
      assert.strictEqual(ws.onceClose.length, 1, "one close listener per socket");
      ws.emitClose();
      closeCtx.clients.delete(connId);
    }
    closeCtx.handlers.IDENTITY_REGISTER_COMPLETE(wsLive, "close-live", {
      challengeId: liveChallenge.challengeId,
      signature: sign(liveMaterial.pair, liveChallenge.challenge),
    });
    assert.strictEqual(last(wsLive).type, "REGISTERED");
    // Single use still holds after a close.
    closeCtx.handlers.IDENTITY_REGISTER_COMPLETE(wsLive, "close-live", {
      challengeId: liveChallenge.challengeId,
      signature: sign(liveMaterial.pair, liveChallenge.challenge),
    });
    assert.strictEqual(last(wsLive).error, "invalid_identity_proof");
    // A closed socket's challenge cannot be completed afterwards.
    const wsGone = mockWs();
    const goneMaterial = keyMaterial();
    closeCtx.clients.set("close-gone", { ws: wsGone, clientId: null, lastSeen: Date.now(), ip: "192.0.2.78" });
    closeCtx.handlers.IDENTITY_REGISTER_BEGIN(wsGone, "close-gone", {
      protocol: 2, clientId: goneMaterial.identityId, identityId: goneMaterial.identityId,
      publicKey: goneMaterial.publicKeyBase64Url,
    });
    const goneChallenge = last(wsGone);
    wsGone.emitClose();
    closeCtx.handlers.IDENTITY_REGISTER_COMPLETE(wsGone, "close-gone", {
      challengeId: goneChallenge.challengeId,
      signature: sign(goneMaterial.pair, goneChallenge.challenge),
    });
    assert.strictEqual(last(wsGone).error, "invalid_identity_proof");

    console.log("identity_registration.test.js: PASS");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
})().catch(error => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
