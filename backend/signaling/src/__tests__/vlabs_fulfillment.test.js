const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-vlabs-payment-"));
process.env.SOLD_CODES_FILE = path.join(directory, "sold_codes.json");
process.env.VLABS_FULFILLMENT_ORDERS_FILE = path.join(directory, "orders.json");

const SECRET = "test-fulfillment-secret-with-32+-chars";
process.env.VLABS_FULFILLMENT_SECRET = SECRET;
process.env.BREVO_API_KEY = "test-brevo-key";

const sentEmails = [];
const realFetch = global.fetch;
global.fetch = async (_url, options) => {
  sentEmails.push(JSON.parse(options.body));
  return { ok: true, json: async () => ({ messageId: "test-message" }) };
};

const fulfillment = require("../payments/vlabs_fulfillment");
const {
  verifySignature, isRevocationReason, validateFulfillmentContract, validateRevocationContract,
  createRequestRateLimiter,
  PRODUCTS, CATALOG_VERSION, MIN_FULFILLMENT_SECRET_LENGTH,
  INTERNAL_RATE_LIMIT_MAX_REQUESTS, INTERNAL_RATE_LIMIT_MAX_BUCKETS,
} = fulfillment;
const soldCodes = require("../payments/sold_codes");

// --- Static catalog contract ---------------------------------------------

assert.strictEqual(CATALOG_VERSION, "stealthx-lifetime-v1");
assert.ok(Object.isFrozen(PRODUCTS));
assert.deepStrictEqual(Object.keys(PRODUCTS).sort(), [
  "stealthx-chameleon-elite-lifetime",
  "stealthx-chameleon-pro-lifetime",
  "stealthx-securecall-premium-lifetime",
  "stealthx-securecall-pro-lifetime",
  "stealthx-securechat-elite-lifetime",
  "stealthx-securechat-pro-lifetime",
]);
const EXPECTED_PRODUCTS = {
  "stealthx-securecall-pro-lifetime": {
    tier: "pro", productKey: "vlabs_securecall_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securecall-pro-eur-1500-lifetime-v1",
    releaseId: "securecall-android-1.0.50-vc78017-api36",
    amount: 1500, currency: "eur", name: "SecureCall", url: "https://stealthx.tech/download.html",
  },
  "stealthx-securecall-premium-lifetime": {
    tier: "premium", productKey: "vlabs_securecall_premium_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securecall-premium-eur-2500-lifetime-v1",
    releaseId: "securecall-android-1.0.50-vc78017-api36",
    amount: 2500, currency: "eur", name: "SecureCall", url: "https://stealthx.tech/download.html",
  },
  "stealthx-securechat-pro-lifetime": {
    tier: "pro", productKey: "securechat_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securechat-pro-eur-900-lifetime-v1",
    releaseId: "securechat-android-0.1.11-alpha-vc15-api36",
    amount: 900, currency: "eur", name: "SecureChat", url: "https://securechat.stealthx.tech/",
  },
  "stealthx-securechat-elite-lifetime": {
    tier: "elite", productKey: "securechat_elite_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "securechat-elite-eur-1900-lifetime-v1",
    releaseId: "securechat-android-0.1.11-alpha-vc15-api36",
    amount: 1900, currency: "eur", name: "SecureChat", url: "https://securechat.stealthx.tech/",
  },
  "stealthx-chameleon-pro-lifetime": {
    tier: "pro", productKey: "chameleon_pro_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "chameleon-pro-eur-900-lifetime-v1",
    releaseId: "chameleon-android-0.1.13-alpha-vc14-api36",
    amount: 900, currency: "eur", name: "Chameleon", url: "https://chameleon.stealthx.tech/",
  },
  "stealthx-chameleon-elite-lifetime": {
    tier: "elite", productKey: "chameleon_elite_lifetime",
    catalogVersion: "stealthx-lifetime-v1",
    offerVersion: "chameleon-elite-eur-1900-lifetime-v1",
    releaseId: "chameleon-android-0.1.13-alpha-vc14-api36",
    amount: 1900, currency: "eur", name: "Chameleon", url: "https://chameleon.stealthx.tech/",
  },
};
for (const [productId, expected] of Object.entries(EXPECTED_PRODUCTS)) {
  assert.deepStrictEqual({ ...PRODUCTS[productId] }, expected, `exact contract fields for ${productId}`);
  assert.ok(Object.isFrozen(PRODUCTS[productId]), `${productId} entry is frozen`);
}
assert.ok(!PRODUCTS["stealthx-suite-lifetime"], "Suite is not part of the catalog");
assert.strictEqual(MIN_FULFILLMENT_SECRET_LENGTH, 32);

// --- Signature / revocation-reason primitives -----------------------------

const timestamp = Math.floor(Date.now() / 1000).toString();
const body = JSON.stringify({ externalOrderId: "cs_test_123", productId: "stealthx-securecall-pro-lifetime" });
const signature = crypto.createHmac("sha256", SECRET).update(`${timestamp}.${body}`).digest("hex");
assert.strictEqual(verifySignature(SECRET, timestamp, body, signature), true);
assert.strictEqual(verifySignature(SECRET, timestamp, `${body}x`, signature), false);
assert.strictEqual(isRevocationReason("stripe_full_refund"), true);
assert.strictEqual(isRevocationReason("stripe_dispute"), true);
assert.strictEqual(isRevocationReason("partial_refund"), false);
assert.strictEqual(soldCodes.maskCode("PRO-TEST-TEST-TEST"), "PRO-****");
assert.strictEqual(soldCodes.maskEmail("test@example.invalid"), "te***@example.invalid");
assert.strictEqual(soldCodes.maskStripeId("cs_test_sensitive"), "cs_test_...");

// --- Contract validators ---------------------------------------------------

function contractBody(productId, overrides = {}) {
  const product = PRODUCTS[productId] || {};
  return {
    externalOrderId: "cs_test_validator",
    productId,
    catalogVersion: CATALOG_VERSION,
    offerVersion: product.offerVersion,
    releaseId: product.releaseId,
    paymentReference: "pi_test_validator",
    paymentEventId: "evt_test_validator_paid",
    paymentStatus: "paid",
    amount: product.amount,
    currency: product.currency,
    customerEmail: "buyer@example.invalid",
    ...overrides,
  };
}

assert.ok(validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime")).product);
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { offerVersion: undefined })),
  error => error.status === 400, "missing offerVersion rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { amount: 1499 })),
  error => error.status === 409, "amount drift rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { currency: "usd" })),
  error => error.status === 409, "currency drift rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { releaseId: "securecall-android-9.9.9" })),
  error => error.status === 409, "releaseId drift rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { catalogVersion: "stealthx-lifetime-v0" })),
  error => error.status === 409, "catalogVersion drift rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { paymentStatus: "unpaid" })),
  error => error.status === 409, "unpaid proof rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { paymentReference: "ch_test" })),
  error => error.status === 400, "non-payment-intent proof rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-securecall-pro-lifetime", { paymentEventId: undefined })),
  error => error.status === 400, "missing payment event rejected");
assert.throws(() => validateFulfillmentContract(contractBody("stealthx-suite-lifetime")),
  error => error.status === 409, "Suite product is never enabled");

const revokeBody = (productId, overrides = {}) => ({
  externalOrderId: "cs_test_validator",
  productId,
  catalogVersion: CATALOG_VERSION,
  offerVersion: (PRODUCTS[productId] || {}).offerVersion,
  releaseId: (PRODUCTS[productId] || {}).releaseId,
  paymentReference: "pi_test_validator",
  adjustmentEventId: "evt_test_validator",
  reason: "stripe_full_refund",
  ...overrides,
});
assert.ok(validateRevocationContract(revokeBody("stealthx-chameleon-pro-lifetime")).product);
assert.throws(() => validateRevocationContract(revokeBody("stealthx-chameleon-pro-lifetime", { reason: "partial_refund" })),
  error => error.status === 400, "partial refund is not a revocation");
assert.throws(() => validateRevocationContract(revokeBody("stealthx-chameleon-pro-lifetime", { adjustmentEventId: undefined })),
  error => error.status === 400, "missing adjustment event rejected");

// --- Internal pre-HMAC rate limiter ----------------------------------------

{
  let now = 1_000_000;
  const limiter = createRequestRateLimiter({ limit: 2, windowMs: 60_000, now: () => now });
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), true);
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), true);
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), false, "third request inside the window is rejected");
  assert.strictEqual(limiter.allow({ ip: "203.0.113.2" }), true, "a distinct client keeps its own bucket");
  now += 60_000;
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), true, "window reset lifts the rejection");
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), true);
  assert.strictEqual(limiter.allow({ ip: "203.0.113.1" }), false, "the reset window enforces the limit again");
}

{
  let now = 2_000_000;
  const limiter = createRequestRateLimiter({ windowMs: 60_000, now: () => now });
  for (let index = 0; index < INTERNAL_RATE_LIMIT_MAX_BUCKETS; index += 1) {
    assert.strictEqual(limiter.allow({ ip: `198.51.${index >> 8}.${index & 255}` }), true);
  }
  assert.strictEqual(limiter.allow({ ip: "192.0.2.1" }), false, "bucket capacity fails closed for new clients");
  assert.strictEqual(limiter.allow({ ip: "198.51.0.0" }), true, "tracked clients are not evicted by the cap");
  now += 60_000;
  assert.strictEqual(limiter.allow({ ip: "192.0.2.1" }), true, "expired buckets are reclaimed before failing closed");
}

// --- sold_codes regression + contract tuple persistence --------------------

const active = [];
const recorded = soldCodes.recordSale({
  code: "PRO-TEST-TEST-TEST",
  tier: "pro",
  email: "test@example.invalid",
  stripeSessionId: "cs_test_refund_1",
  productKey: "vlabs_securecall_pro_lifetime",
  catalogVersion: CATALOG_VERSION,
  offerVersion: "securecall-pro-eur-1500-lifetime-v1",
  releaseId: "securecall-android-1.0.50-vc78017-api36",
  externalProductId: "stealthx-securecall-pro-lifetime",
  activationCodesRef: active,
});
assert.strictEqual(active.length, 1);
assert.strictEqual(recorded.catalogVersion, CATALOG_VERSION);
assert.strictEqual(recorded.offerVersion, "securecall-pro-eur-1500-lifetime-v1");
assert.strictEqual(recorded.releaseId, "securecall-android-1.0.50-vc78017-api36");
assert.strictEqual(recorded.externalProductId, "stealthx-securecall-pro-lifetime");
assert.strictEqual(active[0].catalogVersion, CATALOG_VERSION, "tuple survives activation-code merge");
assert.strictEqual(active[0].releaseId, "securecall-android-1.0.50-vc78017-api36");
assert.ok(!Object.prototype.hasOwnProperty.call(recorded, "email"), "buyer email is never persisted");

// Reuse with identical tuple is idempotent; drift on any contract field fails closed.
const reused = soldCodes.recordSale({
  code: "PRO-XXXX-XXXX-XXXX",
  tier: "pro",
  stripeSessionId: "cs_test_refund_1",
  productKey: "vlabs_securecall_pro_lifetime",
  catalogVersion: CATALOG_VERSION,
  offerVersion: "securecall-pro-eur-1500-lifetime-v1",
  releaseId: "securecall-android-1.0.50-vc78017-api36",
  externalProductId: "stealthx-securecall-pro-lifetime",
});
assert.strictEqual(reused.code, "PRO-TEST-TEST-TEST", "idempotent reuse returns the original code");
assert.throws(() => soldCodes.recordSale({
  code: "PRO-YYYY-YYYY-YYYY",
  tier: "pro",
  stripeSessionId: "cs_test_refund_1",
  productKey: "vlabs_securecall_pro_lifetime",
  catalogVersion: CATALOG_VERSION,
  offerVersion: "securecall-pro-eur-1500-lifetime-v2",
  releaseId: "securecall-android-1.0.50-vc78017-api36",
  externalProductId: "stealthx-securecall-pro-lifetime",
}), /sale_binding_mismatch/, "reuse with offer drift is rejected");

// Legacy entry without contract fields stays readable but is not immutable evidence.
const legacyStore = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8"));
legacyStore.codes.push({
  code: "PREM-LEGA-CY00-0001",
  tier: "premium",
  maxUses: 2,
  currentUses: 0,
  usedBy: [],
  stripeSessionId: "cs_test_legacy_1",
  productKey: "premium_lifetime",
  createdAt: new Date().toISOString(),
  used: false,
});
fs.writeFileSync(process.env.SOLD_CODES_FILE, JSON.stringify(legacyStore, null, 2), "utf8");
const legacyLoaded = soldCodes.loadAsActivationCodes().find(c => c.code === "PREM-LEGA-CY00-0001");
assert.ok(legacyLoaded, "legacy entry without contract fields remains readable");
assert.strictEqual(legacyLoaded.catalogVersion, null);
assert.throws(() => soldCodes.recordSale({
  code: "PREM-NEW0-NEW0-NEW0",
  tier: "premium",
  stripeSessionId: "cs_test_legacy_1",
  productKey: "premium_lifetime",
  catalogVersion: CATALOG_VERSION,
  offerVersion: "securecall-premium-eur-2500-lifetime-v1",
  releaseId: "securecall-android-1.0.50-vc78017-api36",
  externalProductId: "stealthx-securecall-premium-lifetime",
}), /sale_binding_mismatch/, "legacy entry cannot be upgraded into contract evidence by reuse");

assert.deepStrictEqual(soldCodes.revokeByStripeSession("cs_test_refund_1", active), { found: true, duplicate: false });
assert.strictEqual(active.length, 0);
assert.strictEqual(soldCodes.loadAsActivationCodes().filter(c => c.code === "PRO-TEST-TEST-TEST").length, 0);
assert.deepStrictEqual(soldCodes.revokeByStripeSession("cs_test_refund_1", active), { found: true, duplicate: true });

// --- Route-level lifecycle ---------------------------------------------------

const routes = {};
const app = { post: (routePath, handler) => { routes[routePath] = handler; } };
const activationCodes = [];
fulfillment.setupVlabsFulfillmentRoute(app, activationCodes);

function makeRes() {
  return {
    statusCode: 200,
    payload: null,
    status(code) { this.statusCode = code; return this; },
    json(payload) { this.payload = payload; return this; },
  };
}

function signedReq(requestBody, ip = "127.0.0.1") {
  const raw = JSON.stringify(requestBody);
  const ts = Math.floor(Date.now() / 1000).toString();
  const sig = crypto.createHmac("sha256", process.env.VLABS_FULFILLMENT_SECRET).update(`${ts}.${raw}`).digest("hex");
  return { get: header => ({ "x-vlabs-timestamp": ts, "x-vlabs-signature": sig }[header]), body: requestBody, ip };
}

let orderCounter = 0;
function fulfillBody(productId, overrides = {}) {
  orderCounter += 1;
  return contractBody(productId, {
    externalOrderId: `cs_test_route_${orderCounter}`,
    paymentReference: `pi_test_route_${orderCounter}`,
    paymentEventId: `evt_test_route_${orderCounter}_paid`,
    ...overrides,
  });
}

function revokeFor(fulfillRequestBody, overrides = {}) {
  return {
    externalOrderId: fulfillRequestBody.externalOrderId,
    productId: fulfillRequestBody.productId,
    catalogVersion: fulfillRequestBody.catalogVersion,
    offerVersion: fulfillRequestBody.offerVersion,
    releaseId: fulfillRequestBody.releaseId,
    paymentReference: fulfillRequestBody.paymentReference,
    adjustmentEventId: `evt_${fulfillRequestBody.externalOrderId.slice(3)}`,
    reason: "stripe_full_refund",
    ...overrides,
  };
}

(async () => {
  // Six-product mapping: every individual lifetime product fulfills exactly.
  for (const productId of Object.keys(EXPECTED_PRODUCTS)) {
    const res = makeRes();
    await routes["/internal/vlabs/fulfill"](signedReq(fulfillBody(productId)), res);
    assert.strictEqual(res.statusCode, 200, `fulfill ${productId}: ${JSON.stringify(res.payload)}`);
    assert.strictEqual(res.payload.fulfilled, true);
  }
  const orders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  const fulfilledOrders = Object.values(orders).filter(order => order.status === "FULFILLED");
  assert.strictEqual(fulfilledOrders.length, 6, "six orders persisted with FULFILLED status");
  for (const order of fulfilledOrders) {
    const expected = EXPECTED_PRODUCTS[order.productId];
    assert.strictEqual(order.catalogVersion, CATALOG_VERSION);
    assert.strictEqual(order.offerVersion, expected.offerVersion);
    assert.strictEqual(order.releaseId, expected.releaseId);
    assert.strictEqual(order.amount, expected.amount);
    assert.strictEqual(order.currency, expected.currency);
    assert.ok(/^pi_/.test(order.paymentReference));
  }
  const soldStore = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8"));
  const contractedSales = soldStore.codes.filter(entry => entry.catalogVersion === CATALOG_VERSION
    && /^cs_test_route_/.test(entry.stripeSessionId || ""));
  assert.strictEqual(contractedSales.length, 6, "six sold codes carry the contract tuple");

  // Per-product email identity.
  const securechatMail = sentEmails.find(mail => mail.subject.includes("SecureChat Elite"));
  assert.ok(securechatMail, "SecureChat Elite email was sent");
  assert.ok(securechatMail.htmlContent.includes("https://securechat.stealthx.tech/"), "SecureChat email uses the SecureChat identity");
  const chameleonMail = sentEmails.find(mail => mail.subject.includes("Chameleon Pro"));
  assert.ok(chameleonMail && chameleonMail.htmlContent.includes("https://chameleon.stealthx.tech/"), "Chameleon email uses the Chameleon identity");

  // Missing / drift rejection before persistence.
  for (const [overrides, expectedStatus] of [
    [{ offerVersion: undefined }, 400],
    [{ amount: 1 }, 409],
    [{ currency: "usd" }, 409],
    [{ releaseId: "drifted-release" }, 409],
    [{ catalogVersion: "stealthx-lifetime-v2" }, 409],
    [{ paymentStatus: "unpaid" }, 409],
  ]) {
    const res = makeRes();
    await routes["/internal/vlabs/fulfill"](signedReq(fulfillBody("stealthx-securecall-pro-lifetime", overrides)), res);
    assert.strictEqual(res.statusCode, expectedStatus, `drift ${JSON.stringify(overrides)} -> ${expectedStatus}`);
  }

  // Suite is never accepted.
  const suiteRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(fulfillBody("stealthx-suite-lifetime")), suiteRes);
  assert.strictEqual(suiteRes.statusCode, 409);

  // Short secret fails closed.
  process.env.VLABS_FULFILLMENT_SECRET = "short";
  const shortSecretRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(fulfillBody("stealthx-securecall-pro-lifetime")), shortSecretRes);
  assert.strictEqual(shortSecretRes.statusCode, 503);
  process.env.VLABS_FULFILLMENT_SECRET = SECRET;

  // Rate limiting happens before HMAC parsing and covers both internal routes.
  const attackerIp = "203.0.113.60";
  for (let attempt = 0; attempt < INTERNAL_RATE_LIMIT_MAX_REQUESTS; attempt += 1) {
    const rateRes = makeRes();
    await routes["/internal/vlabs/fulfill"]({ get: () => "", body: {}, ip: attackerIp }, rateRes);
    assert.strictEqual(rateRes.statusCode, 401);
  }
  const limitedFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"]({
    get: () => { throw new Error("authentication must not run after rate limiting"); },
    body: {},
    ip: attackerIp,
  }, limitedFulfillRes);
  assert.strictEqual(limitedFulfillRes.statusCode, 429);
  const limitedRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"]({
    get: () => { throw new Error("authentication must not run after rate limiting"); },
    body: {},
    ip: attackerIp,
  }, limitedRevokeRes);
  assert.strictEqual(limitedRevokeRes.statusCode, 429);

  // Duplicate binding: identical tuple is idempotent, tuple drift is rejected.
  const duplicateTarget = fulfillBody("stealthx-securecall-premium-lifetime");
  const firstRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(duplicateTarget), firstRes);
  assert.strictEqual(firstRes.payload.fulfilled, true);
  const duplicateRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(duplicateTarget), duplicateRes);
  assert.strictEqual(duplicateRes.statusCode, 200);
  assert.strictEqual(duplicateRes.payload.duplicate, true);
  const replayEventRes = makeRes();
  await routes["/internal/vlabs/fulfill"](
    signedReq({ ...duplicateTarget, paymentEventId: "evt_test_manual_replay" }),
    replayEventRes,
  );
  assert.strictEqual(replayEventRes.statusCode, 200);
  assert.strictEqual(replayEventRes.payload.duplicate, true, "new event id may replay the same paid order");
  const driftedRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq({ ...duplicateTarget, offerVersion: "securecall-premium-eur-2500-lifetime-v9" }), driftedRes);
  assert.strictEqual(driftedRes.statusCode, 409, "duplicate order with contract drift fails closed");

  // Refund revoke on a fulfilled order, then idempotent duplicate, then drifted duplicate.
  const revokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(duplicateTarget)), revokeRes);
  assert.strictEqual(revokeRes.statusCode, 200);
  assert.strictEqual(revokeRes.payload.revoked, true);
  assert.strictEqual(soldCodes.isReversed(duplicateTarget.externalOrderId), true);
  const emailCountAfterRevoke = sentEmails.length;
  const revokedFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(duplicateTarget), revokedFulfillRes);
  assert.strictEqual(revokedFulfillRes.statusCode, 409, "revoked order cannot be fulfilled again");
  assert.strictEqual(sentEmails.length, emailCountAfterRevoke, "revoked order never resends activation email");
  const duplicateRevokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(duplicateTarget)), duplicateRevokeRes);
  assert.strictEqual(duplicateRevokeRes.statusCode, 200);
  assert.strictEqual(duplicateRevokeRes.payload.duplicate, true, "duplicate revoke stays idempotent");
  const mismatchedPaymentRevokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](
    signedReq(revokeFor(duplicateTarget, { paymentReference: "pi_test_wrong_payment" })),
    mismatchedPaymentRevokeRes,
  );
  assert.strictEqual(mismatchedPaymentRevokeRes.statusCode, 409, "duplicate revoke with payment drift fails closed");
  const driftedRevokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(duplicateTarget, { releaseId: "drifted-release" })), driftedRevokeRes);
  assert.strictEqual(driftedRevokeRes.statusCode, 409, "revoke with tuple drift fails closed");

  // Dispute is an accepted adjustment; partial refund is not.
  const disputeTarget = fulfillBody("stealthx-securechat-pro-lifetime");
  const disputeFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(disputeTarget), disputeFulfillRes);
  assert.strictEqual(disputeFulfillRes.payload.fulfilled, true);
  const disputeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(disputeTarget, { reason: "stripe_dispute" })), disputeRes);
  assert.strictEqual(disputeRes.payload.revoked, true);
  const partialRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(disputeTarget, { reason: "partial_refund" })), partialRes);
  assert.strictEqual(partialRes.statusCode, 400);

  // A payment-reference-only reversal must find and revoke the original
  // fulfilled order rather than creating a second hash-keyed tombstone.
  const referenceTarget = fulfillBody("stealthx-securecall-pro-lifetime");
  const referenceFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(referenceTarget), referenceFulfillRes);
  assert.strictEqual(referenceFulfillRes.payload.fulfilled, true);
  const referenceRevoke = revokeFor(referenceTarget);
  delete referenceRevoke.externalOrderId;
  const referenceRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(referenceRevoke), referenceRevokeRes);
  assert.strictEqual(referenceRevokeRes.payload.revoked, true);
  const referenceOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  assert.strictEqual(referenceOrders[referenceTarget.externalOrderId].status, "REVOKED");
  const referencePaymentKey = `payment_${crypto.createHash("sha256").update(referenceTarget.paymentReference).digest("hex").slice(0, 32)}`;
  assert.strictEqual(referenceOrders[referencePaymentKey], undefined, "payment-only revoke reuses the original order key");
  assert.strictEqual(soldCodes.findByStripeSession(referenceTarget.externalOrderId).revoked, true);

  // Revoke-before-fulfill tombstone: later fulfillment fails closed.
  const tombstoneTarget = fulfillBody("stealthx-chameleon-elite-lifetime");
  const tombstoneRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(tombstoneTarget)), tombstoneRes);
  assert.strictEqual(tombstoneRes.statusCode, 200);
  assert.strictEqual(tombstoneRes.payload.tombstoned, true);
  const lateFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(tombstoneTarget), lateFulfillRes);
  assert.strictEqual(lateFulfillRes.statusCode, 409, "fulfillment after revoke tombstone fails closed");
  assert.throws(() => soldCodes.recordSale({
    code: "ELIT-LATE-LATE-LATE",
    tier: "elite",
    stripeSessionId: tombstoneTarget.externalOrderId,
    productKey: "chameleon_elite_lifetime",
    catalogVersion: CATALOG_VERSION,
    offerVersion: "chameleon-elite-eur-1900-lifetime-v1",
    releaseId: "chameleon-android-0.1.13-alpha-vc14-api36",
    externalProductId: "stealthx-chameleon-elite-lifetime",
  }), /payment_reversed/, "sold_codes reversal tombstone blocks late sale recording");
  const tombstoneAgainRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(tombstoneTarget)), tombstoneAgainRes);
  assert.strictEqual(tombstoneAgainRes.payload.duplicate, true, "duplicate revoke of a tombstone is idempotent");

  // Stripe may deliver a refund/dispute before VLABS has a local session order.
  // The payment-intent tombstone must still block a later fulfillment.
  const paymentOnlyTarget = fulfillBody("stealthx-securechat-elite-lifetime");
  const paymentOnlySale = soldCodes.recordSale({
    code: "ELIT-PAYM-ENT0-ONLY",
    tier: "elite",
    stripeSessionId: paymentOnlyTarget.externalOrderId,
    paymentReference: paymentOnlyTarget.paymentReference,
    productKey: "securechat_elite_lifetime",
    catalogVersion: CATALOG_VERSION,
    offerVersion: paymentOnlyTarget.offerVersion,
    releaseId: paymentOnlyTarget.releaseId,
    externalProductId: paymentOnlyTarget.productId,
    activationCodesRef: activationCodes,
  });
  const paymentOnlyRevoke = revokeFor(paymentOnlyTarget);
  delete paymentOnlyRevoke.externalOrderId;
  const paymentOnlyRevokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(paymentOnlyRevoke), paymentOnlyRevokeRes);
  assert.strictEqual(paymentOnlyRevokeRes.statusCode, 200);
  assert.strictEqual(paymentOnlyRevokeRes.payload.tombstoned, true);
  assert.strictEqual(soldCodes.isReversed(null, paymentOnlyTarget.paymentReference), true);
  assert.strictEqual(
    soldCodes.load().find(entry => entry.code === paymentOnlySale.code).revoked,
    true,
    "payment-reference-only reversal revokes the matching sold code",
  );
  const paymentOnlyLateFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(paymentOnlyTarget), paymentOnlyLateFulfillRes);
  assert.strictEqual(paymentOnlyLateFulfillRes.statusCode, 409, "payment-only reversal blocks later fulfillment");

  // A payment tombstone plus the later session tombstone for the same
  // immutable contract keep a payment-reference-only reversal idempotent.
  const dualTombstoneTarget = fulfillBody("stealthx-securecall-pro-lifetime");
  const dualTombstoneRevoke = revokeFor(dualTombstoneTarget);
  delete dualTombstoneRevoke.externalOrderId;
  const dualFirstRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(dualTombstoneRevoke, "198.51.100.10"), dualFirstRevokeRes);
  assert.strictEqual(dualFirstRevokeRes.payload.tombstoned, true);
  const dualLateFulfillRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(dualTombstoneTarget, "198.51.100.10"), dualLateFulfillRes);
  assert.strictEqual(dualLateFulfillRes.statusCode, 409, "fulfillment after payment reversal still fails closed");
  const dualOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  const dualPaymentKey = `payment_${crypto.createHash("sha256").update(dualTombstoneTarget.paymentReference).digest("hex").slice(0, 32)}`;
  assert.strictEqual(dualOrders[dualPaymentKey].status, "TOMBSTONED", "payment tombstone exists");
  assert.strictEqual(dualOrders[dualTombstoneTarget.externalOrderId].status, "TOMBSTONED", "session tombstone exists");
  const dualRetryRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(dualTombstoneRevoke, "198.51.100.10"), dualRetryRevokeRes);
  assert.strictEqual(dualRetryRevokeRes.statusCode, 200);
  assert.strictEqual(dualRetryRevokeRes.payload.duplicate, true,
    "payment + session tombstones keep the reversal idempotent");

  // Ambiguous scans still fail closed: a live order beside a tombstone for
  // the same payment reference is never silently treated as a duplicate.
  const ambiguousTarget = fulfillBody("stealthx-securecall-premium-lifetime");
  const ambiguousPaymentKey = `payment_${crypto.createHash("sha256").update(ambiguousTarget.paymentReference).digest("hex").slice(0, 32)}`;
  const ambiguousOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  ambiguousOrders[ambiguousTarget.externalOrderId] = {
    productId: ambiguousTarget.productId,
    tier: "premium",
    catalogVersion: CATALOG_VERSION,
    offerVersion: ambiguousTarget.offerVersion,
    releaseId: ambiguousTarget.releaseId,
    amount: 2500,
    currency: "eur",
    paymentReference: ambiguousTarget.paymentReference,
    status: "FULFILLED",
    createdAt: new Date().toISOString(),
  };
  ambiguousOrders[ambiguousPaymentKey] = {
    productId: ambiguousTarget.productId,
    tier: "premium",
    catalogVersion: CATALOG_VERSION,
    offerVersion: ambiguousTarget.offerVersion,
    releaseId: ambiguousTarget.releaseId,
    amount: 2500,
    currency: "eur",
    paymentReference: ambiguousTarget.paymentReference,
    status: "TOMBSTONED",
    createdAt: new Date().toISOString(),
    tombstonedAt: new Date().toISOString(),
    revokeReason: "stripe_full_refund",
  };
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(ambiguousOrders), { mode: 0o600 });
  const ambiguousRevoke = revokeFor(ambiguousTarget);
  delete ambiguousRevoke.externalOrderId;
  const ambiguousRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(ambiguousRevoke, "198.51.100.11"), ambiguousRevokeRes);
  assert.strictEqual(ambiguousRevokeRes.statusCode, 409, "live order beside a tombstone fails closed");

  // Legacy terminal rows remain idempotent only when their historical tier
  // agrees with the product contract. A contradictory row is not accepted as
  // a harmless duplicate merely because it lacks the modern tuple fields.
  const legacyTierTarget = fulfillBody("stealthx-securecall-pro-lifetime");
  const legacyTierOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  legacyTierOrders.payment_legacy_tier_conflict = {
    productId: legacyTierTarget.productId,
    tier: "premium",
    paymentReference: legacyTierTarget.paymentReference,
    status: "TOMBSTONED",
  };
  legacyTierOrders[legacyTierTarget.externalOrderId] = {
    productId: legacyTierTarget.productId,
    tier: "pro",
    paymentReference: legacyTierTarget.paymentReference,
    status: "REVOKED",
  };
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(legacyTierOrders), { mode: 0o600 });
  const legacyTierRevoke = revokeFor(legacyTierTarget);
  delete legacyTierRevoke.externalOrderId;
  const legacyTierRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(legacyTierRevoke, "198.51.100.13"), legacyTierRevokeRes);
  assert.strictEqual(legacyTierRevokeRes.statusCode, 409, "legacy terminal tier mismatch fails closed");

  // Payment-reference-only reversal must still reach legacy sold entries that
  // predate paymentReferenceHash once the scan recovers the canonical cs_ key.
  const hashlessTarget = fulfillBody("stealthx-chameleon-pro-lifetime");
  const hashlessSale = soldCodes.recordSale({
    code: "PRO0-NOH0-ASH0-0001",
    tier: "pro",
    stripeSessionId: hashlessTarget.externalOrderId,
    productKey: "chameleon_pro_lifetime",
    activationCodesRef: activationCodes,
  });
  assert.strictEqual(hashlessSale.paymentReferenceHash, null, "legacy sold entry has no payment reference hash");
  const hashlessOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  hashlessOrders[hashlessTarget.externalOrderId] = {
    productId: hashlessTarget.productId,
    tier: "pro",
    catalogVersion: CATALOG_VERSION,
    offerVersion: hashlessTarget.offerVersion,
    releaseId: hashlessTarget.releaseId,
    amount: 900,
    currency: "eur",
    paymentReference: hashlessTarget.paymentReference,
    paymentEventId: hashlessTarget.paymentEventId,
    code: hashlessSale.code,
    status: "FULFILLED",
    createdAt: new Date().toISOString(),
  };
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(hashlessOrders), { mode: 0o600 });
  const hashlessRevoke = revokeFor(hashlessTarget);
  delete hashlessRevoke.externalOrderId;
  const hashlessRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(hashlessRevoke, "198.51.100.12"), hashlessRevokeRes);
  assert.strictEqual(hashlessRevokeRes.statusCode, 200);
  assert.strictEqual(hashlessRevokeRes.payload.revoked, true);
  assert.strictEqual(soldCodes.findByStripeSession(hashlessTarget.externalOrderId).revoked, true,
    "legacy sold entry without paymentReferenceHash is revoked via the discovered order key");
  assert.ok(!activationCodes.some(candidate => candidate.code === hashlessSale.code),
    "revoked legacy code leaves the activation set");

  // Legacy rows must never be mutated before terminal status and existing
  // payment-reference checks have passed.
  const legacyBlockedTarget = fulfillBody("stealthx-securecall-pro-lifetime", {
    externalOrderId: "cs_test_legacy_blocked",
    paymentReference: "pi_test_legacy_blocked",
  });
  const legacyMismatchTarget = fulfillBody("stealthx-securecall-pro-lifetime", {
    externalOrderId: "cs_test_legacy_mismatch",
    paymentReference: "pi_test_legacy_new",
  });
  const guardedLegacyOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  guardedLegacyOrders[legacyBlockedTarget.externalOrderId] = {
    productId: legacyBlockedTarget.productId,
    tier: "pro",
    status: "TOMBSTONED",
    paymentReference: legacyBlockedTarget.paymentReference,
  };
  guardedLegacyOrders[legacyMismatchTarget.externalOrderId] = {
    productId: legacyMismatchTarget.productId,
    tier: "pro",
    status: "FULFILLED",
    paymentReference: "pi_test_legacy_original",
  };
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(guardedLegacyOrders), { mode: 0o600 });
  const blockedLegacyRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(legacyBlockedTarget), blockedLegacyRes);
  assert.strictEqual(blockedLegacyRes.statusCode, 409);
  const mismatchLegacyRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(legacyMismatchTarget), mismatchLegacyRes);
  assert.strictEqual(mismatchLegacyRes.statusCode, 409);
  const mismatchLegacyRevokeRes = makeRes();
  routes["/internal/vlabs/revoke"](signedReq(revokeFor(legacyMismatchTarget)), mismatchLegacyRevokeRes);
  assert.strictEqual(mismatchLegacyRevokeRes.statusCode, 409);
  const guardedLegacyAfter = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  assert.deepStrictEqual(guardedLegacyAfter[legacyBlockedTarget.externalOrderId], guardedLegacyOrders[legacyBlockedTarget.externalOrderId]);
  assert.deepStrictEqual(guardedLegacyAfter[legacyMismatchTarget.externalOrderId], guardedLegacyOrders[legacyMismatchTarget.externalOrderId]);

  // Pre-contract receiver orders remain revocable and are bound during the
  // signed request instead of wedging on missing tuple fields.
  const legacyTarget = fulfillBody("stealthx-securecall-pro-lifetime", {
    externalOrderId: "cs_test_legacy_receiver",
    paymentReference: "pi_test_legacy_receiver",
    paymentEventId: "evt_test_legacy_receiver_paid",
  });
  soldCodes.recordSale({
    code: "PRO-LEGA-CY00-0001",
    tier: "pro",
    stripeSessionId: legacyTarget.externalOrderId,
    productKey: "vlabs_securecall_pro_lifetime",
    activationCodesRef: activationCodes,
  });
  const legacyOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  legacyOrders[legacyTarget.externalOrderId] = {
    productId: legacyTarget.productId,
    tier: "pro",
    code: "PRO-LEGA-CY00-0001",
    status: "FULFILLED",
    createdAt: new Date().toISOString(),
  };
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(legacyOrders), { mode: 0o600 });
  const legacyRevokeRes = makeRes();
  await routes["/internal/vlabs/revoke"](signedReq(revokeFor(legacyTarget)), legacyRevokeRes);
  assert.strictEqual(legacyRevokeRes.statusCode, 200);
  assert.strictEqual(legacyRevokeRes.payload.revoked, true);
  const migratedLegacyOrder = JSON.parse(
    fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"),
  )[legacyTarget.externalOrderId];
  assert.strictEqual(migratedLegacyOrder.catalogVersion, CATALOG_VERSION);
  assert.strictEqual(migratedLegacyOrder.offerVersion, legacyTarget.offerVersion);
  assert.strictEqual(migratedLegacyOrder.releaseId, legacyTarget.releaseId);

  // Concurrency: a foreign-held orders lock fails the mutation closed.
  fs.writeFileSync(`${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`, "999999:foreignowner\n", { mode: 0o600 });
  const busyRes = makeRes();
  await routes["/internal/vlabs/fulfill"](signedReq(fulfillBody("stealthx-securecall-pro-lifetime")), busyRes);
  assert.strictEqual(busyRes.statusCode, 503, "busy order store fails closed");
  fs.unlinkSync(`${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`);

  // Concurrency: a foreign-held sold-codes lock blocks sale recording.
  fs.writeFileSync(`${process.env.SOLD_CODES_FILE}.lock`, "999999:foreignowner\n", { mode: 0o600 });
  assert.throws(() => soldCodes.recordSale({
    code: "PRO-BUSY-BUSY-BUSY",
    tier: "pro",
    stripeSessionId: "cs_test_busy_1",
    productKey: "vlabs_securecall_pro_lifetime",
  }), /sold_code_store_busy/);
  fs.unlinkSync(`${process.env.SOLD_CODES_FILE}.lock`);

  // Corrupt durable state must never be interpreted as an empty order registry.
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "[]", { mode: 0o600 });
  const corruptStoreRes = makeRes();
  await routes["/internal/vlabs/fulfill"](
    signedReq(fulfillBody("stealthx-securecall-pro-lifetime")),
    corruptStoreRes,
  );
  assert.strictEqual(corruptStoreRes.statusCode, 503, "invalid order state fails closed");

  // Deployment regression: behind the single trusted nginx hop
  // (TRUST_PROXY=true) the pre-HMAC limiter must bucket the rightmost
  // X-Forwarded-For entry, so distinct forwarded clients stay isolated and
  // client-supplied multi-hop prefixes are never trusted.
  const proxyDataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-vlabs-proxy-"));
  fs.writeFileSync(path.join(proxyDataDir, "wallets.json"), JSON.stringify({ wallets: [] }));
  const proxyOutput = { value: "" };
  const proxyChild = spawn(process.execPath, ["src/server.js"], {
    cwd: path.resolve(__dirname, "../.."),
    env: {
      HOME: process.env.HOME || "",
      PATH: process.env.PATH || "",
      NODE_ENV: "test",
      PORT: "0",
      DATA_DIR: proxyDataDir,
      WALLETS_FILE: path.join(proxyDataDir, "wallets.json"),
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_RTDN_ENABLED: "false",
      LEGACY_STRIPE_CHECKOUT_ENABLED: "false",
      TRUST_PROXY: "true",
      VLABS_FULFILLMENT_SECRET: SECRET,
      VLABS_FULFILLMENT_ORDERS_FILE: path.join(proxyDataDir, "orders.json"),
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  proxyChild.stdout.on("data", chunk => { proxyOutput.value += chunk.toString(); });
  proxyChild.stderr.on("data", chunk => { proxyOutput.value += chunk.toString(); });
  try {
    const portDeadline = Date.now() + 10000;
    let proxyPort = null;
    while (proxyPort === null && Date.now() < portDeadline) {
      const match = proxyOutput.value.match(/Server running on port (\d+)/);
      if (match) proxyPort = Number(match[1]);
      else await new Promise(resolve => setTimeout(resolve, 25));
    }
    assert.ok(proxyPort, `proxy test server did not report its port\n${proxyOutput.value}`);

    const postFulfill = forwardedFor => new Promise((resolvePromise, rejectPromise) => {
      const request = http.request({
        host: "127.0.0.1",
        port: proxyPort,
        path: "/internal/vlabs/fulfill",
        method: "POST",
        headers: { "Content-Type": "application/json", "X-Forwarded-For": forwardedFor },
        timeout: 2000,
      }, response => {
        response.resume();
        response.on("end", () => resolvePromise(response.statusCode));
      });
      request.once("timeout", () => request.destroy(new Error("fulfill request timed out")));
      request.once("error", rejectPromise);
      request.end("{}");
    });

    const proxiedClient = "203.0.113.60";
    for (let attempt = 0; attempt < INTERNAL_RATE_LIMIT_MAX_REQUESTS; attempt += 1) {
      assert.strictEqual(await postFulfill(proxiedClient), 401,
        "unsigned request passes the limiter and fails authentication");
    }
    assert.strictEqual(await postFulfill(proxiedClient), 429,
      "requests beyond the limit from the same forwarded client are throttled");
    assert.strictEqual(await postFulfill("203.0.113.61"), 401,
      "a distinct forwarded client keeps its own bucket");
    assert.strictEqual(await postFulfill(`198.51.100.9, ${proxiedClient}`), 429,
      "a spoofed multi-hop prefix is ignored; the trusted single hop is bucketed");
  } finally {
    proxyChild.kill("SIGKILL");
    fs.rmSync(proxyDataDir, { recursive: true, force: true });
  }

  global.fetch = realFetch;
  fs.rmSync(directory, { recursive: true, force: true });
  console.log("vlabs_fulfillment.test.js ok");
})().catch(error => {
  global.fetch = realFetch;
  fs.rmSync(directory, { recursive: true, force: true });
  console.error(error);
  process.exit(1);
});
