"use strict";
/**
 * context.smoke.js — validates context.js wiring without a running wss.
 *
 * Run: node src/__tests__/context.smoke.js
 * CI: added to ci-basic.yml node --check pass
 */
const assert = require("assert");

// Minimal mock external deps that satisfy the interface contracts
const mockPkd = {
  registerKey: (pub) => ({ keyId: "k1", publicKey: pub, created: Date.now() }),
  getKey: () => null,
  rotateKey: () => null,
  deleteKey: () => false,
};
const mockSubscriptions = {
  verifySubscription: () => ({ tier: "FREE", expiresAt: 0 }),
  getSubscription: () => null,
};
const mockFcm = {
  initFcm: () => {},
  sendPushNotification: () => Promise.resolve({ success: false }),
};
const mockCustomIds = { resolveCustomId: () => null };
const mockLicenses = {
  getStatus: () => ({}),
  recordSale: () => {},
  saveLicenses: () => {},
  getCurrentPrice: () => null,
  LICENSES: { pro_lifetime: { sold: 0 }, premium_lifetime: { sold: 0 } },
};
const mockRateLimit = {
  registerEvent: () => true,
  registerBinaryEvent: () => true,
};
const mockHb = { start: () => {}, updateClient: () => {}, startInterval: () => {} };

// ── Build context ─────────────────────────────────────────────────────────────

const { buildContext } = require("../context");

const ctx = buildContext({
  pkd: mockPkd,
  subscriptions: mockSubscriptions,
  fcm: mockFcm,
  customIds: mockCustomIds,
  licenses: mockLicenses,
  getIceServers: () => [{ urls: "stun:stun.l.google.com:19302" }],
  ADMIN_API_KEY: "smoke-test-admin-key",
  ALLOWED_ORIGINS: ["https://stealthx.tech"],
  rateLimit: mockRateLimit,
  hb: mockHb,
  giftCodes: new Map(),
  saveGiftCodes: () => {},
  CLIENT_ID_REGEX: /^[a-zA-Z0-9_-]{1,64}$/,
});

// ── Assert: core state refs ───────────────────────────────────────────────────

const stateFields = [
  "clients", "clientIds", "routingTable",
  "phoneNumbers", "phoneHashes", "ipConnections",
];
for (const f of stateFields) {
  assert.ok(ctx[f] instanceof Map, `ctx.${f} must be a Map`);
}

const arrayFields = ["activationCodes", "walletMappings"];
for (const f of arrayFields) {
  assert.ok(Array.isArray(ctx[f]), `ctx.${f} must be an Array`);
}

assert.ok(ctx.fcmTokens instanceof Map, "ctx.fcmTokens must be a Map");
assert.ok(ctx.giftCodes instanceof Map, "ctx.giftCodes must be a Map");

// ── Assert: helpers ───────────────────────────────────────────────────────────

const helpers = ["sendToClient", "getClientId", "getSessionPeer", "forwardBinaryToPeer"];
for (const h of helpers) {
  assert.strictEqual(typeof ctx[h], "function", `ctx.${h} must be a function`);
}

// ── Assert: WS handlers ───────────────────────────────────────────────────────

const expectedHandlers = [
  "REGISTER", "DEREGISTER", "REGISTER_FCM_TOKEN",
  "CALL_INVITE", "CALL_ACCEPT", "CALL_BUSY", "CALL_END",
  "WEBRTC_OFFER", "WEBRTC_ANSWER", "ICE_CANDIDATE", "GHOST_PREPARE",
  "PHONE_LOOKUP", "BATCH_PHONE_LOOKUP", "ONLINE_STATUS_REQUEST",
  "SUBSCRIPTION_VERIFY", "ACTIVATE_CODE", "VERIFY_IFR_LOCK", "INVITE_ACCEPTED",
];

assert.ok(ctx.handlers && typeof ctx.handlers === "object", "ctx.handlers must be an object");
for (const type of expectedHandlers) {
  assert.strictEqual(
    typeof ctx.handlers[type], "function",
    `ctx.handlers.${type} must be a function`
  );
}

// ── Assert: middleware refs ───────────────────────────────────────────────────

assert.strictEqual(typeof ctx.requireAdmin, "function", "ctx.requireAdmin must be a function");
assert.strictEqual(typeof ctx.corsMiddleware, "function", "ctx.corsMiddleware must be a function");
assert.strictEqual(typeof ctx.getClientIp, "function", "ctx.getClientIp must be a function");

// ── Assert: store ops ─────────────────────────────────────────────────────────

const storeFns = [
  "loadFcmTokens", "saveFcmTokens",
  "loadActivationCodes", "saveActivationCodes",
  "loadWalletMappings", "saveWalletMappings",
];
for (const fn of storeFns) {
  assert.strictEqual(typeof ctx[fn], "function", `ctx.${fn} must be a function`);
}

// ── Done ──────────────────────────────────────────────────────────────────────

console.log(
  `✓ context.smoke PASSED — ${stateFields.length} state maps, ` +
  `${helpers.length} helpers, ${expectedHandlers.length} WS handlers wired`
);
