const assert = require("assert");
const crypto = require("crypto");

const { privateKey, publicKey } = crypto.generateKeyPairSync("ed25519");
process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM = privateKey.export({ type: "pkcs8", format: "pem" });

const {
  issueEntitlementToken,
  verifyEntitlementToken,
  audienceForProduct,
  orderHash,
  signingPrivateKey,
  SIGNING_UNAVAILABLE_CODE,
  TOKEN_TTL_SECONDS,
} = require("../payments/entitlement_tokens");

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

const privateKeyPem = process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
delete process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM_BASE64 = Buffer.from(privateKeyPem, "utf8").toString("base64");
assert.strictEqual(signingPrivateKey(), privateKeyPem);
assert.ok(issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "chameleon_elite_lifetime",
  tier: "elite",
  externalOrderId: "google_play_reviewer",
  nowSeconds: now,
}), "base64-encoded signing key issues a token");

delete process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM_BASE64;
delete process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
assert.strictEqual(issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_order",
  nowSeconds: now,
}), null, "missing signing key fails closed without issuing a token");
assert.throws(
  () => verifyEntitlementToken(token, { expectedSubject: "sx_test_device", nowSeconds: now + 60 }),
  error => error && error.code === SIGNING_UNAVAILABLE_CODE,
  "missing signing key produces a retryable infrastructure error",
);

// --- v2 exact-claim tokens -----------------------------------------------------

process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM = privateKeyPem;

const V2_CLAIMS = {
  catalogVersion: "stealthx-lifetime-v1",
  offerVersion: "securechat-pro-eur-900-lifetime-v1",
  releaseId: "securechat-android-0.1.11-alpha-vc15-api36",
};
const v2Token = issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_v2_order",
  ...V2_CLAIMS,
  nowSeconds: now,
});
assert.ok(v2Token, "v2 token issued with the complete immutable contract tuple");
const [v2EncodedPayload] = v2Token.split(".");
const v2Payload = Buffer.from(v2EncodedPayload, "base64url").toString("utf8");
assert.ok(v2Payload.includes("v=2"));
assert.ok(v2Payload.includes("catalog=stealthx-lifetime-v1"));
assert.ok(v2Payload.includes("offer=securechat-pro-eur-900-lifetime-v1"));
assert.ok(v2Payload.includes("release=securechat-android-0.1.11-alpha-vc15-api36"));
const verifiedV2 = verifyEntitlementToken(v2Token, { expectedSubject: "sx_test_device", nowSeconds: now + 60 });
assert.strictEqual(verifiedV2.v, "2");
assert.strictEqual(verifiedV2.catalog, V2_CLAIMS.catalogVersion);
assert.strictEqual(verifiedV2.offer, V2_CLAIMS.offerVersion);
assert.strictEqual(verifiedV2.release, V2_CLAIMS.releaseId);
assert.throws(() => verifyEntitlementToken(v2Token, { expectedSubject: "copied_device", nowSeconds: now + 60 }),
  undefined, "v2 token is bound to its subject");

// v2 tamper: a modified claim no longer matches the signature.
const tamperedPayload = Buffer.from(
  v2Payload.replace("release=securechat-android-0.1.11-alpha-vc15-api36", "release=securechat-android-9.9.9-forged"),
  "utf8",
).toString("base64url");
assert.throws(() => verifyEntitlementToken(`${tamperedPayload}.${v2Token.split(".")[1]}`, {
  expectedSubject: "sx_test_device",
  nowSeconds: now + 60,
}), /Invalid entitlement signature/, "tampered v2 payload fails signature verification");

// v2 issuance fails closed on any contract drift or incomplete tuple.
assert.throws(() => issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_v2_order",
  ...V2_CLAIMS,
  offerVersion: "securechat-pro-eur-900-lifetime-v2",
  nowSeconds: now,
}), /Invalid entitlement claims/, "offerVersion drift rejects v2 issuance");
assert.throws(() => issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "elite",
  externalOrderId: "cs_test_v2_order",
  ...V2_CLAIMS,
  nowSeconds: now,
}), /Invalid entitlement claims/, "tier drift rejects v2 issuance");
assert.throws(() => issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "securechat_pro_lifetime",
  tier: "pro",
  externalOrderId: "cs_test_v2_order",
  catalogVersion: V2_CLAIMS.catalogVersion,
  nowSeconds: now,
}), /Invalid entitlement claims/, "partial contract tuple rejects v2 issuance");
assert.throws(() => issueEntitlementToken({
  subject: "sx_test_device",
  productKey: "stealthx_suite_lifetime",
  tier: "elite",
  externalOrderId: "cs_test_v2_order",
  catalogVersion: V2_CLAIMS.catalogVersion,
  offerVersion: "suite-eur-0-lifetime-v1",
  releaseId: "suite-android-0.0.0",
  nowSeconds: now,
}), /Invalid entitlement claims/, "Suite is never an accepted v2 product");

// v1 migration: tokens issued before v2 remain verifiable, without v2 claims.
const legacyV1 = issueEntitlementToken({
  subject: "sx_reviewer_device",
  productKey: "chameleon_elite_lifetime",
  tier: "elite",
  externalOrderId: "google_play_reviewer",
  nowSeconds: now,
});
const verifiedLegacy = verifyEntitlementToken(legacyV1, { expectedSubject: "sx_reviewer_device", nowSeconds: now + 60 });
assert.strictEqual(verifiedLegacy.v, "1");
assert.strictEqual(verifiedLegacy.catalog, undefined, "v1 tokens carry no v2 claims");

delete process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;

console.log("entitlement_tokens.test.js ok");
