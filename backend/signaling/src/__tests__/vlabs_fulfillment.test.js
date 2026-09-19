const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

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
  PRODUCTS, CATALOG_VERSION, MIN_FULFILLMENT_SECRET_LENGTH, INTERNAL_RATE_LIMIT_MAX_REQUESTS,
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

  global.fetch = realFetch;
  fs.rmSync(directory, { recursive: true, force: true });
  console.log("vlabs_fulfillment.test.js ok");
})().catch(error => {
  global.fetch = realFetch;
  fs.rmSync(directory, { recursive: true, force: true });
  console.error(error);
  process.exit(1);
});
