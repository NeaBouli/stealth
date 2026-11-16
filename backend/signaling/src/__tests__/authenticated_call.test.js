"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const {
  callInviteTranscript,
  callAcceptTranscript,
  parseIdentityPublicKey,
  transcriptDigest,
} = require("../security/identity_protocol");

function resetState() {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
}

function mockWs() {
  return {
    readyState: 1,
    messages: [],
    send(value) { this.messages.push(JSON.parse(value)); },
  };
}

function last(ws) { return ws.messages.at(-1); }

function keyMaterial() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  return { pair, ...parseIdentityPublicKey(encoded) };
}

function sign(pair, transcript) {
  return crypto.sign("sha256", Buffer.from(transcript, "utf8"), pair.privateKey).toString("base64url");
}

function randomKey() { return crypto.randomBytes(32).toString("base64url"); }
function randomNonce() { return crypto.randomBytes(16).toString("base64url"); }

function makeContext(registry, fcm, nowSeconds) {
  return buildContext({
    identityRegistry: registry,
    identityProtocolMode: "enforce",
    nowSeconds,
    fcm,
    pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
    getIceServers: () => [{ urls: "stun:synthetic.invalid" }],
    ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
  });
}

function attach(ctx, connId, identity) {
  const ws = mockWs();
  ctx.clients.set(connId, {
    ws,
    clientId: identity.identityId,
    identityId: identity.identityId,
    identityPublicKey: identity.publicKeyBase64Url,
    authVersion: 2,
    lastSeen: Date.now(),
  });
  ctx.clientIds.set(identity.identityId, connId);
  return ws;
}

(async () => {
  resetState();
  const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-call-v2-"));
  const registry = createIdentityRegistry({
    file: path.join(root, "identity_registry.json"),
    now: () => 2_000_000_000,
  });
  const now = 2_000_000_000;
  const pushes = [];
  const fcm = {
    isInitialized: () => true,
    sendAuthenticatedCallInvitePush: async (token, payload) => {
      pushes.push({ token, payload });
      return true;
    },
    sendCallInvitePush: () => { throw new Error("legacy push must not be used"); },
  };
  const ctx = makeContext(registry, fcm, () => now);

  try {
    const alice = keyMaterial();
    const bob = keyMaterial();
    const mallory = keyMaterial();
    registry.bind({ requestedClientId: alice.identityId, identityId: alice.identityId,
      publicKey: alice.publicKeyBase64Url });
    registry.bind({ requestedClientId: "bob-legacy", identityId: bob.identityId,
      publicKey: bob.publicKeyBase64Url, migrationAuthorized: true });

    const wsAlice = attach(ctx, "conn-alice", alice);
    const wsBob = attach(ctx, "conn-bob", bob);
    const wsMallory = attach(ctx, "conn-mallory", mallory);
    const sessionId = crypto.randomUUID();
    const inviteFields = {
      sessionId,
      fromClientId: alice.identityId,
      fromIdentityId: alice.identityId,
      to: "bob-legacy",
      ephemeralPublicKey: randomKey(),
      issuedAt: now,
      nonce: randomNonce(),
    };
    const inviteTranscript = callInviteTranscript(inviteFields);
    const invite = {
      protocol: 2,
      sessionId,
      to: inviteFields.to,
      ephemeralPublicKey: inviteFields.ephemeralPublicKey,
      issuedAt: now,
      nonce: inviteFields.nonce,
      signature: sign(alice.pair, inviteTranscript),
    };

    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", invite);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsAlice).to, bob.identityId);
    assert.strictEqual(last(wsBob).type, "CALL_INVITE");
    assert.strictEqual(last(wsBob).fromIdentityId, alice.identityId);
    assert.strictEqual(last(wsBob).inviteDigest, transcriptDigest(inviteTranscript));
    assert.strictEqual(ctx.routingTable.get(sessionId).state, "INVITE");
    // Old-to-old clients keep the unchanged protocol-2 server path.
    assert.strictEqual(last(wsBob).protocol, 2);
    assert.strictEqual(last(wsAlice).protocol, 2);
    assert.strictEqual(ctx.routingTable.get(sessionId).protocol, 2);

    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", invite);
    assert.strictEqual(last(wsAlice).error, "invalid_call_invite");

    const tamperedSessionId = crypto.randomUUID();
    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
      ...invite,
      sessionId: tamperedSessionId,
      ephemeralPublicKey: randomKey(),
    });
    assert.strictEqual(last(wsAlice).error, "invalid_call_signature");
    assert.strictEqual(ctx.routingTable.has(tamperedSessionId), false);

    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
      to: bob.identityId,
      sessionId: crypto.randomUUID(),
      pubKey: "unsigned-legacy-key",
    });
    assert.strictEqual(last(wsAlice).error, "invalid_call_invite");

    ctx.handlers.CALL_ACCEPT(wsMallory, "conn-mallory", { protocol: 2, sessionId });
    assert.strictEqual(last(wsMallory).error, "not_callee");

    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", {
      protocol: 2,
      sessionId,
      inviteDigest: randomKey(),
      toIdentityId: alice.identityId,
      ephemeralPublicKey: randomKey(),
      issuedAt: now,
      nonce: randomNonce(),
      signature: sign(bob.pair, "securecall-invalid"),
    });
    assert.strictEqual(last(wsBob).error, "invalid_call_accept");
    assert.strictEqual(ctx.routingTable.get(sessionId).state, "INVITE");

    const acceptFields = {
      sessionId,
      inviteDigest: transcriptDigest(inviteTranscript),
      fromClientId: bob.identityId,
      fromIdentityId: bob.identityId,
      toIdentityId: alice.identityId,
      ephemeralPublicKey: randomKey(),
      issuedAt: now,
      nonce: randomNonce(),
    };
    const acceptTranscript = callAcceptTranscript(acceptFields);
    const accept = {
      protocol: 2,
      sessionId,
      inviteDigest: acceptFields.inviteDigest,
      toIdentityId: acceptFields.toIdentityId,
      ephemeralPublicKey: acceptFields.ephemeralPublicKey,
      issuedAt: now,
      nonce: acceptFields.nonce,
      signature: sign(bob.pair, acceptTranscript),
    };
    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", accept);
    assert.strictEqual(last(wsBob).type, "CALL_ACCEPT_ACK");
    assert.strictEqual(last(wsAlice).type, "CALL_ACCEPT");
    assert.strictEqual(last(wsAlice).acceptDigest, transcriptDigest(acceptTranscript));
    assert.strictEqual(ctx.routingTable.get(sessionId).state, "KEY_CONFIRM");

    ctx.handlers.WEBRTC_OFFER(wsAlice, "conn-alice", { sessionId, sdp: "synthetic-sdp" });
    assert.strictEqual(last(wsAlice).error, "session_not_active");

    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", accept);
    assert.strictEqual(last(wsBob).error, "invalid_session_state");

    const callerConfirmation = randomKey();
    ctx.handlers.CALL_KEY_CONFIRM(wsAlice, "conn-alice", {
      protocol: 2, sessionId, role: "caller", confirmation: callerConfirmation,
    });
    assert.strictEqual(last(wsAlice).type, "CALL_KEY_CONFIRM_ACK");
    assert.strictEqual(last(wsAlice).active, false);
    assert.strictEqual(last(wsBob).confirmation, callerConfirmation);

    ctx.handlers.CALL_KEY_CONFIRM(wsAlice, "conn-alice", {
      protocol: 2, sessionId, role: "caller", confirmation: callerConfirmation,
    });
    assert.strictEqual(last(wsAlice).error, "key_confirmation_replayed");

    const calleeConfirmation = randomKey();
    ctx.handlers.CALL_KEY_CONFIRM(wsBob, "conn-bob", {
      protocol: 2, sessionId, role: "callee", confirmation: calleeConfirmation,
    });
    assert.strictEqual(last(wsBob).type, "CALL_KEY_CONFIRM_ACK");
    assert.strictEqual(last(wsBob).active, true);
    assert.strictEqual(ctx.routingTable.get(sessionId).state, "ACTIVE");

    ctx.handlers.WEBRTC_OFFER(wsMallory, "conn-mallory", { sessionId, sdp: "synthetic-sdp" });
    assert.strictEqual(last(wsMallory).error, "not_participant");

    ctx.handlers.CALL_KEY_CONFIRM(wsMallory, "conn-mallory", {
      protocol: 2, sessionId, role: "caller", confirmation: randomKey(),
    });
    assert.strictEqual(last(wsMallory).error, "invalid_key_confirmation");

    ctx.handlers.CALL_END(wsAlice, "conn-alice", { sessionId });
    assert.strictEqual(last(wsAlice).type, "CALL_END_ACK");
    assert.strictEqual(ctx.routingTable.has(sessionId), false);

    // ---- STX-24 protocol 3: media version bound into the signed invite and accept ----
    const v3SessionId = crypto.randomUUID();
    const v3InviteFields = {
      ...inviteFields, sessionId: v3SessionId, ephemeralPublicKey: randomKey(), nonce: randomNonce(),
    };
    const v3InviteTranscript = callInviteTranscript({ ...v3InviteFields, protocol: 3 });
    const v2RelabelTranscript = callInviteTranscript({ ...v3InviteFields, protocol: 2 });
    assert.ok(v3InviteTranscript.startsWith("securecall-call-invite-v3\n"));
    assert.ok(v3InviteTranscript.endsWith("\nsecurecall-media-frame-v3"));
    assert.throws(() => callInviteTranscript({ ...v3InviteFields, protocol: 4 }));
    const v3Invite = {
      protocol: 3,
      sessionId: v3SessionId,
      to: v3InviteFields.to,
      ephemeralPublicKey: v3InviteFields.ephemeralPublicKey,
      issuedAt: now,
      nonce: v3InviteFields.nonce,
      signature: sign(alice.pair, v3InviteTranscript),
    };
    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", { ...v3Invite, protocol: 2 });
    assert.strictEqual(last(wsAlice).error, "invalid_call_signature");
    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", { ...v3Invite, signature: sign(alice.pair, v2RelabelTranscript) });
    assert.strictEqual(last(wsAlice).error, "invalid_call_signature");
    for (const protocol of [4, "3", undefined]) {
      ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", { ...v3Invite, protocol });
      assert.strictEqual(last(wsAlice).error, "invalid_call_invite");
    }
    assert.strictEqual(ctx.routingTable.has(v3SessionId), false);

    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", v3Invite);
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsAlice).protocol, 3);
    assert.strictEqual(last(wsBob).type, "CALL_INVITE");
    assert.strictEqual(last(wsBob).protocol, 3);
    assert.strictEqual(last(wsBob).inviteDigest, transcriptDigest(v3InviteTranscript));
    assert.strictEqual(ctx.routingTable.get(v3SessionId).protocol, 3);

    const v3AcceptFields = {
      sessionId: v3SessionId,
      inviteDigest: transcriptDigest(v3InviteTranscript),
      fromClientId: bob.identityId,
      fromIdentityId: bob.identityId,
      toIdentityId: alice.identityId,
      ephemeralPublicKey: randomKey(),
      issuedAt: now,
      nonce: randomNonce(),
    };
    const v3AcceptTranscript = callAcceptTranscript({ ...v3AcceptFields, protocol: 3 });
    assert.ok(v3AcceptTranscript.startsWith("securecall-call-accept-v3\n"));
    assert.ok(v3AcceptTranscript.endsWith("\nsecurecall-media-frame-v3"));
    const v3Accept = {
      protocol: 3,
      sessionId: v3SessionId,
      inviteDigest: v3AcceptFields.inviteDigest,
      toIdentityId: alice.identityId,
      ephemeralPublicKey: v3AcceptFields.ephemeralPublicKey,
      issuedAt: now,
      nonce: v3AcceptFields.nonce,
      signature: sign(bob.pair, v3AcceptTranscript),
    };
    // Downgrade of the accept to the invite's non-matching protocol is rejected.
    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", {
      ...v3Accept, protocol: 2,
      signature: sign(bob.pair, callAcceptTranscript({ ...v3AcceptFields, protocol: 2 })),
    });
    assert.strictEqual(last(wsBob).error, "invalid_call_accept");
    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", {
      ...v3Accept, signature: sign(bob.pair, callAcceptTranscript({ ...v3AcceptFields, protocol: 2 })),
    });
    assert.strictEqual(last(wsBob).error, "invalid_call_signature");
    assert.strictEqual(ctx.routingTable.get(v3SessionId).state, "INVITE");

    ctx.handlers.CALL_ACCEPT(wsBob, "conn-bob", v3Accept);
    assert.strictEqual(last(wsBob).type, "CALL_ACCEPT_ACK");
    assert.strictEqual(last(wsBob).protocol, 3);
    assert.strictEqual(last(wsAlice).type, "CALL_ACCEPT");
    assert.strictEqual(last(wsAlice).protocol, 3);
    assert.strictEqual(last(wsAlice).acceptDigest, transcriptDigest(v3AcceptTranscript));
    assert.strictEqual(ctx.routingTable.get(v3SessionId).state, "KEY_CONFIRM");

    ctx.handlers.CALL_KEY_CONFIRM(wsAlice, "conn-alice", {
      protocol: 2, sessionId: v3SessionId, role: "caller", confirmation: randomKey(),
    });
    assert.strictEqual(last(wsAlice).error, "invalid_key_confirmation");
    assert.strictEqual(ctx.routingTable.get(v3SessionId).callerConfirmed, false);
    const v3CallerConfirmation = randomKey();
    ctx.handlers.CALL_KEY_CONFIRM(wsAlice, "conn-alice", {
      protocol: 3, sessionId: v3SessionId, role: "caller", confirmation: v3CallerConfirmation,
    });
    assert.strictEqual(last(wsAlice).type, "CALL_KEY_CONFIRM_ACK");
    assert.strictEqual(last(wsAlice).protocol, 3);
    assert.strictEqual(last(wsBob).type, "CALL_KEY_CONFIRM");
    assert.strictEqual(last(wsBob).protocol, 3);
    assert.strictEqual(last(wsBob).confirmation, v3CallerConfirmation);
    ctx.handlers.CALL_KEY_CONFIRM(wsBob, "conn-bob", {
      protocol: 3, sessionId: v3SessionId, role: "callee", confirmation: randomKey(),
    });
    assert.strictEqual(last(wsBob).active, true);
    assert.strictEqual(ctx.routingTable.get(v3SessionId).state, "ACTIVE");
    ctx.handlers.CALL_END(wsBob, "conn-bob", { sessionId: v3SessionId });
    assert.strictEqual(ctx.routingTable.has(v3SessionId), false);

    ctx.clientIds.delete(bob.identityId);
    ctx.clients.delete("conn-bob");
    ctx.fcmTokens.set("bob-legacy", "synthetic-fcm-token-that-is-long-enough");
    const pushFields = {
      sessionId: crypto.randomUUID(),
      fromClientId: alice.identityId,
      fromIdentityId: alice.identityId,
      to: bob.identityId,
      ephemeralPublicKey: randomKey(),
      issuedAt: now,
      nonce: randomNonce(),
    };
    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
      protocol: 2,
      sessionId: pushFields.sessionId,
      to: pushFields.to,
      ephemeralPublicKey: pushFields.ephemeralPublicKey,
      issuedAt: now,
      nonce: pushFields.nonce,
      signature: sign(alice.pair, callInviteTranscript(pushFields)),
    });
    assert.strictEqual(last(wsAlice).type, "CALL_INVITE_ACK");
    assert.strictEqual(last(wsAlice).pushSent, true);
    assert.strictEqual(pushes.length, 1);
    assert.strictEqual(pushes[0].payload.fromIdentityId, alice.identityId);
    assert.strictEqual(pushes[0].payload.requestedTo, bob.identityId);

    // Stale invites (outside the 120s skew window, both directions) are rejected
    // even with a valid signature, and neither route nor push is touched.
    for (const issuedAt of [now - 121, now + 121]) {
      const staleFields = { ...pushFields, sessionId: crypto.randomUUID(), nonce: randomNonce(), issuedAt };
      ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
        protocol: 2, sessionId: staleFields.sessionId, to: staleFields.to,
        ephemeralPublicKey: staleFields.ephemeralPublicKey, issuedAt, nonce: staleFields.nonce,
        signature: sign(alice.pair, callInviteTranscript(staleFields)),
      });
      assert.strictEqual(last(wsAlice).error, "stale_call_invite");
      assert.strictEqual(ctx.routingTable.has(staleFields.sessionId), false);
    }
    assert.strictEqual(pushes.length, 1);

    // Replaying the already pushed invite is rejected without a second push.
    ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
      protocol: 2, sessionId: pushFields.sessionId, to: pushFields.to,
      ephemeralPublicKey: pushFields.ephemeralPublicKey, issuedAt: now, nonce: pushFields.nonce,
      signature: sign(alice.pair, callInviteTranscript(pushFields)),
    });
    assert.strictEqual(last(wsAlice).error, "invalid_call_invite");
    assert.strictEqual(pushes.length, 1);

    // FCM negatives: provider uninitialised or no stored route -> no push, no
    // session, peer_not_found for an offline callee.
    const sendNegativeInvite = () => {
      const fields = { ...pushFields, sessionId: crypto.randomUUID(), nonce: randomNonce() };
      ctx.handlers.CALL_INVITE(wsAlice, "conn-alice", {
        protocol: 2, sessionId: fields.sessionId, to: fields.to,
        ephemeralPublicKey: fields.ephemeralPublicKey, issuedAt: now, nonce: fields.nonce,
        signature: sign(alice.pair, callInviteTranscript(fields)),
      });
      assert.strictEqual(last(wsAlice).error, "peer_not_found");
      assert.strictEqual(ctx.routingTable.has(fields.sessionId), false);
    };
    fcm.isInitialized = () => false;
    sendNegativeInvite();
    fcm.isInitialized = () => true;
    ctx.fcmTokens.delete("bob-legacy");
    sendNegativeInvite();
    assert.strictEqual(pushes.length, 1);

    console.log("authenticated_call.test.js: PASS");
  } finally {
    fs.rmSync(root, { recursive: true, force: true });
  }
})().catch(error => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
