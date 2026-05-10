"use strict";

/**
 * WS Handler Integration Tests
 *
 * Tests actual message processing through the modular handler system.
 * No real WebSocket server started — uses mock ws objects and a real ctx built
 * via buildContext() with in-memory stubs for external services.
 *
 * Run: node src/__tests__/handlers.test.js
 */

let passed = 0;
let failed = 0;

function assert(condition, label) {
  if (condition) {
    console.log("  ✓", label);
    passed++;
  } else {
    console.error("  ✗", label);
    failed++;
  }
}

function assertThrows(fn, label) {
  try {
    fn();
    console.error("  ✗", label, "(expected throw but got none)");
    failed++;
  } catch {
    console.log("  ✓", label);
    passed++;
  }
}

// --- State isolation ---
// state.js is a module singleton — clear Maps between suites to prevent bleed-through.
function clearState() {
  const state = require("../state");
  for (const [k, v] of Object.entries(state)) {
    if (v instanceof Map) v.clear();
    else if (Array.isArray(v)) v.splice(0);
  }
  const { fcmTokens } = require("../services/fcm_store");
  fcmTokens.clear();
  const { activationCodes } = require("../services/activation_store");
  activationCodes.splice(0);
  const { walletMappings } = require("../services/wallet_store");
  walletMappings.splice(0);
}

// --- Mock factories ---

function mockWs() {
  const ws = { readyState: 1, messages: [], closed: false };
  ws.send = (data) => ws.messages.push(JSON.parse(data));
  ws.close = (code, reason) => { ws.closed = true; ws.closeCode = code; ws.closeReason = reason; };
  return ws;
}

function lastMsg(ws) {
  return ws.messages[ws.messages.length - 1];
}

function buildCtx() {
  const { buildContext } = require("../context");

  const mockFcm = {
    isInitialized: () => false,
    initFcm: () => {},
    sendCallInvitePush: () => {},
    sendDataMessage: () => {},
  };
  const mockPkd = { registerKey: () => ({ keyId: "k1", publicKey: "pk", created: 0 }), getKey: () => null };
  const mockSubscriptions = {
    verifySubscription: () => ({ tier: "free", expiresAt: 0 }),
    getSubscription: () => null,
  };
  const mockCustomIds = { resolve: () => null };
  const mockLicenses = { getStatus: () => ({}), getCurrentPrice: () => null };
  const mockRateLimit = { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} };
  const mockHb = { start: () => {}, updateClient: () => {}, stop: () => {} };
  const giftCodes = new Map();
  const saveGiftCodes = () => {};

  const getIceServers = () => [{ urls: "stun:stun.l.google.com:19302" }];
  const ADMIN_API_KEY = "test-admin-key";
  const ALLOWED_ORIGINS = ["https://stealthx.tech"];
  const CLIENT_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

  return buildContext({
    pkd: mockPkd,
    subscriptions: mockSubscriptions,
    fcm: mockFcm,
    customIds: mockCustomIds,
    licenses: mockLicenses,
    getIceServers,
    ADMIN_API_KEY,
    ALLOWED_ORIGINS,
    CLIENT_ID_REGEX,
    rateLimit: mockRateLimit,
    hb: mockHb,
    giftCodes,
    saveGiftCodes,
  });
}

// ==========================================
// Suite: REGISTER handler
// ==========================================
console.log("\n[Suite] REGISTER handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-001";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: null, ip: "1.2.3.4" });

  // Missing clientId
  ctx.handlers.REGISTER(ws, connId, {});
  assert(lastMsg(ws).type === "ERROR", "missing clientId → ERROR");
  assert(lastMsg(ws).error === "missing_client_id", "correct error code");

  // Invalid clientId format
  ctx.handlers.REGISTER(ws, connId, { clientId: "!!invalid!!" });
  assert(lastMsg(ws).type === "ERROR", "invalid clientId format → ERROR");
  assert(lastMsg(ws).error === "invalid_client_id", "correct error code");

  // Valid REGISTER
  ctx.handlers.REGISTER(ws, connId, { clientId: "alice" });
  const reg = lastMsg(ws);
  assert(reg.type === "REGISTERED", "valid REGISTER → REGISTERED");
  assert(reg.clientId === "alice", "clientId echoed back");
  assert(Array.isArray(reg.iceServers), "iceServers array included");
  assert(reg.iceServers.length > 0, "iceServers not empty");

  // State updates
  assert(ctx.clientIds.get("alice") === connId, "clientIds Map updated");
  assert(ctx.clients.get(connId).clientId === "alice", "client.clientId set");

  // Supersede: second conn registers same clientId
  const ws2 = mockWs();
  const connId2 = "conn-002";
  ctx.clients.set(connId2, { ws: ws2, lastSeen: Date.now(), clientId: null, ip: "1.2.3.5" });
  ctx.handlers.REGISTER(ws2, connId2, { clientId: "alice" });
  assert(ws.closed, "old connection closed on supersede");
  assert(lastMsg(ws2).type === "REGISTERED", "new connection gets REGISTERED");
  assert(ctx.clientIds.get("alice") === connId2, "clientIds updated to new conn");
}

// ==========================================
// Suite: REGISTER_FCM_TOKEN handler
// ==========================================
console.log("\n[Suite] REGISTER_FCM_TOKEN handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-fcm";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: "bob", ip: "1.2.3.4" });
  ctx.clientIds.set("bob", connId);

  // Missing fcmToken
  ctx.handlers.REGISTER_FCM_TOKEN(ws, connId, {});
  assert(lastMsg(ws).error === "missing_fcm_token", "missing fcmToken → error");

  // Not registered
  const ws2 = mockWs();
  const connId2 = "conn-noreg";
  ctx.clients.set(connId2, { ws: ws2, lastSeen: Date.now(), clientId: null, ip: "1.2.3.6" });
  ctx.handlers.REGISTER_FCM_TOKEN(ws2, connId2, { fcmToken: "tok123" });
  assert(lastMsg(ws2).error === "not_registered", "unregistered → not_registered error");

  // Valid
  ctx.handlers.REGISTER_FCM_TOKEN(ws, connId, { fcmToken: "fcm-token-abc" });
  assert(lastMsg(ws).type === "REGISTER_FCM_TOKEN_ACK", "valid → ACK");
  assert(ctx.fcmTokens.get("bob") === "fcm-token-abc", "fcmTokens Map updated");

  // FCM token cleared on DEREGISTER
  ctx.handlers.DEREGISTER(ws, connId, {});
  assert(!ctx.fcmTokens.has("bob"), "FCM token cleared on DEREGISTER");
  assert(!ctx.clientIds.has("bob"), "clientId removed on DEREGISTER");
}

// ==========================================
// Suite: CALL_INVITE handler
// ==========================================
console.log("\n[Suite] CALL_INVITE handler");
{
  clearState();
  const ctx = buildCtx();

  // Register alice
  const wsA = mockWs();
  const connA = "conn-alice";
  ctx.clients.set(connA, { ws: wsA, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connA);

  // Register bob
  const wsB = mockWs();
  const connB = "conn-bob";
  ctx.clients.set(connB, { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("bob", connB);

  // Not registered → error
  const wsX = mockWs();
  const connX = "conn-x";
  ctx.clients.set(connX, { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "3.3.3.3" });
  ctx.handlers.CALL_INVITE(wsX, connX, { to: "bob" });
  assert(lastMsg(wsX).error === "not_registered", "unregistered caller → not_registered");

  // Missing 'to'
  ctx.handlers.CALL_INVITE(wsA, connA, {});
  assert(lastMsg(wsA).error === "missing_to", "missing 'to' → error");

  // Peer not found (unknown clientId, no FCM)
  ctx.handlers.CALL_INVITE(wsA, connA, { to: "charlie" });
  assert(lastMsg(wsA).error === "peer_not_found", "unknown peer → peer_not_found");

  // Valid call: alice → bob (bob is online)
  ctx.handlers.CALL_INVITE(wsA, connA, { to: "bob", pubKey: "alicePub" });
  const ack = lastMsg(wsA);
  assert(ack.type === "CALL_INVITE_ACK", "caller gets CALL_INVITE_ACK");
  assert(ack.ok === true, "ACK ok=true");
  assert(typeof ack.sessionId === "string" && ack.sessionId.length > 0, "sessionId assigned");

  const inv = lastMsg(wsB);
  assert(inv.type === "CALL_INVITE", "callee receives CALL_INVITE");
  assert(inv.from === "alice", "from = alice");
  assert(inv.pubKey === "alicePub", "pubKey forwarded");

  // Session created in routingTable
  const session = ctx.routingTable.get(ack.sessionId);
  assert(session !== undefined, "session in routingTable");
  assert(session.from === "alice", "session.from = alice");
  assert(session.to === "bob", "session.to = bob");
  assert(session.state === "INVITE", "session.state = INVITE");
}

// ==========================================
// Suite: CALL_ACCEPT + CALL_END handlers
// ==========================================
console.log("\n[Suite] CALL_ACCEPT + CALL_END handlers");
{
  clearState();
  const ctx = buildCtx();
  const wsA = mockWs();
  const wsB = mockWs();
  ctx.clients.set("cA", { ws: wsA, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clients.set("cB", { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("alice", "cA");
  ctx.clientIds.set("bob", "cB");

  // Setup session manually
  const sessionId = "sess-001";
  ctx.routingTable.set(sessionId, { sessionId, from: "alice", to: "bob", state: "INVITE", created: Date.now(), updated: Date.now() });

  // Wrong callee trying to accept
  const wsC = mockWs();
  ctx.clients.set("cC", { ws: wsC, lastSeen: Date.now(), clientId: "charlie", ip: "3.3.3.3" });
  ctx.clientIds.set("charlie", "cC");
  ctx.handlers.CALL_ACCEPT(wsC, "cC", { sessionId });
  assert(lastMsg(wsC).error === "not_callee", "wrong callee → not_callee error");

  // Bob accepts
  ctx.handlers.CALL_ACCEPT(wsB, "cB", { sessionId, pubKey: "bobPub" });
  const acceptAck = lastMsg(wsB);
  assert(acceptAck.type === "CALL_ACCEPT_ACK", "callee gets CALL_ACCEPT_ACK");
  assert(ctx.routingTable.get(sessionId).state === "ACTIVE", "session state → ACTIVE");

  const aliceAccept = lastMsg(wsA);
  assert(aliceAccept.type === "CALL_ACCEPT", "caller receives CALL_ACCEPT");
  assert(aliceAccept.pubKey === "bobPub", "pubKey forwarded to caller");

  // Alice ends call
  ctx.handlers.CALL_END(wsA, "cA", { sessionId });
  assert(lastMsg(wsA).type === "CALL_END_ACK", "CALL_END_ACK sent to ender");
  assert(lastMsg(wsB).type === "CALL_END", "CALL_END forwarded to peer");
  assert(!ctx.routingTable.has(sessionId), "session removed from routingTable");
}

// ==========================================
// Suite: PHONE_LOOKUP handler
// ==========================================
console.log("\n[Suite] PHONE_LOOKUP handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-pl";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connId);

  // Seed a phone → clientId mapping
  const { hashPhone, normalizePhone } = ctx;
  const phone = normalizePhone("+4915123456789");
  ctx.phoneNumbers.set(phone, "bob");
  ctx.phoneHashes.set(hashPhone(phone), "bob");

  // Not registered
  const wsX = mockWs();
  ctx.clients.set("cX", { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "9.9.9.9" });
  ctx.handlers.PHONE_LOOKUP(wsX, "cX", { phoneNumber: "+4915123456789" });
  assert(lastMsg(wsX).error === "not_registered", "unregistered → not_registered");

  // Lookup registered phone
  ctx.handlers.PHONE_LOOKUP(ws, connId, { phoneNumber: "+4915123456789" });
  const res = lastMsg(ws);
  assert(res.type === "PHONE_LOOKUP_RESULT", "PHONE_LOOKUP_RESULT returned");
  assert(res.clientId === "bob", "resolved to correct clientId");
  assert(res.online === false, "bob not in clientIds → online=false");

  // bob registers
  const wsB = mockWs();
  ctx.clients.set("cB", { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("bob", "cB");

  ctx.handlers.PHONE_LOOKUP(ws, connId, { phoneNumber: "+4915123456789" });
  const res2 = lastMsg(ws);
  assert(res2.online === true, "bob online after registration");
}

// ==========================================
// Results
// ==========================================
console.log(`\n${"─".repeat(50)}`);
const total = passed + failed;
console.log(`handlers.test: ${passed}/${total} passed${failed > 0 ? ` (${failed} FAILED)` : " ✅"}`);
if (failed > 0) process.exit(1);
