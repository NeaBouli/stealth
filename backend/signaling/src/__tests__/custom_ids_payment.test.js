const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const testDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-custom-id-payment-"));
process.env.IDS_FILE = path.join(testDir, "ids.json");
process.env.PENDING_FILE = path.join(testDir, "pending.json");
delete process.env.ID_HASH_PEPPER;
fs.writeFileSync(process.env.IDS_FILE, JSON.stringify({
  "owned-id": { deviceId: "device-owned", stripeSessionId: "cs_test_refund" }
}));
fs.writeFileSync(process.env.PENDING_FILE, JSON.stringify({
  token_paid: {
    customId: "chosen-id",
    passwordHash: crypto.pbkdf2Sync("test-password-123", "test-salt", 100000, 64, "sha512").toString("hex"),
    passwordSalt: "test-salt",
    createdAt: Date.now(),
    stripeSessionId: "cs_test_custom_id",
    paidAt: null
  }
}));

const { activate, markPendingPaid, revokeByStripeSession, resolve, setupRoutes } = require("../custom_ids");
const routes = {};
setupRoutes({ get() {}, post(route, ...handlers) { routes[route] = handlers.at(-1); } });
function activateToken() {
  const res = { payload: null, json(value) { this.payload = value; return this; } };
  routes["/custom-id/activate-token"]({ body: {
    id: "chosen-id", deviceId: "device-chosen", token: "token_paid",
  } }, res);
  return res.payload;
}

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

assert.deepStrictEqual(revokeByStripeSession("invalid"), { revokedIds: 0, revokedPending: 0 });
assert.deepStrictEqual(revokeByStripeSession("cs_test_refund"), { revokedIds: 1, revokedPending: 0 });
assert.deepStrictEqual(JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8")), {});

const originalRename = fs.renameSync;
try {
  // A failed ID write must propagate, retain the paid token and allow a real retry.
  fs.renameSync = (from, to) => {
    if (to === process.env.IDS_FILE) throw new Error("injected ID persistence failure");
    return originalRename(from, to);
  };
  assert.throws(activateToken, /injected ID persistence failure/);
  assert.strictEqual(resolve("chosen-id"), null, "failed activation does not publish ownership");
  assert.ok(JSON.parse(fs.readFileSync(process.env.PENDING_FILE, "utf8")).token_paid,
    "failed ID persistence must not consume the pending token on disk");
  assert.deepStrictEqual(JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8")), {});
  // Retrying without a reload proves the token also remains available in memory.
  assert.throws(activateToken, /injected ID persistence failure/,
    "an in-memory activation must not turn a failed save into an already-active success");

  const writes = [];
  fs.renameSync = (from, to) => {
    if (to === process.env.IDS_FILE || to === process.env.PENDING_FILE) writes.push(to);
    if (to === process.env.IDS_FILE) {
      assert.ok(JSON.parse(fs.readFileSync(process.env.PENDING_FILE, "utf8")).token_paid,
        "the pending token must still exist when ownership is committed");
    }
    return originalRename(from, to);
  };
  assert.deepStrictEqual(activateToken(), { success: true, transferred: false });
  assert.deepStrictEqual(writes, [process.env.IDS_FILE, process.env.PENDING_FILE]);
  assert.strictEqual(resolve("chosen-id"), "device-chosen");
  assert.strictEqual(JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8"))["chosen-id"].deviceId, "device-chosen");
  assert.strictEqual(JSON.parse(fs.readFileSync(process.env.PENDING_FILE, "utf8")).token_paid, undefined);
  assert.strictEqual(markPendingPaid("token_paid", "chosen-id", "cs_test_custom_id"), false,
    "successful persistence consumes the token in memory too");

  // Legacy revocation must propagate a failed save and remain retryable as well.
  fs.renameSync = (from, to) => {
    if (to === process.env.IDS_FILE) throw new Error("injected ID persistence failure");
    return originalRename(from, to);
  };
  assert.throws(() => activate("chosen-id", "device-transfer", "test-password-123"), /injected ID persistence failure/);
  assert.strictEqual(resolve("chosen-id"), "device-chosen", "failed transfer preserves the previous device mapping");
  assert.throws(() => revokeByStripeSession("cs_test_custom_id"), /injected ID persistence failure/);
  assert.strictEqual(resolve("chosen-id"), "device-chosen", "failed revocation preserves live ownership for retry");
  fs.renameSync = originalRename;
  assert.deepStrictEqual(revokeByStripeSession("cs_test_custom_id"), { revokedIds: 1, revokedPending: 0 });
  assert.strictEqual(resolve("chosen-id"), null);
  assert.deepStrictEqual(JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8")), {});
} finally {
  fs.renameSync = originalRename;
  fs.rmSync(testDir, { recursive: true, force: true });
}

console.log("custom_ids_payment.test.js ok");
