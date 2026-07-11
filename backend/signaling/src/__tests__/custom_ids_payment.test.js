const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");

const testDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-custom-id-payment-"));
process.env.IDS_FILE = path.join(testDir, "ids.json");
process.env.PENDING_FILE = path.join(testDir, "pending.json");
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

const { activate, markPendingPaid } = require("../custom_ids");

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

fs.rmSync(testDir, { recursive: true, force: true });
console.log("custom_ids_payment.test.js ok");
