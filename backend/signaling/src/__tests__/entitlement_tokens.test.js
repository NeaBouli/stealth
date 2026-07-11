const assert = require("assert");
const crypto = require("crypto");

const { privateKey, publicKey } = crypto.generateKeyPairSync("ed25519");
process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM = privateKey.export({ type: "pkcs8", format: "pem" });

const { issueEntitlementToken, verifyEntitlementToken, audienceForProduct, orderHash, TOKEN_TTL_SECONDS } = require("../payments/entitlement_tokens");

assert.strictEqual(audienceForProduct("securechat_pro_lifetime"), "securechat");
assert.strictEqual(audienceForProduct("chameleon_elite_lifetime"), "chameleon");
assert.strictEqual(audienceForProduct("stealthx_suite_lifetime"), "stealthx-suite");
assert.strictEqual(audienceForProduct("securecall_pro_lifetime"), "securecall");

const now = 1_800_000_000;
const token = issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_secret_order",
  nowSeconds: now,
});
assert.ok(token);
const [encodedPayload, encodedSignature] = token.split(".");
const payload = Buffer.from(encodedPayload, "base64url").toString("utf8");
assert.ok(payload.includes("aud=securechat"));
assert.ok(payload.includes("sub=sx_test_device"));
assert.ok(payload.includes("tier=PRO"));
assert.ok(payload.includes(`exp=${now + TOKEN_TTL_SECONDS}`));
assert.ok(!payload.includes("cs_test_secret_order"), "raw Stripe order id is not exposed");
assert.strictEqual(
  crypto.verify(null, Buffer.from(encodedPayload, "utf8"), publicKey, Buffer.from(encodedSignature, "base64url")),
  true,
);
const verified = verifyEntitlementToken(token, { expectedSubject: "sx_test_device", nowSeconds: now + 60 });
assert.strictEqual(verified.product, "securechat_pro_lifetime");
assert.strictEqual(verified.order, orderHash("cs_test_secret_order"));
assert.throws(() => verifyEntitlementToken(token, { expectedSubject: "copied_device", nowSeconds: now + 60 }));
assert.doesNotThrow(() => verifyEntitlementToken(token, {
  expectedSubject: "sx_test_device",
  nowSeconds: now + TOKEN_TTL_SECONDS + 60,
  expiryGraceSeconds: 120,
}));
assert.throws(() => verifyEntitlementToken(token, {
  expectedSubject: "sx_test_device",
  nowSeconds: now + TOKEN_TTL_SECONDS + 121,
  expiryGraceSeconds: 120,
}));
assert.strictEqual(
  crypto.verify(null, Buffer.from(`${encodedPayload}x`, "utf8"), publicKey, Buffer.from(encodedSignature, "base64url")),
  false,
);

delete process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
assert.strictEqual(issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_order",
  nowSeconds: now,
}), null, "missing signing key fails closed without issuing a token");

console.log("entitlement_tokens.test.js ok");
