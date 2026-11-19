"use strict";

// SC245: mixed-version call routing. Advisory capability from the accepted
// registration steers fail-fast UX only; signed protocol checks stay authoritative.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const { callInviteTranscript, parseIdentityPublicKey } = require("../security/identity_protocol");

function mockWs() {
  return {
    readyState: 1, messages: [],
    send(value) { this.messages.push(JSON.parse(value)); },
    close() {}, once() {},
  };
}
function last(ws) { return ws.messages.at(-1); }
function keyMaterial() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  return { pair, ...parseIdentityPublicKey(encoded) };
}
function sign(pair, text) {
  return crypto.sign("sha256", Buffer.from(text, "utf8"), pair.privateKey).toString("base64url");
}
const rnd = () => crypto.randomBytes(32).toString("base64url");

(async () => {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-call-compat-"));
  const now = 2_000_000_000;
  const registry = createIdentityRegistry({ file: path.join(root, "reg.json"), now: () => now });
  const pushes = [];
  const fcm = {
    isInitialized: () => true,
    sendAuthenticatedCallInvitePush: async (token, payload) => { pushes.push({ token, payload }); return true; },
  };
  const ctx = buildContext({
    identityRegistry: registry, identityProtocolMode: "enforce", nowSeconds: () => now, fcm,
    identityMigrationRoutes: { size: 0, get: () => null },
    pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
    getIceServers: () => [{ urls: "stun:synthetic.invalid" }],
    ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
    saveFcmTokens: () => true,
  });

  // Real authenticated registration; callProtocols is the advisory claim under test.
  function register(connId, key, callProtocols) {
    const ws = mockWs();
    ctx.clients.set(connId, { ws, clientId: null, lastSeen: Date.now() });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(ws, connId, { protocol: 2, clientId: key.identityId,
      identityId: key.identityId, publicKey: key.publicKeyBase64Url });
    const challenge = last(ws);
    const complete = { challengeId: challenge.challengeId, signature: sign(key.pair, challenge.challenge) };
    if (callProtocols !== undefined) complete.callProtocols = callProtocols;
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(ws, connId, complete);
    assert.strictEqual(last(ws).type, "REGISTERED");
    return ws;
  }
  function invite(ws, connId, from, to, protocol, signer = from.pair) {
    const fields = { sessionId: crypto.randomUUID(), fromClientId: from.identityId,
      fromIdentityId: from.identityId, to: to.identityId, ephemeralPublicKey: rnd(),
      issuedAt: now, nonce: rnd(), protocol };
    ctx.handlers.CALL_INVITE(ws, connId, { protocol, sessionId: fields.sessionId, to: fields.to,
      ephemeralPublicKey: fields.ephemeralPublicKey, issuedAt: now, nonce: fields.nonce,
      signature: sign(signer, callInviteTranscript(fields)) });
    return fields.sessionId;
  }

  try {
    const alice = keyMaterial(); // new client (v3)
    const bob = keyMaterial();   // new client (v3)
    const carol = keyMaterial(); // old client, no capability field
    const dave = keyMaterial();  // old client, no capability field
    const eve = keyMaterial();   // malformed claims
    const wsAlice = register("a", alice, [3]);
    const wsBob = register("b", bob, [3]);
    const wsCarol = register("c", carol);
    const wsDave = register("d", dave);

    // v3 -> known v3: forwarded.
    let sid = invite(wsAlice, "a", alice, bob, 3);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsBob).type, "CALL_INVITE");
    assert.strictEqual(last(wsBob).protocol, 3);
    assert.strictEqual(ctx.routingTable.get(sid).protocol, 3);

    // v3 -> known legacy: explicit update-required, nothing forwarded/pushed/stored.
    ctx.fcmTokens.set(carol.identityId, "synthetic-token-carol");
    const carolBefore = wsCarol.messages.length;
    sid = invite(wsAlice, "a", alice, carol, 3);
    assert.strictEqual(last(wsAlice).type, "ERROR");
    assert.strictEqual(last(wsAlice).error, "peer_media_upgrade_required");
    assert.strictEqual(wsCarol.messages.length, carolBefore);
    assert.strictEqual(ctx.routingTable.has(sid), false);
    assert.strictEqual(pushes.length, 0);

    // A forged signature is rejected by the authoritative check first.
    sid = invite(wsAlice, "a", alice, bob, 3, carol.pair);
    assert.strictEqual(last(wsAlice).error, "invalid_call_signature");

    // v2 -> v2 (old/old) unchanged; v2 -> new is still forwarded (callee rejects visibly).
    sid = invite(wsCarol, "c", carol, dave, 2);
    assert.strictEqual(last(wsCarol).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsDave).protocol, 2);
    sid = invite(wsCarol, "c", carol, alice, 2);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE");
    assert.strictEqual(last(wsAlice).protocol, 2);

    // Allowlisted end reasons: new callee ends a v2 invite with media_upgrade_required.
    ctx.handlers.CALL_END(wsAlice, "a", { sessionId: sid, reason: "media_upgrade_required" });
    assert.deepStrictEqual(last(wsCarol), { type: "CALL_END", sessionId: sid, from: alice.identityId,
      reason: "media_upgrade_required" });
    sid = invite(wsCarol, "c", carol, dave, 2);
    ctx.handlers.CALL_END(wsDave, "d", { sessionId: sid, reason: "<script>evil</script>" });
    assert.strictEqual(last(wsCarol).reason, "user_hangup");
    sid = invite(wsCarol, "c", carol, dave, 2);
    ctx.handlers.CALL_END(wsDave, "d", { sessionId: sid });
    assert.strictEqual(last(wsCarol).reason, "user_hangup");

    // Malformed claims fall back to legacy: no bypass, registration still authenticated.
    for (const claim of [[4], ["3"], [3, 3, 3, 3, 3], {}, "3", [], null]) {
      const ws = register("e", eve, claim);
      assert.strictEqual(ctx.clients.get("e").authVersion, 2);
      assert.deepStrictEqual([...ctx.clients.get("e").callProtocols], [2]);
      invite(wsAlice, "a", alice, eve, 3);
      assert.strictEqual(last(wsAlice).error, "peer_media_upgrade_required", JSON.stringify(claim));
      assert.notStrictEqual(last(ws).type, "CALL_INVITE");
    }

    // Unknown/offline callee: push path preserved, not classified as old.
    const frank = keyMaterial();
    registry.bind({ requestedClientId: frank.identityId, identityId: frank.identityId,
      publicKey: frank.publicKeyBase64Url });
    ctx.fcmTokens.set(frank.identityId, "synthetic-token-frank");
    const pendingNew = invite(wsAlice, "a", alice, frank, 3);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsAlice).pushSent, true);
    assert.strictEqual(pushes.length, 1);
    // Reconnect as a v3 client: pending invite survives.
    register("f", frank, [3]);
    assert.strictEqual(ctx.routingTable.get(pendingNew).state, "INVITE");

    // Offline old callee wakes via push and registers without v3: pending v3 invite
    // ends for the caller with the fixed reason; pending v2 invites are untouched.
    const grace = keyMaterial();
    registry.bind({ requestedClientId: grace.identityId, identityId: grace.identityId,
      publicKey: grace.publicKeyBase64Url });
    ctx.fcmTokens.set(grace.identityId, "synthetic-token-grace");
    const pendingV3 = invite(wsAlice, "a", alice, grace, 3);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    const pendingV2 = invite(wsCarol, "c", carol, grace, 2);
    assert.strictEqual(last(wsCarol).type, "CALL_INVITE_ACK");
    register("g", grace);
    assert.strictEqual(ctx.routingTable.has(pendingV3), false);
    assert.deepStrictEqual(last(wsAlice), { type: "CALL_END", sessionId: pendingV3,
      from: grace.identityId, reason: "media_upgrade_required" });
    assert.strictEqual(ctx.routingTable.get(pendingV2).state, "INVITE");

    console.log("call_compatibility.test.js: PASS");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
})().catch(error => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
