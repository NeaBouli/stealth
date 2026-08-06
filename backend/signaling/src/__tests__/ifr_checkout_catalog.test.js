"use strict";

/**
 * ifr_checkout_catalog.test.js — deterministic assertions for the
 * centralized web checkout catalog allowlist.
 *
 * Run: node src/__tests__/ifr_checkout_catalog.test.js
 */

const assert = require("assert");
const { IFR_CHECKOUT_PRODUCTS, getCheckoutProduct } = require("../services/ifr_checkout_catalog");
const { LICENSES } = require("../licenses");

// ── Allowlist contents ──────────────────────────────────────────────────────

const expectedTiers = [
  "pro_lifetime",
  "premium_lifetime",
  "securechat_pro_lifetime",
  "securechat_elite_lifetime",
  "chameleon_pro_lifetime",
  "chameleon_elite_lifetime",
].sort();

assert.deepStrictEqual(
  Object.keys(IFR_CHECKOUT_PRODUCTS).sort(),
  expectedTiers,
  "catalog contains exactly the six allowlisted individual products"
);

// The suite bundle stays out of web checkout until fulfillment is ready,
// even though it still exists in the license pricing model.
assert.ok(LICENSES.stealthx_suite_lifetime, "stealthx_suite_lifetime is still modeled in licenses");
assert.strictEqual(
  getCheckoutProduct("stealthx_suite_lifetime"), null,
  "stealthx_suite_lifetime is excluded from web checkout"
);
assert.strictEqual(getCheckoutProduct("unknown_tier"), null, "unknown tier rejected");
assert.strictEqual(getCheckoutProduct(""), null, "empty tier rejected");
assert.strictEqual(getCheckoutProduct(undefined), null, "missing tier rejected");
assert.strictEqual(getCheckoutProduct(null), null, "null tier rejected");
assert.strictEqual(getCheckoutProduct(42), null, "non-string tier rejected");

// ── Per-product contract ────────────────────────────────────────────────────

const FAMILY_HOSTS = {
  securecall: "stealthx.tech",
  securechat: "securechat.stealthx.tech",
  chameleon: "chameleon.stealthx.tech",
};

for (const [tier, product] of Object.entries(IFR_CHECKOUT_PRODUCTS)) {
  assert.ok(
    Object.prototype.hasOwnProperty.call(FAMILY_HOSTS, product.productFamily),
    `${tier}: known product family`
  );
  assert.ok(
    ["pro", "premium", "elite"].includes(product.activationTier),
    `${tier}: activation tier is pro/premium/elite`
  );
  assert.strictEqual(
    product.activationTier, LICENSES[tier].activationTier,
    `${tier}: activation tier matches the license model`
  );
  assert.ok(
    ["pro", "premium"].includes(product.requiredIfrTier),
    `${tier}: required IFR tier is pro/premium`
  );
  // Premium or elite activation requires a premium IFR hold; pro activation
  // accepts any verified hold.
  const expectedIfrTier = product.activationTier === "pro" ? "pro" : "premium";
  assert.strictEqual(
    product.requiredIfrTier, expectedIfrTier,
    `${tier}: required IFR tier matches activation tier`
  );
  const host = FAMILY_HOSTS[product.productFamily];
  assert.ok(
    product.successUrl.startsWith(`https://${host}/`),
    `${tier}: success URL is a public https URL on the family host`
  );
  assert.ok(
    product.successUrl.includes("{CHECKOUT_SESSION_ID}"),
    `${tier}: success URL carries the Stripe session placeholder`
  );
  assert.ok(
    product.cancelUrl.startsWith(`https://${host}/`),
    `${tier}: cancel URL is a public https URL on the family host`
  );
  // No prices, keys, or secrets in the catalog.
  for (const key of Object.keys(product)) {
    assert.ok(
      !/price|secret|key|token|amount/i.test(key),
      `${tier}: catalog field '${key}' must not carry prices or secrets`
    );
  }
}

// Catalog entries are frozen against accidental runtime mutation.
assert.ok(Object.isFrozen(IFR_CHECKOUT_PRODUCTS), "catalog object is frozen");
assert.ok(Object.isFrozen(IFR_CHECKOUT_PRODUCTS.pro_lifetime), "catalog entries are frozen");

console.log("ifr_checkout_catalog.test PASSED - 6 allowlisted products, suite excluded, contract verified");
