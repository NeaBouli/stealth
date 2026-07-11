const assert = require("assert");
const { findCodeByPurchaseToken, parseSubscriptionPurchase, resolveOneTimeProduct } = require("../payments/google_play_billing");

delete process.env.GOOGLE_PLAY_ALLOWED_PACKAGES;
assert.deepStrictEqual(resolveOneTimeProduct("com.securecall.app.free", "securecall_pro_lifetime"), {
  productId: "securecall_pro_lifetime", tier: "pro"
});
assert.strictEqual(resolveOneTimeProduct("evil.package", "securecall_pro_lifetime"), null);
assert.strictEqual(resolveOneTimeProduct("com.securecall.app.free", "securecall_pro_monthly"), null);
assert.strictEqual(resolveOneTimeProduct("com.securecall.app.free", "premium_fake"), null);

const codes = new Map([["PREM-TEST", { purchaseToken: "token-1", tier: "premium" }]]);
assert.deepStrictEqual(findCodeByPurchaseToken(codes, "token-1"), {
  code: "PREM-TEST", record: { purchaseToken: "token-1", tier: "premium" }
});
assert.strictEqual(findCodeByPurchaseToken(codes, "token-2"), null);
const future = new Date(Date.now() + 86400000).toISOString();
assert.deepStrictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  lineItems: [{ productId: "securecall_pro_monthly", expiryTime: future }]
}, "securecall_pro_monthly"), { tier: "pro", expiresAt: Date.parse(future) });
assert.strictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ON_HOLD",
  lineItems: [{ productId: "securecall_pro_monthly", expiryTime: future }]
}, "securecall_pro_monthly"), null);
console.log("google_play_billing.test.js ok");
