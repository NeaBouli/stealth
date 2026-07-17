const assert = require("assert");
const {
  findCodeByPurchaseToken,
  issuePlayActivationCode,
  parseSubscriptionPurchase,
  parseSubscriptionToken,
  resolveOneTimeProduct,
} = require("../payments/google_play_billing");

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
const activationCodes = [{ code: "PRO-TEST", purchaseToken: "token-2", tier: "pro" }];
assert.deepStrictEqual(findCodeByPurchaseToken(activationCodes, "token-2"), {
  code: "PRO-TEST", record: activationCodes[0]
});

const issuedCodes = [];
let persistedOptions;
const issued = issuePlayActivationCode({
  activationCodes: issuedCodes,
  giftCodes: new Map(),
  saveActivationCodes: options => { persistedOptions = options; },
  purchaseToken: "play-token-1",
  productId: "securecall_pro_lifetime",
  packageName: "com.securecall.app.free",
  now: 1000,
});
assert.strictEqual(issued.tier, "pro");
assert.strictEqual(issued.duplicate, false);
assert.match(issued.code, /^PRO-[A-F0-9]{8}$/);
assert.deepStrictEqual(persistedOptions, { throwOnError: true });
assert.strictEqual(issuedCodes[0].productKey, "securecall_pro_lifetime");
assert.strictEqual(issuedCodes[0].purchasePackage, "com.securecall.app.free");

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
  saveActivationCodes: () => {},
  purchaseToken: "play-token-1",
  productId: "securecall_premium_lifetime",
  packageName: "com.securecall.app.free",
}), /purchase_binding_mismatch/);

const rollbackCodes = [];
assert.throws(() => issuePlayActivationCode({
  activationCodes: rollbackCodes,
  giftCodes: new Map(),
  saveActivationCodes: () => { throw new Error("disk unavailable"); },
  purchaseToken: "play-token-rollback",
  productId: "securecall_premium_activation_code",
  packageName: "com.securecall.app.free",
}), /disk unavailable/);
assert.strictEqual(rollbackCodes.length, 0);
const future = new Date(Date.now() + 86400000).toISOString();
assert.deepStrictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  lineItems: [{ productId: "pro_monthly", expiryTime: future }]
}, "pro_monthly"), { tier: "pro", expiresAt: Date.parse(future) });
assert.strictEqual(parseSubscriptionPurchase({
  subscriptionState: "SUBSCRIPTION_STATE_ON_HOLD",
  lineItems: [{ productId: "pro_monthly", expiryTime: future }]
}, "pro_monthly"), null);
assert.deepStrictEqual(parseSubscriptionToken({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  lineItems: [{ productId: "premium_monthly", expiryTime: future }]
}), { productId: "premium_monthly", tier: "premium", expiresAt: Date.parse(future) });
assert.deepStrictEqual(parseSubscriptionToken({
  subscriptionState: "SUBSCRIPTION_STATE_ACTIVE",
  lineItems: [{ productId: "securecall_premium_yearly", expiryTime: future }]
}), { productId: "securecall_premium_yearly", tier: "premium", expiresAt: Date.parse(future) });
console.log("google_play_billing.test.js ok");
