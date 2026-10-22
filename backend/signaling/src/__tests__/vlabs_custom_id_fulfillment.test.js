const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-vlabs-custom-id-"));
process.env.SOLD_CODES_FILE = path.join(directory, "sold.json");
process.env.VLABS_FULFILLMENT_ORDERS_FILE = path.join(directory, "orders.json");
process.env.IDS_FILE = path.join(directory, "ids.json");
process.env.PENDING_FILE = path.join(directory, "pending.json");
process.env.ID_HASH_PEPPER = "test-only-custom-id-pepper";
process.env.VLABS_FULFILLMENT_SECRET = "test-only-fulfillment-secret-over-32-characters";
delete process.env.VLABS_FULFILLMENT_TEST_SECRET;
delete process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED;

const fulfillment = require("../payments/vlabs_fulfillment");
let customIds = require("../custom_ids");
const soldCodes = require("../payments/sold_codes");
const activationCodes = [];
let routes;
let counter = 0;
const originalRename = fs.renameSync;

function setup() {
  routes = {};
  fulfillment.setupVlabsFulfillmentRoute({ post: (route, handler) => { routes[route] = handler; } }, activationCodes, {
    persistActivationCodes: () => { throw new Error("Custom IDs must not mutate app activation codes"); },
  });
}

function body(requestedId = "test-example", overrides = {}) {
  counter += 1;
  const length = typeof requestedId === "string" ? requestedId.trim().length : 0;
  const suffix = length >= 10 ? "standard" : length >= 5 ? "short" : "ultra";
  const productId = `stealthx-custom-id-${suffix}`;
  const product = fulfillment.CUSTOM_ID_PRODUCTS[productId];
  return {
    externalOrderId: `cs_live_custom_${counter}`, productId,
    catalogVersion: product.catalogVersion, offerVersion: product.offerVersion, releaseId: product.releaseId,
    paymentReference: `pi_test_custom_${counter}`, paymentEventId: `evt_test_custom_${counter}`,
    paymentStatus: "paid", amount: product.amount, currency: "eur",
    customerEmail: "buyer@example.invalid", requestedId, ...overrides,
  };
}

function revokeBody(order, overrides = {}) {
  return {
    externalOrderId: order.externalOrderId, productId: order.productId,
    catalogVersion: order.catalogVersion, offerVersion: order.offerVersion, releaseId: order.releaseId,
    paymentReference: order.paymentReference, adjustmentEventId: `evt_revoke_${counter}`,
    reason: "stripe_full_refund", ...overrides,
  };
}

async function request(route, payload, { signatureValid = true, secret = process.env.VLABS_FULFILLMENT_SECRET } = {}) {
  const timestamp = String(Math.floor(Date.now() / 1000));
  const signature = crypto.createHmac("sha256", secret)
    .update(`${timestamp}.${JSON.stringify(payload)}`).digest("hex");
  const res = {
    statusCode: 200, payload: null,
    status(status) { this.statusCode = status; return this; },
    json(value) { this.payload = value; return this; },
  };
  await routes[`/internal/vlabs/${route}`]({
    body: payload, ip: "192.0.2.1",
    get: header => header === "x-vlabs-timestamp" ? timestamp : signatureValid ? signature : "0".repeat(64),
  }, res);
  return res;
}

function read(file) { return JSON.parse(fs.readFileSync(file, "utf8")); }
function orders() { return read(process.env.VLABS_FULFILLMENT_ORDERS_FILE); }
function assertUtcTimestamp(value) {
  assert.strictEqual(typeof value, "string");
  assert.strictEqual(new Date(value).toISOString(), value);
}
function reload() {
  delete require.cache[require.resolve("../custom_ids")];
  customIds = require("../custom_ids");
  setup();
}
function failRename(target, occurrence = 1) {
  let count = 0;
  fs.renameSync = (from, to) => {
    if (to === target && ++count === occurrence) throw new Error("injected persistence failure");
    return originalRename(from, to);
  };
}

(async () => {
  setup();
  const disabled = body();
  for (const value of [undefined, "false", "1", "TRUE"]) {
    if (value === undefined) delete process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED;
    else process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED = value;
    assert.strictEqual((await request("fulfill", disabled)).statusCode, 409);
  }
  assert.strictEqual(fs.existsSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE), false);
  assert.strictEqual((await request("fulfill", disabled, { signatureValid: false })).statusCode, 401);
  process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED = "true";

  for (const id of [undefined, null, 42, "", "ab", "-abc", "abc-", "bad_id", "a".repeat(31)]) {
    assert.strictEqual((await request("fulfill", body(id, { requestedId: id }))).statusCode, 400);
  }
  for (const [override, status] of [
    [{ amount: 99 }, 409], [{ amount: 100.5 }, 400], [{ currency: "usd" }, 409],
    [{ catalogVersion: "drift" }, 409], [{ offerVersion: "drift" }, 409], [{ releaseId: "drift" }, 409],
    [{ paymentStatus: "unpaid" }, 409], [{ paymentReference: "ch_test" }, 400],
    [{ paymentEventId: null }, 400], [{ customerEmail: null }, 400], [{ requestedId: "abc" }, 409],
  ]) {
    assert.strictEqual((await request("fulfill", body("test-example", override))).statusCode, status);
  }
  for (const overrides of [
    { listAmount: 100, discount: { couponId: "CQgFczlb", percentOff: 50 } },
    { amount: 50, listAmount: 100, discount: { couponId: "CQgFczlb", percentOff: 50 } },
    { discount: { couponId: "other-coupon", percentOff: 50 } },
    { amount: 50 },
    { listAmount: 99 },
  ]) {
    const result = await request("fulfill", body("test-example", overrides));
    assert.strictEqual(result.statusCode, 409, "Custom IDs keep exact pricing and reject discounts");
  }
  assert.strictEqual(fs.existsSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE), false,
    "invalid contracts cannot create a Custom ID order");
  const pepper = process.env.ID_HASH_PEPPER;
  delete process.env.ID_HASH_PEPPER;
  assert.strictEqual((await request("fulfill", body())).statusCode, 503);
  process.env.ID_HASH_PEPPER = pepper;

  // Real registry checks, length boundaries, normalized identity and app separation.
  const examples = ["abc", "abcd", "short", "ninechars", "tenletters", "a".repeat(30), "constructor"];
  for (const id of examples) {
    const order = body(id, { listAmount: customIds.getPrice(id), discount: null, requestedId: ` ${id.toUpperCase()} ` });
    const result = await request("fulfill", order);
    const referenceHash = crypto.createHash("sha256").update(`stealthx-custom-id-ref:v1:${id}`).digest("hex").slice(0, 24);
    assert.match(result.payload.licenseRef, /^cid_[a-f0-9]{24}$/);
    assert.strictEqual(result.payload.licenseRef, `cid_${referenceHash}`, "reference uses the normalized reserved ID");
    assertUtcTimestamp(result.payload.fulfilledAt);
    assert.deepStrictEqual(result.payload, {
      ok: true, fulfilled: true, productId: order.productId,
      licenseRef: orders()[order.externalOrderId].licenseRef, fulfilledAt: orders()[order.externalOrderId].fulfilledAt,
    });
    assert.strictEqual(order.amount, customIds.getPrice(id));
    assert.strictEqual(customIds.isAvailable(id).available, false);
    assert.strictEqual(customIds.resolve(id), null, "reservation grants no device routing or paid app tier");
    assert.strictEqual(orders()[order.externalOrderId].status, "FULFILLED");
    assert.strictEqual(orders()[order.externalOrderId].paidAmount, order.amount);
    assert.strictEqual(orders()[order.externalOrderId].couponId, null);
    const duplicate = await request("fulfill", { ...order, requestedId: ` ${id.toUpperCase()} `, paymentEventId: "evt_retry" });
    assert.strictEqual(duplicate.payload.duplicate, true);
    assert.strictEqual(duplicate.payload.licenseRef, result.payload.licenseRef);
    assert.strictEqual(duplicate.payload.fulfilledAt, result.payload.fulfilledAt);
    assert.strictEqual((await request("fulfill", { ...order, requestedId: id.replace(/^./, "z") })).statusCode, 409);
    assert.strictEqual((await request("fulfill", { ...order, paymentReference: "pi_drift" })).statusCode, 409);
    assert.strictEqual((await request("fulfill", body(id))).statusCode, 409, "another order cannot take the ID");
    setup(); // Keep each scenario below the unchanged shared request budget.
  }

  // Pre-receipt Custom-ID rows can recover their reference from a signed retry.
  const legacyReceipt = body("legacy-receipt");
  const originalReceipt = await request("fulfill", legacyReceipt);
  const legacyOrders = orders();
  delete legacyOrders[legacyReceipt.externalOrderId].licenseRef;
  delete legacyOrders[legacyReceipt.externalOrderId].fulfilledAt;
  legacyOrders[legacyReceipt.externalOrderId].createdAt = "2024-01-02T03:04:05.000Z";
  fs.writeFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, JSON.stringify(legacyOrders));
  const backfilledReceipt = await request("fulfill", { ...legacyReceipt, requestedId: " LEGACY-RECEIPT " });
  assert.strictEqual(backfilledReceipt.payload.duplicate, true);
  assert.strictEqual(backfilledReceipt.payload.licenseRef, originalReceipt.payload.licenseRef);
  assert.strictEqual(backfilledReceipt.payload.fulfilledAt, "2024-01-02T03:04:05.000Z");
  reload();
  assert.deepStrictEqual((await request("fulfill", legacyReceipt)).payload, backfilledReceipt.payload);

  assert.deepStrictEqual(activationCodes, []);
  assert.strictEqual(soldCodes.load().length, 0, "no application activation code is minted");
  const stored = fs.readFileSync(process.env.IDS_FILE, "utf8") + fs.readFileSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE, "utf8");
  assert.ok(!stored.includes("tenletters") && !stored.includes("buyer@example.invalid"));
  assert.ok(Object.keys(read(process.env.IDS_FILE)).every(key => /^[a-f0-9]{64}$/.test(key)));

  // Pre-existing ownership and an in-flight legacy activation must win.
  const ids = read(process.env.IDS_FILE);
  const ownedKey = crypto.createHmac("sha256", pepper).update("owned-example").digest("hex");
  ids[ownedKey] = { deviceId: "test-device", stripeSessionId: "cs_legacy" };
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify(ids));
  fs.writeFileSync(process.env.PENDING_FILE, JSON.stringify({ test_token: {
    customId: "pending-example", createdAt: Date.now(), stripeSessionId: "cs_legacy_pending",
  } }));
  reload();
  assert.strictEqual((await request("fulfill", body("owned-example"))).statusCode, 409);
  assert.strictEqual((await request("fulfill", body("pending-example"))).statusCode, 409);
  assert.strictEqual(customIds.resolve("owned-example"), "test-device");

  // Recreate the state left by a legacy activation whose failed save was swallowed:
  // the paid token is consumed and ownership exists only in the live registry.
  const legacyId = "live-legacy-owner";
  const legacyKey = crypto.createHmac("sha256", pepper).update(legacyId).digest("hex");
  fs.writeFileSync(process.env.PENDING_FILE, JSON.stringify({ legacy_token: {
    customId: legacyId, createdAt: Date.now(), stripeSessionId: "cs_legacy_live",
    paidAt: new Date().toISOString(), passwordHash: "test-hash", passwordSalt: "test-salt",
  } }));
  reload();
  let legacyActivate;
  customIds.setupRoutes({ get() {}, post(route, ...handlers) {
    if (route === "/custom-id/activate-token") legacyActivate = handlers.at(-1);
  } });
  const legacyResponse = { payload: null, json(value) { this.payload = value; return this; } };
  legacyActivate({ body: { id: legacyId, deviceId: "device-legacy-live", token: "legacy_token" } }, legacyResponse);
  assert.strictEqual(legacyResponse.payload.success, true);
  const beforeLegacySave = read(process.env.IDS_FILE);
  delete beforeLegacySave[legacyKey];
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify(beforeLegacySave));
  assert.deepStrictEqual(read(process.env.PENDING_FILE), {});
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");
  const contested = body(legacyId);
  assert.strictEqual((await request("fulfill", contested)).statusCode, 409,
    "live-only ownership must prevent a second sale after a legacy persistence failure");
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live", "rejected sale preserves routing");
  assert.strictEqual(orders()[contested.externalOrderId]?.status, "PENDING_EMAIL");

  // A persisted same-order reservation cannot bypass conflicting live ownership.
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify({ ...beforeLegacySave, [legacyKey]: {
    source: "vlabs", stripeSessionId: contested.externalOrderId, deviceId: null,
  } }));
  assert.strictEqual((await request("fulfill", contested)).statusCode, 409);
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify(beforeLegacySave));

  // An unrelated reservation and its idempotent retry must also preserve live routing.
  const unrelated = body("unrelated-reservation");
  assert.strictEqual((await request("fulfill", unrelated)).statusCode, 200);
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");
  assert.strictEqual(read(process.env.IDS_FILE)[legacyKey].deviceId, "device-legacy-live");
  assert.strictEqual((await request("fulfill", unrelated)).payload.duplicate, true);
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");

  // Reservation retries and release must not refresh away other live-only owners.
  const withoutLiveOwner = read(process.env.IDS_FILE);
  delete withoutLiveOwner[legacyKey];
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify(withoutLiveOwner));
  customIds.reserveForVlabs(unrelated.requestedId, unrelated.externalOrderId);
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");
  assert.strictEqual((await request("revoke", revokeBody(unrelated))).payload.revoked, true);
  assert.strictEqual(customIds.resolve(legacyId), "device-legacy-live");
  assert.strictEqual(read(process.env.IDS_FILE)[legacyKey].deviceId, "device-legacy-live");

  // Ownership added on disk since startup must also win without reloading the module.
  const diskId = "disk-only-owner";
  const diskKey = crypto.createHmac("sha256", pepper).update(diskId).digest("hex");
  fs.writeFileSync(process.env.IDS_FILE, JSON.stringify({ ...read(process.env.IDS_FILE), [diskKey]: {
    deviceId: "device-disk-only", stripeSessionId: "cs_legacy_disk",
  } }));
  assert.strictEqual((await request("fulfill", body(diskId))).statusCode, 409);

  const race = await Promise.all([
    request("fulfill", body("race-example")), request("fulfill", body("race-example")),
  ]);
  assert.deepStrictEqual(race.map(result => result.statusCode).sort(), [200, 409]);

  // Both accepted reversal reasons work with the sales flag off, including payment-only lookup.
  for (const reason of ["stripe_full_refund", "stripe_dispute"]) {
    const order = body(reason === "stripe_dispute" ? "dispute-example" : "refund-example");
    const fulfilled = await request("fulfill", order);
    assert.strictEqual(fulfilled.statusCode, 200);
    delete process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED;
    const adjustment = revokeBody(order, { reason, externalOrderId: undefined });
    const revoked = await request("revoke", adjustment);
    assert.strictEqual(revoked.payload.revoked, true);
    assertUtcTimestamp(revoked.payload.revokedAt);
    assert.strictEqual(revoked.payload.licenseRef, fulfilled.payload.licenseRef);
    assert.strictEqual(orders()[order.externalOrderId].revokedAt, revoked.payload.revokedAt);
    reload();
    const duplicate = await request("revoke", adjustment);
    assert.strictEqual(duplicate.payload.duplicate, true);
    assert.strictEqual(duplicate.payload.revokedAt, revoked.payload.revokedAt);
    assert.strictEqual(duplicate.payload.licenseRef, fulfilled.payload.licenseRef);
    assert.strictEqual(customIds.isAvailable(order.requestedId).available, true);
    process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED = "true";
    assert.strictEqual((await request("fulfill", order)).statusCode, 409);
    const replacement = body(order.requestedId);
    assert.strictEqual((await request("fulfill", replacement)).statusCode, 200);
    await request("revoke", adjustment);
    assert.strictEqual(customIds.isAvailable(order.requestedId).available, false, "old refund cannot release a new owner");
  }

  for (const paymentOnly of [false, true]) {
    const order = body(paymentOnly ? "early-payment" : "early-session");
    const adjustment = revokeBody(order, paymentOnly ? { externalOrderId: undefined } : {});
    const revoked = await request("revoke", adjustment);
    assert.strictEqual(revoked.payload.tombstoned, true);
    assertUtcTimestamp(revoked.payload.revokedAt);
    assert.strictEqual((await request("fulfill", order)).statusCode, 409);
    const duplicate = await request("revoke", adjustment);
    assert.strictEqual(duplicate.payload.duplicate, true);
    assert.strictEqual(duplicate.payload.revokedAt, revoked.payload.revokedAt);
    assert.strictEqual(customIds.isAvailable(order.requestedId).available, true);
  }
  assert.strictEqual((await request("revoke", revokeBody(body(), { reason: "partial_refund" }))).statusCode, 400);

  // Recover a durable intent if reservation persistence fails.
  const failedReservation = body("retry-reservation");
  failRename(process.env.IDS_FILE);
  assert.strictEqual((await request("fulfill", failedReservation)).statusCode, 503);
  fs.renameSync = originalRename;
  assert.strictEqual(orders()[failedReservation.externalOrderId].status, "PENDING_EMAIL");
  assert.strictEqual(customIds.isAvailable(failedReservation.requestedId).available, true);
  reload();
  assert.strictEqual((await request("fulfill", failedReservation)).statusCode, 200);

  // Crash after reservation, before final order write: retry reuses its ownership.
  const failedFinalization = body("retry-finalization");
  failRename(process.env.VLABS_FULFILLMENT_ORDERS_FILE, 2);
  assert.strictEqual((await request("fulfill", failedFinalization)).statusCode, 503);
  fs.renameSync = originalRename;
  assert.strictEqual(customIds.isAvailable(failedFinalization.requestedId).available, false);
  assert.strictEqual(orders()[failedFinalization.externalOrderId].status, "PENDING_EMAIL");
  reload();
  assert.strictEqual((await request("fulfill", failedFinalization)).statusCode, 200);

  // A failed release stays retryable even though the reversal is already recorded.
  const failedRelease = body("retry-revocation");
  await request("fulfill", failedRelease);
  failRename(process.env.IDS_FILE);
  const adjustment = revokeBody(failedRelease);
  assert.strictEqual((await request("revoke", adjustment)).statusCode, 503);
  const firstReleaseTime = read(process.env.SOLD_CODES_FILE).reversals
    .find(entry => entry.stripeSessionId === failedRelease.externalOrderId).reversedAt;
  fs.renameSync = originalRename;
  assert.strictEqual(orders()[failedRelease.externalOrderId].status, "FULFILLED");
  assert.strictEqual((await request("fulfill", failedRelease)).statusCode, 409, "reversal blocks duplicate fulfillment too");
  reload();
  await new Promise(resolve => setTimeout(resolve, 10));
  const retriedRelease = await request("revoke", adjustment);
  assert.strictEqual(retriedRelease.payload.revoked, true);
  assert.strictEqual(retriedRelease.payload.revokedAt, firstReleaseTime);
  assert.strictEqual(customIds.isAvailable(failedRelease.requestedId).available, true);

  // A failed terminal write must not let a retry remove a replacement's reservation.
  const failedTerminal = body("retry-terminal");
  await request("fulfill", failedTerminal);
  failRename(process.env.VLABS_FULFILLMENT_ORDERS_FILE);
  assert.strictEqual((await request("revoke", revokeBody(failedTerminal))).statusCode, 503);
  const firstTerminalTime = read(process.env.SOLD_CODES_FILE).reversals
    .find(entry => entry.stripeSessionId === failedTerminal.externalOrderId).reversedAt;
  fs.renameSync = originalRename;
  assert.strictEqual((await request("fulfill", body(failedTerminal.requestedId))).statusCode, 200);
  const retriedTerminal = await request("revoke", revokeBody(failedTerminal));
  assert.strictEqual(retriedTerminal.statusCode, 200);
  assert.strictEqual(retriedTerminal.payload.revokedAt, firstTerminalTime);
  assert.strictEqual(customIds.isAvailable(failedTerminal.requestedId).available, false);

  // Failure before durable intent must not leave an orphaned reservation.
  const failedIntent = body("retry-intent");
  failRename(process.env.VLABS_FULFILLMENT_ORDERS_FILE);
  assert.strictEqual((await request("fulfill", failedIntent)).statusCode, 503);
  fs.renameSync = originalRename;
  assert.strictEqual(customIds.isAvailable(failedIntent.requestedId).available, true);
  assert.strictEqual(orders()[failedIntent.externalOrderId], undefined);
  assert.strictEqual((await request("fulfill", failedIntent)).statusCode, 200);

  // Corruption and a contested orders lock fail closed.
  const savedIds = fs.readFileSync(process.env.IDS_FILE, "utf8");
  fs.writeFileSync(process.env.IDS_FILE, "{");
  assert.strictEqual((await request("fulfill", body("corrupt-store"))).statusCode, 503);
  fs.writeFileSync(process.env.IDS_FILE, savedIds);
  const savedPending = fs.readFileSync(process.env.PENDING_FILE, "utf8");
  fs.writeFileSync(process.env.PENDING_FILE, "[]");
  assert.strictEqual((await request("fulfill", body("corrupt-pending"))).statusCode, 503);
  fs.writeFileSync(process.env.PENDING_FILE, savedPending);
  fs.writeFileSync(`${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`, "999999:foreign\n");
  assert.strictEqual((await request("fulfill", body("locked-store"))).statusCode, 503);
  fs.unlinkSync(`${process.env.VLABS_FULFILLMENT_ORDERS_FILE}.lock`);
  assert.strictEqual(customIds.resolve("owned-example"), "test-device", "legacy ownership is preserved");

  // The same TEST signing boundary applies to Custom-ID reservations and release.
  setup();
  const testSecret = "custom-id-test-fulfillment-secret-over-32-characters";
  process.env.VLABS_FULFILLMENT_TEST_SECRET = testSecret;
  const testOrder = body("test-mode-reservation", { externalOrderId: "cs_test_custom_mode", livemode: false });
  assert.strictEqual((await request("fulfill", testOrder)).statusCode, 403);
  assert.strictEqual((await request("fulfill", testOrder, { secret: testSecret })).payload.fulfilled, true);
  assert.strictEqual(orders()[testOrder.externalOrderId].mode, "test");
  assert.strictEqual(orders()[testOrder.externalOrderId].paymentReference, `test:${testOrder.paymentReference}`);
  assert.strictEqual(orders()[testOrder.externalOrderId].paymentEventId, `test:${testOrder.paymentEventId}`);
  assert.strictEqual(customIds.isAvailable(testOrder.requestedId).available, false);
  const modeSnapshot = () => [process.env.VLABS_FULFILLMENT_ORDERS_FILE, process.env.SOLD_CODES_FILE, process.env.IDS_FILE]
    .map(file => fs.existsSync(file) ? fs.readFileSync(file, "utf8") : null);
  const beforeMissingSession = modeSnapshot();
  const missingSession = await request("revoke", revokeBody(testOrder, { externalOrderId: undefined }), { secret: testSecret });
  assert.strictEqual(missingSession.statusCode, 403);
  assert.deepStrictEqual(missingSession.payload, { ok: false, error: "Mode mismatch" });
  assert.deepStrictEqual(modeSnapshot(), beforeMissingSession, "rejected TEST revoke has no persistence effects");
  const testRevoke = revokeBody(testOrder, { livemode: false });
  assert.strictEqual((await request("revoke", testRevoke)).statusCode, 403);
  assert.strictEqual((await request("revoke", testRevoke, { secret: testSecret })).payload.revoked, true);
  assert.strictEqual(orders()[testOrder.externalOrderId].mode, "test");
  assert.strictEqual(orders()[testOrder.externalOrderId].adjustmentEventId, `test:${testRevoke.adjustmentEventId}`);
  assert.strictEqual(customIds.isAvailable(testOrder.requestedId).available, true);

  // F2: a TEST Custom ID cannot claim an orphaned LIVE sale's payment binding.
  // Recreate the sold entry left when the LIVE order-file write failed.
  process.env.VLABS_CUSTOM_ID_FULFILLMENT_ENABLED = "true";
  const product = fulfillment.PRODUCTS["stealthx-securecall-pro-lifetime"];
  const orphanSale = {
    code: "PRO-ORPH-AN00-0001", tier: product.tier,
    stripeSessionId: "cs_live_orphan_custom_test", paymentReference: "pi_live_orphan_custom_test",
    productKey: product.productKey, catalogVersion: product.catalogVersion,
    offerVersion: product.offerVersion, releaseId: product.releaseId,
    externalProductId: "stealthx-securecall-pro-lifetime", mode: "live",
  };
  const liveSaleBefore = soldCodes.recordSale(orphanSale);
  const orphanTest = body("orphan-test-reservation", {
    externalOrderId: "cs_test_orphan_custom", paymentReference: orphanSale.paymentReference,
  });
  assert.strictEqual(orders()[orphanSale.stripeSessionId], undefined);
  assert.strictEqual((await request("fulfill", orphanTest, { secret: testSecret })).payload.fulfilled, true);
  assert.strictEqual(orders()[orphanTest.externalOrderId].paymentReference, `test:${orphanSale.paymentReference}`);
  assert.strictEqual(read(process.env.IDS_FILE)[orders()[orphanTest.externalOrderId].requestedIdKey].stripeSessionId,
    orphanTest.externalOrderId, "TEST reservation belongs to its TEST session");
  assert.deepStrictEqual(soldCodes.findByStripeSession(orphanSale.stripeSessionId), liveSaleBefore);
  assert.deepStrictEqual(soldCodes.recordSale({ ...orphanSale, code: "PRO-UNUS-ED00-0001" }), liveSaleBefore,
    "LIVE sale recovery still reuses the original code");
  assert.deepStrictEqual(soldCodes.revokeByStripeSession(orphanSale.stripeSessionId, undefined, {
    paymentIntent: orphanSale.paymentReference, eventId: "evt_live_orphan_custom_refund",
    reason: "stripe_full_refund", mode: "live",
  }), { found: true, duplicate: false });
  assert.strictEqual(soldCodes.isReversed(null, orphanSale.paymentReference), true);
  assert.strictEqual(soldCodes.isReversed(null, orphanSale.paymentReference, "test"), false);
  assert.strictEqual((await request("fulfill", orphanTest, { secret: testSecret })).payload.duplicate, true,
    "a LIVE reversal cannot block a TEST Custom-ID retry");
  const revokedLiveSale = soldCodes.findByStripeSession(orphanSale.stripeSessionId);
  assert.strictEqual((await request("revoke", revokeBody(orphanTest), { secret: testSecret })).payload.revoked, true);
  assert.deepStrictEqual(soldCodes.findByStripeSession(orphanSale.stripeSessionId), revokedLiveSale);
  assert.strictEqual(customIds.isAvailable(orphanTest.requestedId).available, true);

  // A TEST reversal must also leave a later LIVE reservation/reversal usable.
  const laterLive = body("later-live-reservation");
  const earlierTest = body("earlier-test-reservation", {
    externalOrderId: "cs_test_custom_preempt", paymentReference: laterLive.paymentReference,
  });
  assert.strictEqual((await request("revoke", revokeBody(earlierTest), { secret: testSecret })).payload.tombstoned, true);
  assert.strictEqual((await request("fulfill", laterLive)).payload.fulfilled, true);
  assert.strictEqual((await request("revoke", revokeBody(laterLive, { externalOrderId: undefined }))).payload.revoked, true);
  assert.strictEqual(customIds.isAvailable(laterLive.requestedId).available, true);
  assert.strictEqual((await request("fulfill", earlierTest, { secret: testSecret })).statusCode, 409);
  const sameTestPayment = body("blocked-test-reservation", {
    externalOrderId: "cs_test_custom_same_payment", paymentReference: laterLive.paymentReference,
  });
  assert.strictEqual((await request("fulfill", sameTestPayment, { secret: testSecret })).statusCode, 409,
    "TEST reversal still blocks another TEST session using the same payment");
  assert.strictEqual(customIds.isAvailable(sameTestPayment.requestedId).available, true);

  // Equal references cannot release the other mode's existing reservation.
  const sharedLive = body("shared-live-reservation");
  const sharedTest = body("shared-test-reservation", {
    externalOrderId: "cs_test_custom_shared", paymentReference: sharedLive.paymentReference,
  });
  assert.strictEqual((await request("fulfill", sharedLive)).payload.fulfilled, true);
  assert.strictEqual((await request("fulfill", sharedTest, { secret: testSecret })).payload.fulfilled, true);
  assert.strictEqual((await request("revoke", revokeBody(sharedTest), { secret: testSecret })).payload.revoked, true);
  assert.strictEqual(customIds.isAvailable(sharedLive.requestedId).available, false);
  assert.strictEqual(orders()[sharedLive.externalOrderId].status, "FULFILLED");
  assert.strictEqual((await request("revoke", revokeBody(sharedLive, { externalOrderId: undefined }))).payload.revoked, true);
  assert.strictEqual(customIds.isAvailable(sharedLive.requestedId).available, true);

  // Explicit sold-store mode checks also protect the custom branch if a stored
  // session binding is inconsistent, before any order write or reservation.
  const conflicting = body("conflicting-mode-reservation", { externalOrderId: "cs_test_custom_conflict" });
  soldCodes.recordSale({
    ...orphanSale, code: "PRO-MODE-CONF-LICT",
    stripeSessionId: conflicting.externalOrderId, paymentReference: "pi_unrelated_conflict",
  });
  const beforeConflict = modeSnapshot();
  const conflict = await request("fulfill", conflicting, { secret: testSecret });
  assert.strictEqual(conflict.statusCode, 403);
  assert.deepStrictEqual(conflict.payload, { ok: false, error: "Mode mismatch" });
  assert.deepStrictEqual(modeSnapshot(), beforeConflict);
  assert.strictEqual(customIds.isAvailable(conflicting.requestedId).available, true);
  assert.deepStrictEqual(activationCodes, []);
  delete process.env.VLABS_FULFILLMENT_TEST_SECRET;
  console.log("vlabs_custom_id_fulfillment.test.js ok");
})().catch(error => {
  console.error(error);
  process.exitCode = 1;
}).finally(() => {
  fs.renameSync = originalRename;
  fs.rmSync(directory, { recursive: true, force: true });
});
