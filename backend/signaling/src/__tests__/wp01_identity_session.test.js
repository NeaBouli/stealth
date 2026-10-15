"use strict";

// Friend audit WP-01 (MAIN-07 / FB-01): unproven sx_ IDENTIFY must not displace a live owner
// or inherit identity-v2 authentication. Synthetic ids only.
const assert = require("assert");
const { buildContext } = require("../context");

function mockWs() {
  return { readyState: 1, messages: [], closed: false,
    send(v) { this.messages.push(JSON.parse(v)); },
    close() { this.closed = true; this.readyState = 3; }, once() {} };
}
const last = ws => ws.messages.at(-1);
function resetState() {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
}

resetState();
const ctx = buildContext({
  identityRegistry: { resolve: () => null, getIdentity: () => null }, identityProtocolMode: "enforce",
  pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
  getIceServers: () => [], ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
  CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
  rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
  hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
  saveFcmTokens: () => true,
});
const SX = "sx_ABCDEFGHJ";
const connect = (id) => { const ws = mockWs(); ctx.clients.set(id, { ws, lastSeen: Date.now(), clientId: null }); return ws; };

// 1. Owner binds; a second socket cannot displace the live owner.
const owner = connect("c-owner");
ctx.handlers.IDENTIFY(owner, "c-owner", { sxId: SX });
assert.strictEqual(last(owner).type, "IDENTIFY_ACK");
const claimant = connect("c-claim");
ctx.handlers.IDENTIFY(claimant, "c-claim", { sxId: SX });
assert.strictEqual(last(claimant).error, "sx_id_in_use", "live owner is not displaced");
assert.strictEqual(ctx.clientIds.get(SX), "c-owner");
assert.strictEqual(owner.closed, false);

// 2. A stale owner (no heartbeat for > 15 s) may be taken over (legit reconnect).
ctx.clients.get("c-owner").lastSeen = Date.now() - 16_000;
ctx.handlers.IDENTIFY(claimant, "c-claim", { sxId: SX });
assert.strictEqual(last(claimant).type, "IDENTIFY_ACK", "stale binding can be taken over");
assert.strictEqual(ctx.clientIds.get(SX), "c-claim");
assert.strictEqual(owner.closed, true);

// 3. A closed owner socket may be taken over immediately.
const SX2 = "sx_KLMNPQRST";
const a = connect("c-a"); ctx.handlers.IDENTIFY(a, "c-a", { sxId: SX2 }); a.close();
const b = connect("c-b"); ctx.handlers.IDENTIFY(b, "c-b", { sxId: SX2 });
assert.strictEqual(last(b).type, "IDENTIFY_ACK", "closed binding can be taken over");

// 4. IDENTIFY on an identity-v2 socket drops its authenticated state.
const ID = "sc-" + "A".repeat(43);
const v2 = connect("c-v2");
Object.assign(ctx.clients.get("c-v2"), { clientId: ID, authVersion: 2, identityId: ID, identityPublicKey: "pk" });
ctx.clientIds.set(ID, "c-v2");
ctx.handlers.REGISTER_FCM_TOKEN(v2, "c-v2", { fcmToken: "synthetic-token-".padEnd(40, "x") });
assert.notStrictEqual(last(v2).type, "ERROR", "own authenticated identity may register a push token");
const SX3 = "sx_UVWXYZabc";
ctx.handlers.IDENTIFY(v2, "c-v2", { sxId: SX3 });
const after = ctx.clients.get("c-v2");
assert.strictEqual(after.authVersion, undefined);
assert.strictEqual(after.identityId, null);
ctx.handlers.REGISTER_FCM_TOKEN(v2, "c-v2", { fcmToken: "attacker-token-".padEnd(40, "y") });
assert.strictEqual(last(v2).error, "not_authenticated", "sx_ binding cannot register push tokens");
assert.strictEqual(require("../services/fcm_store").fcmTokens.has(SX3), false);

// 5. Even with stale v2 fields, a socket whose clientId is not its identity is refused.
const mixed = connect("c-mixed");
Object.assign(ctx.clients.get("c-mixed"), { clientId: "sx_mnopqrstu", authVersion: 2, identityId: ID, identityPublicKey: "pk" });
ctx.handlers.REGISTER_FCM_TOKEN(mixed, "c-mixed", { fcmToken: "mixed-token-".padEnd(40, "z") });
assert.strictEqual(last(mixed).error, "identity_protocol_required");
ctx.handlers.CALL_INVITE(mixed, "c-mixed", { protocol: 2, sessionId: "8f0e2c1a-1b2c-4d3e-8f4a-5b6c7d8e9f00",
  to: ID, ephemeralPublicKey: "x", issuedAt: 1, nonce: "n", signature: "s" });
assert.ok(["not_authenticated", "identity_protocol_required"].includes(last(mixed).error),
  "v2 call handlers require clientId === identityId");
assert.strictEqual(ctx.routingTable.has("8f0e2c1a-1b2c-4d3e-8f4a-5b6c7d8e9f00"), false, "no call session created");

console.log("wp01_identity_session.test.js: PASS");
