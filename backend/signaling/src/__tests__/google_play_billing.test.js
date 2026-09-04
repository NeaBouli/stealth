const assert = require("assert");
const {
  PLAY_CATALOG_VERSION,
  findCodeByPurchaseToken,
  issuePlayActivationCode,
  isPlayBillingEnabled,
  parseSubscriptionPurchase,
  parseSubscriptionToken,
  resolveOneTimeProduct,
} = require("../payments/google_play_billing");

delete process.env.PLAY_BILLING_ENABLED;
assert.strictEqual(isPlayBillingEnabled(), false);
process.env.PLAY_BILLING_ENABLED = "true";
assert.strictEqual(isPlayBillingEnabled(), true);
process.env.PLAY_BILLING_ENABLED = "TRUE";
assert.strictEqual(isPlayBillingEnabled(), true);
process.env.PLAY_BILLING_ENABLED = "false";
assert.strictEqual(isPlayBillingEnabled(), false);

delete process.env.GOOGLE_PLAY_ALLOWED_PACKAGES;
assert.deepStrictEqual(resolveOneTimeProduct("com.securecall.app.free", "securecall_pro_lifetime"), {
  productId: "securecall_pro_lifetime", tier: "pro", catalogVersion: PLAY_CATALOG_VERSION
});
assert.strictEqual(resolveOneTimeProduct("evil.package", "securecall_pro_lifetime"), null);
assert.strictEqual(resolveOneTimeProduct("com.securecall.app.free", "securecall_pro_monthly"), null);
assert.strictEqual(resolveOneTimeProduct("com.securecall.app.free", "premium_fake"), null);

const codes = new Map([["PREM-TEST", {
  purchaseToken: "token-1",
  tier: "premium",
  catalogVersion: PLAY_CATALOG_VERSION
}]]);
assert.deepStrictEqual(findCodeByPurchaseToken(codes, "token-1"), {
  code: "PREM-TEST",
  record: { purchaseToken: "token-1", tier: "premium", catalogVersion: PLAY_CATALOG_VERSION }
});
assert.strictEqual(findCodeByPurchaseToken(codes, "token-2"), null);
const activationCodes = [{
  code: "PRO-TEST",
  purchaseToken: "token-2",
  tier: "pro",
  catalogVersion: PLAY_CATALOG_VERSION
}];
assert.deepStrictEqual(findCodeByPurchaseToken(activationCodes, "token-2"), {
  code: "PRO-TEST", record: activationCodes[0]
});

const issuedCodes = [];
const issued = issuePlayActivationCode({
  activationCodes: issuedCodes,
  giftCodes: new Map(),
  saveActivationCodes: () => true,
  purchaseToken: "play-token-1",
  productId: "securecall_pro_lifetime",
  packageName: "com.securecall.app.free",
  now: 1000,
});
assert.strictEqual(issued.tier, "pro");
assert.strictEqual(issued.duplicate, false);
assert.match(issued.code, /^PRO-[A-F0-9]{8}$/);
assert.strictEqual(issuedCodes[0].productKey, "securecall_pro_lifetime");
assert.strictEqual(issuedCodes[0].purchasePackage, "com.securecall.app.free");
assert.strictEqual(issuedCodes[0].catalogVersion, PLAY_CATALOG_VERSION);

const duplicate = issuePlayActivationCode({
  activationCodes: issuedCodes,
  giftCodes: new Map(),
  saveActivationCodes: () => { throw new Error("must not persist duplicate"); },
  purchaseToken: "play-token-1",
  productId: "securecall_pro_lifetime",
  packageName: "com.securecall.app.free",
});
assert.strictEqual(duplicate.code, issued.code);
assert.strictEqual(duplicate.duplicate, true);

assert.throws(() => issuePlayActivationCode({
  activationCodes: issuedCodes,
  giftCodes: new Map(),
  saveActivationCodes: () => true,
  purchaseToken: "play-token-1",
  productId: "securecall_premium_lifetime",
  packageName: "com.securecall.app.free",
}), /purchase_binding_mismatch/);

const failedPersistenceCodes = [];
assert.throws(() => issuePlayActivationCode({
  activationCodes: failedPersistenceCodes,
  giftCodes: new Map(),
  saveActivationCodes: () => false,
  purchaseToken: "play-token-persistence-failure",
  productId: "securecall_pro_lifetime",
  packageName: "com.securecall.app.free",
}), /purchase_persistence_failed/);
assert.deepStrictEqual(failedPersistenceCodes, []);
const future = new Date(Date.now() + 86400000).toISOString();
assert.deepStrictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_PENDING",
  lineItems: [{ productId: "securecall_pro_monthly", expiryTime: future }]
}, "securecall_pro_monthly"), {
  tier: "pro",
  expiresAt: Date.parse(future),
  catalogVersion: PLAY_CATALOG_VERSION,
  needsAcknowledgement: true
});
assert.strictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ON_HOLD",
  lineItems: [{ productId: "securecall_pro_monthly", expiryTime: future }]
}, "securecall_pro_monthly"), null);
assert.deepStrictEqual(parseSubscriptionToken({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  acknowledgementState: "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  lineItems: [{ productId: "securecall_premium_yearly", expiryTime: future }]
}), {
  productId: "securecall_premium_yearly",
  tier: "premium",
  expiresAt: Date.parse(future),
  catalogVersion: PLAY_CATALOG_VERSION,
  needsAcknowledgement: false
});
console.log("google_play_billing.test.js ok");
