"use strict";

/**
 * Subscription + WebRTC Handler Integration Tests
 *
 * Tests message processing for subscription.js and webrtc.js handlers.
 * Uses mock ws objects and a real ctx built via buildContext() with in-memory stubs.
 * VERIFY_IFR_LOCK async path is not tested here — requires a live Ethereum RPC.
 *
 * Run: node src/__tests__/subscription_webrtc.test.js
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

function mockWs() {
  const ws = { readyState: 1, messages: [], closed: false };
  ws.send = (data) => ws.messages.push(JSON.parse(data));
  ws.close = () => { ws.closed = true; };
  return ws;
}

function lastMsg(ws) {
  return ws.messages[ws.messages.length - 1];
}

function buildCtx() {
  const { buildContext } = require("../context");

  const mockFcm = {
    isInitialized: () => false,
    sendCallInvitePush: () => {},
    sendDataMessage: () => {},
  };
  const mockPkd = { registerKey: () => ({ keyId: "k1", publicKey: "pk", created: 0 }), getKey: () => null };
  const mockSubscriptions = {
    verifySubscription: () => ({ tier: "pro", expiresAt: 9999999999 }),
    getSubscription: () => null,
  };
  const mockCustomIds = { resolve: () => null };
  const mockLicenses = { getStatus: () => ({}), getCurrentPrice: () => null };
  const mockRateLimit = { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} };
  const mockHb = { start: () => {}, updateClient: () => {}, stop: () => {} };
  const giftCodes = new Map();
  const saveGiftCodes = () => {};

  const ICE_SERVERS = [{ urls: "stun:stun.l.google.com:19302" }];
  const ADMIN_API_KEY = "test-admin-key";
  const ALLOWED_ORIGINS = ["https://stealthx.tech"];
  const CLIENT_ID_REGEX = /^[a-zA-Z0-9_-]{1,64}$/;

  return buildContext({
    pkd: mockPkd,
    subscriptions: mockSubscriptions,
    fcm: mockFcm,
    customIds: mockCustomIds,
    licenses: mockLicenses,
    ICE_SERVERS,
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
// Suite: SUBSCRIPTION_VERIFY handler
// ==========================================
console.log("\n[Suite] SUBSCRIPTION_VERIFY handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-sv";

  // Not registered
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: null, ip: "1.1.1.1" });
  ctx.handlers.SUBSCRIPTION_VERIFY(ws, connId, { purchaseToken: "tok", productId: "pro" });
  assert(lastMsg(ws).error === "not_registered", "unregistered → not_registered");

  // Register
  ctx.clients.get(connId).clientId = "alice";
  ctx.clientIds.set("alice", connId);

  // Missing purchaseToken
  ctx.handlers.SUBSCRIPTION_VERIFY(ws, connId, { productId: "pro" });
  assert(lastMsg(ws).type === "ERROR", "missing purchaseToken → ERROR");

  // Missing productId
  ctx.handlers.SUBSCRIPTION_VERIFY(ws, connId, { purchaseToken: "tok" });
  assert(lastMsg(ws).type === "ERROR", "missing productId → ERROR");

  // Valid → mock returns tier=pro
  ctx.handlers.SUBSCRIPTION_VERIFY(ws, connId, { purchaseToken: "tok123", productId: "pro_monthly" });
  const ack = lastMsg(ws);
  assert(ack.type === "SUBSCRIPTION_VERIFY_ACK", "valid → SUBSCRIPTION_VERIFY_ACK");
  assert(ack.tier === "pro", "tier from mock subscription service");
  assert(ack.expiresAt === 9999999999, "expiresAt forwarded");
}

// ==========================================
// Suite: ACTIVATE_CODE handler
// ==========================================
console.log("\n[Suite] ACTIVATE_CODE handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-ac";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connId);

  // Missing code
  ctx.handlers.ACTIVATE_CODE(ws, connId, {});
  const r1 = lastMsg(ws);
  assert(r1.type === "ACTIVATE_CODE_RESULT", "missing code → ACTIVATE_CODE_RESULT");
  assert(r1.success === false && r1.error === "missing_code", "error=missing_code");

  // Blocked BETA code
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "BETA-PRO0-2026" });
  const r2 = lastMsg(ws);
  assert(r2.success === false && r2.error === "expired", "blocked BETA code → expired");

  // Invalid code (not in activationCodes, not in giftCodes)
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "NOPE-1111-XXXX" });
  const r3 = lastMsg(ws);
  assert(r3.success === false && r3.error === "invalid", "unknown code → invalid");

  // Gift code — valid
  ctx.giftCodes.set("GIFT-GOOD-2026", {
    tier: "pro",
    used: false,
    expires: new Date(Date.now() + 86400000).toISOString(),
  });
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "GIFT-GOOD-2026" });
  const r4 = lastMsg(ws);
  assert(r4.success === true && r4.tier === "pro", "valid gift code → success + tier");

  // Gift code — already used
  ctx.giftCodes.set("GIFT-USED-2026", {
    tier: "pro",
    used: true,
    expires: new Date(Date.now() + 86400000).toISOString(),
  });
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "GIFT-USED-2026" });
  const r5 = lastMsg(ws);
  assert(r5.success === false && r5.error === "already_used", "used gift code → already_used");

  // Gift code — expired
  ctx.giftCodes.set("GIFT-EXP-2026", {
    tier: "pro",
    used: false,
    expires: new Date(Date.now() - 1000).toISOString(),
  });
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "GIFT-EXP-2026" });
  const r6 = lastMsg(ws);
  assert(r6.success === false && r6.error === "expired", "expired gift code → expired");

  // Activation code — valid first use
  ctx.activationCodes.push({ code: "TEAM-ABCD-1234", tier: "pro", maxUses: 3, usedBy: [], currentUses: 0 });
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "TEAM-ABCD-1234" });
  const r7 = lastMsg(ws);
  assert(r7.success === true && r7.tier === "pro", "activation code first use → success");
  assert(r7.slot === 1 && r7.maxSlots === 3, "slot=1, maxSlots=3");
  const entry = ctx.activationCodes.find(c => c.code === "TEAM-ABCD-1234");
  assert(entry.usedBy.includes("alice"), "alice added to usedBy");

  // Activation code — re-activation same device
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "TEAM-ABCD-1234" });
  const r8 = lastMsg(ws);
  assert(r8.success === true, "re-activation same device → success");
  assert(entry.usedBy.filter(u => u === "alice").length === 1, "alice not duplicated in usedBy");

  // Activation code — max devices exceeded
  ctx.activationCodes.push({ code: "FULL-CODE-5678", tier: "basic", maxUses: 1, usedBy: ["bob"], currentUses: 1 });
  ctx.handlers.ACTIVATE_CODE(ws, connId, { code: "FULL-CODE-5678" });
  const r9 = lastMsg(ws);
  assert(r9.success === false && r9.error === "max_devices", "full code → max_devices");
}

// ==========================================
// Suite: VERIFY_IFR_LOCK handler (sync paths only)
// ==========================================
console.log("\n[Suite] VERIFY_IFR_LOCK handler (sync paths)");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-ifr";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connId);

  // Missing wallet
  ctx.handlers.VERIFY_IFR_LOCK(ws, connId, {});
  assert(lastMsg(ws).type === "IFR_LOCK_RESULT", "missing wallet → IFR_LOCK_RESULT");
  assert(lastMsg(ws).success === false && lastMsg(ws).error === "invalid_address", "error=invalid_address");

  // Invalid wallet format (not 0x + 40 hex)
  ctx.handlers.VERIFY_IFR_LOCK(ws, connId, { walletAddress: "not-a-wallet" });
  assert(lastMsg(ws).error === "invalid_address", "non-hex wallet → invalid_address");

  // Short address
  ctx.handlers.VERIFY_IFR_LOCK(ws, connId, { walletAddress: "0xDEAD" });
  assert(lastMsg(ws).error === "invalid_address", "short address → invalid_address");

  // Wallet bound to different client
  ctx.walletMappings.push({ wallet: "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef", clientId: "bob", tier: "pro", lastVerified: Date.now() });
  ctx.handlers.VERIFY_IFR_LOCK(ws, connId, { walletAddress: "0xdeadbeefdeadbeefdeadbeefdeadbeefdeadbeef" });
  assert(lastMsg(ws).error === "wallet_bound", "wallet bound to other client → wallet_bound");
  assert(lastMsg(ws).boundTo.endsWith("..."), "boundTo is truncated");
}

// ==========================================
// Suite: INVITE_ACCEPTED handler
// ==========================================
console.log("\n[Suite] INVITE_ACCEPTED handler");
{
  clearState();
  const ctx = buildCtx();
  const wsA = mockWs();
  const wsB = mockWs();
  const connA = "conn-ia";
  const connB = "conn-ib";

  ctx.clients.set(connA, { ws: wsA, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connA);
  ctx.clients.set(connB, { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("bob", connB);

  // Not registered
  const wsX = mockWs();
  ctx.clients.set("cX", { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "9.9.9.9" });
  ctx.handlers.INVITE_ACCEPTED(wsX, "cX", { inviterSecureId: "bob" });
  assert(lastMsg(wsX).error === "not_registered", "unregistered → not_registered");

  // Missing inviterSecureId
  ctx.handlers.INVITE_ACCEPTED(wsA, connA, {});
  assert(lastMsg(wsA).error === "missing_inviterSecureId", "missing inviterSecureId → error");

  // Valid: alice accepted bob's invite — bob is online
  ctx.handlers.INVITE_ACCEPTED(wsA, connA, { inviterSecureId: "bob" });
  const ack = lastMsg(wsA);
  assert(ack.type === "INVITE_ACCEPTED_ACK" && ack.ok === true, "valid → INVITE_ACCEPTED_ACK ok=true");
  const bobNotify = lastMsg(wsB);
  assert(bobNotify.type === "INVITE_ACCEPTED", "inviter (bob) notified");
  assert(bobNotify.newUserSecureId === "alice", "newUserSecureId = alice");
}

// ==========================================
// Suite: WEBRTC_OFFER + WEBRTC_ANSWER handlers
// ==========================================
console.log("\n[Suite] WEBRTC_OFFER + WEBRTC_ANSWER handlers");
{
  clearState();
  const ctx = buildCtx();
  const wsA = mockWs();
  const wsB = mockWs();
  ctx.clients.set("cA", { ws: wsA, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clients.set("cB", { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("alice", "cA");
  ctx.clientIds.set("bob", "cB");

  const sessionId = "sess-webrtc-01";
  ctx.routingTable.set(sessionId, { sessionId, from: "alice", to: "bob", state: "ACTIVE", created: Date.now(), updated: Date.now() });

  const validSdp = "v=0\r\no=- 0 0 IN IP4 127.0.0.1\r\n";

  // Not registered → error
  const wsX = mockWs();
  ctx.clients.set("cX", { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "9.9.9.9" });
  ctx.handlers.WEBRTC_OFFER(wsX, "cX", { sessionId, sdp: validSdp });
  assert(lastMsg(wsX).error === "not_registered", "OFFER: unregistered → not_registered");

  // Session not found
  ctx.handlers.WEBRTC_OFFER(wsA, "cA", { sessionId: "bad-sess", sdp: validSdp });
  assert(lastMsg(wsA).error === "session_not_found", "OFFER: bad sessionId → session_not_found");

  // Missing SDP
  ctx.handlers.WEBRTC_OFFER(wsA, "cA", { sessionId });
  assert(lastMsg(wsA).error === "missing_sdp", "OFFER: missing sdp → missing_sdp");

  // SDP too long
  ctx.handlers.WEBRTC_OFFER(wsA, "cA", { sessionId, sdp: "x".repeat(10001) });
  assert(lastMsg(wsA).error === "invalid_sdp", "OFFER: sdp > 10000 chars → invalid_sdp");

  // Valid OFFER: alice → bob
  ctx.handlers.WEBRTC_OFFER(wsA, "cA", { sessionId, sdp: validSdp });
  const offerAck = lastMsg(wsA);
  assert(offerAck.type === "WEBRTC_OFFER_ACK" && offerAck.ok === true, "OFFER: valid → WEBRTC_OFFER_ACK");
  const bobOffer = lastMsg(wsB);
  assert(bobOffer.type === "WEBRTC_OFFER", "OFFER: forwarded to bob");
  assert(bobOffer.from === "alice", "OFFER: from=alice");
  assert(bobOffer.sdp === validSdp, "OFFER: sdp forwarded");

  // Valid ANSWER: bob → alice
  ctx.handlers.WEBRTC_ANSWER(wsB, "cB", { sessionId, sdp: validSdp });
  const answerAck = lastMsg(wsB);
  assert(answerAck.type === "WEBRTC_ANSWER_ACK" && answerAck.ok === true, "ANSWER: valid → WEBRTC_ANSWER_ACK");
  const aliceAnswer = lastMsg(wsA);
  assert(aliceAnswer.type === "WEBRTC_ANSWER", "ANSWER: forwarded to alice");
  assert(aliceAnswer.from === "bob", "ANSWER: from=bob");

  // ANSWER: missing SDP
  ctx.handlers.WEBRTC_ANSWER(wsB, "cB", { sessionId });
  assert(lastMsg(wsB).error === "missing_sdp", "ANSWER: missing sdp → missing_sdp");

  // ANSWER: session not found
  ctx.handlers.WEBRTC_ANSWER(wsB, "cB", { sessionId: "gone", sdp: validSdp });
  assert(lastMsg(wsB).error === "session_not_found", "ANSWER: bad sessionId → session_not_found");
}

// ==========================================
// Suite: ICE_CANDIDATE handler
// ==========================================
console.log("\n[Suite] ICE_CANDIDATE handler");
{
  clearState();
  const ctx = buildCtx();
  const wsA = mockWs();
  const wsB = mockWs();
  ctx.clients.set("cA", { ws: wsA, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clients.set("cB", { ws: wsB, lastSeen: Date.now(), clientId: "bob", ip: "2.2.2.2" });
  ctx.clientIds.set("alice", "cA");
  ctx.clientIds.set("bob", "cB");

  const sessionId = "sess-ice-01";
  ctx.routingTable.set(sessionId, { sessionId, from: "alice", to: "bob", state: "ACTIVE", created: Date.now(), updated: Date.now() });

  const candidate = { candidate: "candidate:0 1 UDP 2122252543 192.168.1.2 52832 typ host", sdpMid: "0", sdpMLineIndex: 0 };

  // Not registered
  const wsX = mockWs();
  ctx.clients.set("cX", { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "9.9.9.9" });
  ctx.handlers.ICE_CANDIDATE(wsX, "cX", { sessionId, candidate });
  assert(lastMsg(wsX).error === "not_registered", "ICE: unregistered → not_registered");

  // Session not found
  ctx.handlers.ICE_CANDIDATE(wsA, "cA", { sessionId: "bad", candidate });
  assert(lastMsg(wsA).error === "session_not_found", "ICE: bad sessionId → session_not_found");

  // Missing candidate
  ctx.handlers.ICE_CANDIDATE(wsA, "cA", { sessionId });
  assert(lastMsg(wsA).error === "missing_candidate", "ICE: missing candidate → missing_candidate");

  // Valid ICE: alice → bob (object candidate)
  ctx.handlers.ICE_CANDIDATE(wsA, "cA", { sessionId, candidate });
  assert(lastMsg(wsA).type === "ICE_CANDIDATE_ACK", "ICE: valid object → ICE_CANDIDATE_ACK");
  assert(lastMsg(wsA).ok === true, "ICE: ACK ok=true");
  const bobIce = lastMsg(wsB);
  assert(bobIce.type === "ICE_CANDIDATE", "ICE: forwarded to bob");
  assert(bobIce.from === "alice", "ICE: from=alice");

  // Valid ICE: string candidate
  ctx.handlers.ICE_CANDIDATE(wsA, "cA", { sessionId, candidate: "candidate:0 1 UDP 2122252543 192.168.1.2 52832 typ host" });
  assert(lastMsg(wsA).type === "ICE_CANDIDATE_ACK", "ICE: valid string → ICE_CANDIDATE_ACK");
}

// ==========================================
// Suite: GHOST_PREPARE handler
// ==========================================
console.log("\n[Suite] GHOST_PREPARE handler");
{
  clearState();
  const ctx = buildCtx();
  const ws = mockWs();
  const connId = "conn-ghost";
  ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId: "alice", ip: "1.1.1.1" });
  ctx.clientIds.set("alice", connId);

  const sessionId = "sess-ghost-01";
  ctx.routingTable.set(sessionId, { sessionId, from: "alice", to: "bob", state: "ACTIVE", created: Date.now(), updated: Date.now() });

  // Not registered
  const wsX = mockWs();
  ctx.clients.set("cX", { ws: wsX, lastSeen: Date.now(), clientId: null, ip: "9.9.9.9" });
  ctx.handlers.GHOST_PREPARE(wsX, "cX", { sessionId });
  assert(lastMsg(wsX).error === "not_registered", "GHOST: unregistered → not_registered");

  // Session not found
  ctx.handlers.GHOST_PREPARE(ws, connId, { sessionId: "bad-sess" });
  assert(lastMsg(ws).error === "session_not_found", "GHOST: bad sessionId → session_not_found");

  // Valid GHOST_PREPARE
  ctx.handlers.GHOST_PREPARE(ws, connId, { sessionId });
  const ghostAck = lastMsg(ws);
  assert(ghostAck.type === "GHOST_ACK", "GHOST: valid → GHOST_ACK");
  assert(typeof ghostAck.ghostNetId === "string" && ghostAck.ghostNetId.length > 0, "ghostNetId is UUID string");
  assert(Array.isArray(ghostAck.iceServers) && ghostAck.iceServers.length > 0, "iceServers included");
  assert(Array.isArray(ghostAck.relayHints) && ghostAck.relayHints.length === 2, "2 relay hints included");
  assert(ghostAck.sessionId === sessionId, "sessionId echoed in GHOST_ACK");
}

// ==========================================
// Results
// ==========================================
console.log(`\n${"─".repeat(50)}`);
const total = passed + failed;
console.log(`subscription_webrtc.test: ${passed}/${total} passed${failed > 0 ? ` (${failed} FAILED)` : " ✅"}`);
if (failed > 0) process.exit(1);
