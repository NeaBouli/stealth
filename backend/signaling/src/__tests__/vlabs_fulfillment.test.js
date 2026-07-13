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
    const request = signedRequest({ externalOrderId, productId, customerEmail: "transient@example.invalid" });
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

  const storedCodes = JSON.parse(fs.readFileSync(process.env.SOLD_CODES_FILE, "utf8")).codes;
  assert.strictEqual(storedCodes.some(entry => Object.prototype.hasOwnProperty.call(entry, "email")), false);
  assert.strictEqual(fs.statSync(process.env.SOLD_CODES_FILE).mode & 0o777, 0o600);
  assert.strictEqual(fs.statSync(process.env.VLABS_FULFILLMENT_ORDERS_FILE).mode & 0o777, 0o600);

  const revokeBody = {
    externalOrderId: "cs_test_securechat_pro_lifetime",
    productId: "stealthx-securechat-pro-lifetime",
    reason: "stripe_full_refund"
  };
  const revoked = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest(revokeBody), revoked);
  assert.strictEqual(revoked.body.revoked, true);
  assert.strictEqual(activationCodes.some(entry => entry.stripeSessionId === revokeBody.externalOrderId), false);
  const duplicateRevoke = response();
  await routes.get("/internal/vlabs/revoke")(signedRequest(revokeBody), duplicateRevoke);
  assert.strictEqual(duplicateRevoke.body.duplicate, true);

  fs.writeFileSync(process.env.SOLD_CODES_FILE, JSON.stringify({
    codes: [{ code: "PRO-OLD-TEST-TEST", tier: "pro", email: "legacy@example.invalid" }]
  }), { mode: 0o644 });
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
