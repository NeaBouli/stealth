"use strict";

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-vlabs-payment-"));
process.env.SOLD_CODES_FILE = path.join(directory, "sold_codes.json");
process.env.VLABS_FULFILLMENT_ORDERS_FILE = path.join(directory, "orders.json");
process.env.VLABS_FULFILLMENT_SECRET = "v".repeat(32);

const {
  setupVlabsFulfillmentRoute,
  verifySignature,
  isRevocationReason,
  createRequestRateLimiter,
  PRODUCTS,
} = require("../payments/vlabs_fulfillment");
const soldCodes = require("../payments/sold_codes");

function signedRequest(body) {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const serialized = JSON.stringify(body);
  const signature = crypto.createHmac("sha256", process.env.VLABS_FULFILLMENT_SECRET)
    .update(`${timestamp}.${serialized}`).digest("hex");
  return {
    body,
    get(name) {
      if (name === "x-vlabs-timestamp") return timestamp;
      if (name === "x-vlabs-signature") return signature;
      return "";
    }
  };
}

function paidBody(externalOrderId, productId, customerEmail = "transient@example.invalid") {
  const product = PRODUCTS[productId];
  return {
    externalOrderId,
    productId,
    customerEmail,
    paymentProvider: "stripe",
    paymentStatus: "paid",
    paymentEventId: `evt_${externalOrderId.slice(3)}`,
    paymentReference: `pi_${externalOrderId.slice(3)}`,
    amount: product.amount,
    currency: product.currency,
  };
}

function response() {
  return {
    statusCode: 200,
    body: null,
    status(code) { this.statusCode = code; return this; },
    json(value) { this.body = value; return this; }
  };
}

async function run() {
  const timestamp = Math.floor(Date.now() / 1000).toString();
  const body = JSON.stringify({ externalOrderId: "cs_test_123", productId: "stealthx-securecall-pro-lifetime" });
  const signature = crypto.createHmac("sha256", process.env.VLABS_FULFILLMENT_SECRET)
    .update(`${timestamp}.${body}`).digest("hex");
  assert.strictEqual(verifySignature(process.env.VLABS_FULFILLMENT_SECRET, timestamp, body, signature), true);
  assert.strictEqual(verifySignature("short", timestamp, body, signature), false);
  assert.strictEqual(verifySignature(process.env.VLABS_FULFILLMENT_SECRET, timestamp, `${body}x`, signature), false);
  assert.deepStrictEqual(Object.keys(PRODUCTS).sort(), [
    "stealthx-chameleon-elite-lifetime",
    "stealthx-chameleon-pro-lifetime",
    "stealthx-securecall-premium-lifetime",
    "stealthx-securecall-pro-lifetime",
    "stealthx-securechat-elite-lifetime",
    "stealthx-securechat-pro-lifetime",
  ]);
  assert.strictEqual(Object.prototype.hasOwnProperty.call(PRODUCTS, "stealthx-suite-lifetime"), false);
  assert.strictEqual(isRevocationReason("stripe_full_refund"), true);
  assert.strictEqual(isRevocationReason("stripe_dispute"), true);
  assert.strictEqual(isRevocationReason("partial_refund"), false);
  let limiterTime = 1000;
  const limiter = createRequestRateLimiter({ maxAttempts: 2, windowMs: 100, now: () => limiterTime });
  const limiterRequest = { ip: "127.0.0.1" };
  assert.strictEqual(limiter(limiterRequest, "fulfill"), true);
  assert.strictEqual(limiter(limiterRequest, "fulfill"), true);
  assert.strictEqual(limiter(limiterRequest, "fulfill"), false);
  assert.strictEqual(limiter(limiterRequest, "revoke"), true, "routes have separate limits");
  limiterTime += 101;
  assert.strictEqual(limiter(limiterRequest, "fulfill"), true, "expired windows reset");

  const routes = new Map();
  const delivered = [];
  const activationCodes = [];
  let codeCounter = 0;
  setupVlabsFulfillmentRoute({ post: (route, handler) => routes.set(route, handler) }, activationCodes, {
    generateActivationCode: tier => `${String(tier).toUpperCase()}-TEST-TEST-${++codeCounter}`,
    sendActivationCode: async (email, code, tier, options) => {
      delivered.push({ email, code, tier, options });
      return true;
    }
  });

  const products = [
    ["stealthx-securechat-pro-lifetime", "securechat_pro_lifetime", "SecureChat", "pro"],
    ["stealthx-securechat-elite-lifetime", "securechat_elite_lifetime", "SecureChat", "elite"],
    ["stealthx-chameleon-pro-lifetime", "chameleon_pro_lifetime", "Chameleon", "pro"],
    ["stealthx-chameleon-elite-lifetime", "chameleon_elite_lifetime", "Chameleon", "elite"],
  ];

  for (const [productId, productKey, productName, tier] of products) {
    const externalOrderId = `cs_test_${productKey}`;
    const request = signedRequest(paidBody(externalOrderId, productId));
    const first = response();
    await routes.get("/internal/vlabs/fulfill")(request, first);
    assert.strictEqual(first.statusCode, 200);
    assert.strictEqual(first.body.fulfilled, true);
    const activation = activationCodes.find(entry => entry.stripeSessionId === externalOrderId);
    assert.strictEqual(activation.productKey, productKey);
    assert.strictEqual(activation.tier, tier);
    const delivery = delivered.find(entry => entry.options.productKey === productKey);
    assert.strictEqual(delivery.options.productName, productName);

    const duplicate = response();
    await routes.get("/internal/vlabs/fulfill")(request, duplicate);
    assert.strictEqual(duplicate.body.duplicate, true);
  }
  assert.strictEqual(delivered.length, products.length, "duplicates must not resend activation email");

  const invalidProof = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest({
    ...paidBody("cs_test_invalid_proof", "stealthx-securechat-pro-lifetime"),
    paymentStatus: "unpaid",
  }), invalidProof);
  assert.strictEqual(invalidProof.statusCode, 409);
  assert.strictEqual(invalidProof.body.error, "Paid order proof mismatch");
  const proofMismatches = [
    { paymentProvider: "paypal" },
    { paymentEventId: "invalid_event" },
    { paymentReference: "invalid_payment" },
    { amount: 901 },
    { currency: "usd" },
  ];
  for (const [index, mismatch] of proofMismatches.entries()) {
    const rejected = response();
    await routes.get("/internal/vlabs/fulfill")(signedRequest({
      ...paidBody(`cs_test_proof_mismatch_${index}`, "stealthx-securechat-pro-lifetime"),
      ...mismatch,
    }), rejected);
    assert.strictEqual(rejected.statusCode, 409);
    assert.strictEqual(rejected.body.error, "Paid order proof mismatch");
  }

  const storedCodes = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8")).codes;
  assert.strictEqual(storedCodes.some(entry => Object.prototype.hasOwnProperty.call(entry, "email")), false);
  assert.strictEqual(fs.statSync(process.env.SOLD_CODES_FILE).mode & 0o777, 0o600);
  assert.strictEqual(fs.statSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE).mode & 0o777, 0o600);
  const storedOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  const storedOrder = storedOrders.cs_test_securechat_pro_lifetime;
  assert.match(storedOrder.paymentEventHash, /^[a-f0-9]{64}$/);
  assert.match(storedOrder.paymentReferenceHash, /^[a-f0-9]{64}$/);
  assert.strictEqual(Object.prototype.hasOwnProperty.call(storedOrder, "paymentEventId"), false);
  assert.strictEqual(Object.prototype.hasOwnProperty.call(storedOrder, "paymentReference"), false);

  const revokeBody = {
    externalOrderId: "cs_test_securechat_pro_lifetime",
    productId: "stealthx-securechat-pro-lifetime",
    reason: "stripe_full_refund",
    paymentProvider: "stripe",
    adjustmentEventId: "evt_refund_securechat_pro_lifetime",
    paymentReference: "pi_test_securechat_pro_lifetime",
  };
  const revoked = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest(revokeBody), revoked);
  assert.strictEqual(revoked.body.revoked, true);
  assert.strictEqual(activationCodes.some(entry => entry.stripeSessionId === revokeBody.externalOrderId), false);
  const fulfillAfterRevoke = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest(paidBody(
    revokeBody.externalOrderId,
    revokeBody.productId,
  )), fulfillAfterRevoke);
  assert.strictEqual(fulfillAfterRevoke.statusCode, 409);
  assert.strictEqual(fulfillAfterRevoke.body.error, "Payment was reversed");
  assert.strictEqual(delivered.length, products.length, "revoked orders must not resend activation email");
  const duplicateRevoke = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest(revokeBody), duplicateRevoke);
  assert.strictEqual(duplicateRevoke.body.duplicate, true);
  assert.throws(() => soldCodes.recordSale({
    code: "PRO-REVOKED-REUSE-TEST",
    tier: "pro",
    stripeSessionId: revokeBody.externalOrderId,
    productKey: "securechat_pro_lifetime",
    activationCodesRef: activationCodes,
  }), /payment_reversed/, "a revoked stored entry cannot be merged back into live activations");

  const mismatchedRevoke = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest({
    ...revokeBody,
    paymentReference: "pi_different_payment",
  }), mismatchedRevoke);
  assert.strictEqual(mismatchedRevoke.statusCode, 409);
  assert.strictEqual(mismatchedRevoke.body.error, "Order payment mismatch");

  const noOrderIdRevoke = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest({
    productId: "stealthx-chameleon-pro-lifetime",
    reason: "stripe_dispute",
    paymentProvider: "stripe",
    adjustmentEventId: "evt_dispute_without_order_id",
    paymentReference: "pi_test_chameleon_pro_lifetime",
  }), noOrderIdRevoke);
  assert.strictEqual(noOrderIdRevoke.body.revoked, true, "payment reference locates an existing receiver order");
  assert.strictEqual(
    activationCodes.some(entry => entry.stripeSessionId === "cs_test_chameleon_pro_lifetime"),
    false,
    "no-order-id revoke removes the existing activation code",
  );

  const concurrentRoutes = new Map();
  let releaseDelivery;
  const deliveryBlocked = new Promise(resolve => { releaseDelivery = resolve; });
  let deliveryStarted;
  const started = new Promise(resolve => { deliveryStarted = resolve; });
  setupVlabsFulfillmentRoute({ post: (route, handler) => concurrentRoutes.set(route, handler) }, activationCodes, {
    generateActivationCode: tier => `${String(tier).toUpperCase()}-LOCK-TEST-${++codeCounter}`,
    sendActivationCode: async () => {
      deliveryStarted();
      await deliveryBlocked;
      return true;
    },
  });
  const concurrentRequest = signedRequest(paidBody(
    "cs_test_concurrent_order",
    "stealthx-chameleon-pro-lifetime",
  ));
  const firstConcurrent = response();
  const firstPromise = concurrentRoutes.get("/internal/vlabs/fulfill")(concurrentRequest, firstConcurrent);
  await started;
  const secondConcurrent = response();
  await concurrentRoutes.get("/internal/vlabs/fulfill")(concurrentRequest, secondConcurrent);
  assert.strictEqual(secondConcurrent.statusCode, 409);
  assert.strictEqual(secondConcurrent.body.error, "Order registry is busy");
  releaseDelivery();
  await firstPromise;
  assert.strictEqual(firstConcurrent.body.fulfilled, true);
  const concurrentCodes = soldCodes.load().filter(entry => entry.stripeSessionId === "cs_test_concurrent_order");
  assert.strictEqual(concurrentCodes.length, 1, "cross-process lock path mints one activation code");
  assert.strictEqual(fs.existsSync(`${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`), false);

  const reversedBeforeFulfillment = {
    productId: "stealthx-securecall-pro-lifetime",
    reason: "stripe_full_refund",
    paymentProvider: "stripe",
    adjustmentEventId: "evt_refund_before_fulfillment",
    paymentReference: "pi_test_refund_before_fulfillment",
  };
  const earlyRevoke = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest(reversedBeforeFulfillment), earlyRevoke);
  assert.strictEqual(earlyRevoke.body.revoked, true, "out-of-order refund creates a payment tombstone");
  const lateFulfillment = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest({
    ...paidBody("cs_test_refund_before_fulfillment", reversedBeforeFulfillment.productId),
    paymentReference: reversedBeforeFulfillment.paymentReference,
  }), lateFulfillment);
  assert.strictEqual(lateFulfillment.statusCode, 409);
  assert.strictEqual(lateFulfillment.body.error, "Payment was reversed");

  const paymentOwner = paidBody("cs_test_unique_payment_owner", "stealthx-securecall-pro-lifetime");
  const ownerResponse = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest(paymentOwner), ownerResponse);
  assert.strictEqual(ownerResponse.body.fulfilled, true);
  const reusedPayment = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest({
    ...paidBody("cs_test_unique_payment_reuse", "stealthx-securecall-pro-lifetime"),
    paymentReference: paymentOwner.paymentReference,
  }), reusedPayment);
  assert.strictEqual(reusedPayment.statusCode, 409);
  assert.strictEqual(reusedPayment.body.error, "Payment is already bound to another order");
  const reusedEvent = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest({
    ...paidBody("cs_test_unique_event_reuse", "stealthx-securecall-pro-lifetime"),
    paymentEventId: paymentOwner.paymentEventId,
  }), reusedEvent);
  assert.strictEqual(reusedEvent.statusCode, 409);
  assert.strictEqual(reusedEvent.body.error, "Payment event is already bound to another order");

  const staleLockPath = `${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`;
  fs.writeFileSync(staleLockPath, "stale\n", { mode: 0o600 });
  const staleTime = new Date(Date.now() - 10 * 60 * 1000);
  fs.utimesSync(staleLockPath, staleTime, staleTime);
  const blockedByAbandonedLock = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest(paidBody(
    "cs_test_stale_lock_recovery",
    "stealthx-chameleon-elite-lifetime",
  )), blockedByAbandonedLock);
  assert.strictEqual(blockedByAbandonedLock.statusCode, 409);
  assert.strictEqual(blockedByAbandonedLock.body.error, "Order registry is busy");
  assert.strictEqual(fs.existsSync(staleLockPath), true, "abandoned locks are never reclaimed automatically");
  fs.unlinkSync(staleLockPath);

  const oldOwnerRoutes = new Map();
  let releaseOldOwner;
  let oldOwnerStarted;
  const oldOwnerReady = new Promise(resolve => { oldOwnerStarted = resolve; });
  const oldOwnerDelivery = new Promise(resolve => { releaseOldOwner = resolve; });
  setupVlabsFulfillmentRoute({ post: (route, handler) => oldOwnerRoutes.set(route, handler) }, activationCodes, {
    generateActivationCode: tier => `${String(tier).toUpperCase()}-OLD-LEASE-${++codeCounter}`,
    sendActivationCode: async () => { oldOwnerStarted(); await oldOwnerDelivery; return true; },
  });
  const oldOwnerResponse = response();
  const oldOwnerPromise = oldOwnerRoutes.get("/internal/vlabs/fulfill")(
    signedRequest(paidBody("cs_test_old_lease_owner", "stealthx-securechat-elite-lifetime")),
    oldOwnerResponse,
  );
  await oldOwnerReady;
  const displacedLock = `${staleLockPath}.displaced`;
  fs.renameSync(staleLockPath, displacedLock);
  fs.writeFileSync(staleLockPath, "replacement-owner\n", { mode: 0o600 });
  releaseOldOwner();
  await oldOwnerPromise;
  assert.strictEqual(oldOwnerResponse.statusCode, 503, "a displaced owner cannot commit after losing its lock inode");
  assert.strictEqual(fs.existsSync(staleLockPath), true, "a displaced owner cannot remove a replacement lock");
  fs.unlinkSync(staleLockPath);
  fs.unlinkSync(displacedLock);

  const soldStoreLock = `${process.env.SOLD_CODES_FILE}.lock`;
  fs.writeFileSync(soldStoreLock, "abandoned\n", { mode: 0o600 });
  assert.throws(() => soldCodes.recordSale({
    code: "PRO-LOCK-BUSY-TEST",
    tier: "pro",
    stripeSessionId: "cs_test_store_lock_busy",
    productKey: "securechat_pro_lifetime",
    activationCodesRef: activationCodes,
  }), /sold_code_store_busy/);
  assert.strictEqual(fs.existsSync(soldStoreLock), true, "sold-code locks are never reclaimed automatically");
  fs.unlinkSync(soldStoreLock);

  soldCodes.revokeByStripeSession("cs_test_refund_race", activationCodes, {
    paymentIntent: "pi_test_refund_race",
    productKey: "securechat_pro_lifetime",
    eventId: "evt_test_refund_race",
    reason: "stripe_full_refund",
  });
  assert.throws(() => soldCodes.recordSale({
    code: "PRO-LATE-SALE-TEST",
    tier: "pro",
    stripeSessionId: "cs_test_refund_race",
    productKey: "securechat_pro_lifetime",
    activationCodesRef: activationCodes,
  }), /payment_reversed/, "a persisted reversal blocks every later sale write");

  soldCodes.recordSale({
    code: "PRO-BINDING-TEST-01",
    tier: "pro",
    stripeSessionId: "cs_test_sale_binding",
    productKey: "securechat_pro_lifetime",
    activationCodesRef: activationCodes,
  });
  assert.throws(() => soldCodes.recordSale({
    code: "ELIT-BINDING-TEST-02",
    tier: "elite",
    stripeSessionId: "cs_test_sale_binding",
    productKey: "securechat_elite_lifetime",
    activationCodesRef: activationCodes,
  }), /sale_binding_mismatch/, "a Stripe session cannot be reused for another product or tier");

  const driftOrderId = "cs_test_missing_sold_record";
  const driftProductId = "stealthx-chameleon-elite-lifetime";
  const driftPaidBody = paidBody(driftOrderId, driftProductId);
  const driftFulfilled = response();
  await routes.get("/internal/vlabs/fulfill")(signedRequest(driftPaidBody), driftFulfilled);
  assert.strictEqual(driftFulfilled.body.fulfilled, true);
  const driftStore = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8"));
  driftStore.codes = driftStore.codes.filter(item => item.stripeSessionId !== driftOrderId);
  fs.writeFileSync(process.env.SOLD_CODES_FILE, JSON.stringify(driftStore), { mode: 0o600 });
  const driftRevoked = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest({
    externalOrderId: driftOrderId,
    productId: driftProductId,
    reason: "stripe_dispute",
    paymentProvider: "stripe",
    adjustmentEventId: "evt_test_missing_sold_record",
    paymentReference: driftPaidBody.paymentReference,
  }), driftRevoked);
  assert.strictEqual(driftRevoked.body.revoked, true, "store drift still persists a fail-closed revocation");
  assert.strictEqual(activationCodes.some(item => item.stripeSessionId === driftOrderId), false, "store drift removes any live activation");
  const driftOrders = JSON.parse(fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8"));
  assert.strictEqual(driftOrders[driftOrderId].status, "REVOKED");
  assert.strictEqual(soldCodes.isReversed(driftOrderId), true, "store drift persists the sold-store reversal tombstone");

  fs.writeFileSync(process.env.SOLD_CODES_FILE, JSON.stringify({
    codes: [{ code: "PRO-OLD-TEST-TEST", tier: "pro", email: "legacy@example.invalid" }]
  }), { mode: 0o644 });
  fs.writeFileSync(soldStoreLock, "migration-blocked\n", { mode: 0o600 });
  const loadedDespiteMigrationFailure = soldCodes.load();
  fs.unlinkSync(soldStoreLock);
  assert.strictEqual(loadedDespiteMigrationFailure.length, 1);
  assert.strictEqual(Object.prototype.hasOwnProperty.call(loadedDespiteMigrationFailure[0], "email"), false);
  const migrated = soldCodes.load();
  assert.strictEqual(Object.prototype.hasOwnProperty.call(migrated[0], "email"), false);
  const migratedFile = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8"));
  assert.strictEqual(Object.prototype.hasOwnProperty.call(migratedFile.codes[0], "email"), false);
  assert.strictEqual(fs.statSync(process.env.SOLD_CODES_FILE).mode & 0o777, 0o600);
}

run().then(() => {
  fs.rmSync(directory, { recursive: true, force: true });
  console.log("vlabs_fulfillment.test.js ok");
}).catch(error => {
  fs.rmSync(directory, { recursive: true, force: true });
  console.error(error);
  process.exit(1);
});
