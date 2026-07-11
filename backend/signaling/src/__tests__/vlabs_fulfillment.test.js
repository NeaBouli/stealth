const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-vlabs-payment-"));
process.env.SOLD_CODES_FILE = path.join(directory, "sold_codes.json");
process.env.VLABS_FULFILLMENT_ORDERS_FILE = path.join(directory, "orders.json");

const { verifySignature, isRevocationReason, PRODUCTS } = require("../payments/vlabs_fulfillment");
const soldCodes = require("../payments/sold_codes");

const secret = "test-secret";
const timestamp = Math.floor(Date.now() / 1000).toString();
const body = JSON.stringify({ externalOrderId: "cs_test_123", productId: "stealthx-securecall-pro-lifetime" });
const signature = crypto.createHmac("sha256", secret).update(`${timestamp}.${body}`).digest("hex");
assert.strictEqual(verifySignature(secret, timestamp, body, signature), true);
assert.strictEqual(verifySignature(secret, timestamp, `${body}x`, signature), false);
assert.deepStrictEqual(Object.keys(PRODUCTS).sort(), [
  "stealthx-securecall-premium-lifetime",
  "stealthx-securecall-pro-lifetime",
]);
assert.strictEqual(isRevocationReason("stripe_full_refund"), true);
assert.strictEqual(isRevocationReason("stripe_dispute"), true);
assert.strictEqual(isRevocationReason("partial_refund"), false);
assert.strictEqual(soldCodes.maskCode("PRO-TEST-TEST-TEST"), "PRO-****");
assert.strictEqual(soldCodes.maskEmail("test@example.invalid"), "te***@example.invalid");
assert.strictEqual(soldCodes.maskStripeId("cs_test_sensitive"), "cs_test_...");

const active = [];
soldCodes.recordSale({
  code: "PRO-TEST-TEST-TEST",
  tier: "pro",
  email: "test@example.invalid",
  stripeSessionId: "cs_test_refund_1",
  productKey: "vlabs_securecall_pro_lifetime",
  activationCodesRef: active,
});
assert.strictEqual(active.length, 1);
assert.deepStrictEqual(soldCodes.revokeByStripeSession("cs_test_refund_1", active), { found: true, duplicate: false });
assert.strictEqual(active.length, 0);
assert.strictEqual(soldCodes.loadAsActivationCodes().length, 0);
assert.deepStrictEqual(soldCodes.revokeByStripeSession("cs_test_refund_1", active), { found: true, duplicate: true });

fs.rmSync(directory, { recursive: true, force: true });
console.log("vlabs_fulfillment.test.js ok");
