const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const testDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-custom-id-payment-"));
process.env.ID_HASH_PEPPER = "test-custom-id-pepper-32-characters-minimum";
process.env.IDS_FILE = path.join(testDir, "ids.json");
process.env.PENDING_FILE = path.join(testDir, "pending.json");
fs.writeFileSync(process.env.IDS_FILE, JSON.stringify({
  "owned-id": { deviceId: "device-owned", stripeSessionId: "cs_test_refund" }
}));
fs.writeFileSync(process.env.PENDING_FILE, JSON.stringify({
  token_paid: {
    customId: "chosen-id",
    passwordHash: "test-hash",
    passwordSalt: "test-salt",
    createdAt: Date.now(),
    stripeSessionId: "cs_test_custom_id",
    paidAt: null
  }
}));

const { activate, markPendingPaid, revokeByStripeSession, setupRoutes } = require("../custom_ids");

assert.deepStrictEqual(
  activate("free-id", "device-a", "password123"),
  { success: false, error: "purchase_required" },
  "direct activation cannot mint an unpaid Custom ID"
);

assert.strictEqual(markPendingPaid("missing", "chosen-id", "cs_test_custom_id"), false);
assert.strictEqual(markPendingPaid("token_paid", "other-id", "cs_test_custom_id"), false);
assert.strictEqual(markPendingPaid("token_paid", "chosen-id", "cs_other"), false);
assert.strictEqual(markPendingPaid("token_paid", "chosen-id", "cs_test_custom_id"), true);

const stored = JSON.parse(fs.readFileSync(process.env.PENDING_FILE, "utf8"));
assert.ok(stored.token_paid.paidAt, "paid webhook persists payment confirmation");
assert.strictEqual(Object.prototype.hasOwnProperty.call(stored.token_paid, "customId"), false, "pending store removes clear custom ID");
assert.match(stored.token_paid.customIdKey, /^[a-f0-9]{64}$/, "pending store binds only HMAC custom ID");
assert.strictEqual(fs.statSync(process.env.PENDING_FILE).mode & 0o777, 0o600, "pending store is private");

assert.deepStrictEqual(revokeByStripeSession("invalid"), { revokedIds: 0, revokedPending: 0 });
assert.deepStrictEqual(revokeByStripeSession("cs_test_refund"), { revokedIds: 1, revokedPending: 0 });
assert.deepStrictEqual(JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8")), {});

const postRoutes = new Map();
setupRoutes({
  get() {},
  post(route, ...handlers) { postRoutes.set(route, handlers); }
});
const activateTokenHandlers = postRoutes.get("/custom-id/activate-token");
assert.strictEqual(activateTokenHandlers.length, 2, "token activation must include rate limiting middleware");
let allowedAttempts = 0;
const rateLimitResponse = {
  statusCode: 200,
  body: null,
  status(code) { this.statusCode = code; return this; },
  json(value) { this.body = value; return this; }
};
for (let attempt = 0; attempt < 5; attempt += 1) {
  activateTokenHandlers[0]({ ip: "127.0.0.1" }, rateLimitResponse, () => { allowedAttempts += 1; });
}
activateTokenHandlers[0]({ ip: "127.0.0.1" }, rateLimitResponse, () => { allowedAttempts += 1; });
assert.strictEqual(allowedAttempts, 5);
assert.strictEqual(rateLimitResponse.statusCode, 429);
assert.strictEqual(rateLimitResponse.body.error, "rate_limited");

fs.rmSync(testDir, { recursive: true, force: true });
console.log("custom_ids_payment.test.js ok");
