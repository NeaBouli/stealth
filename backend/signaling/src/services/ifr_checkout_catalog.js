"use strict";

/**
 * IFR Checkout Catalog — centralized allowlist of web checkout products
 * eligible for the IFR holder discount flow.
 *
 * Only currently modeled individual products are listed. The bundle
 * stealthx_suite_lifetime is intentionally excluded until its fulfillment
 * path is ready. This module contains no prices, keys, or secrets.
 *
 * requiredIfrTier is the minimum IFR hold tier needed for the 50% discount:
 *   "pro"     -> any verified IFR hold (pro or premium)
 *   "premium" -> premium IFR hold required
 */

const IFR_CHECKOUT_PRODUCTS = Object.freeze({
  pro_lifetime: Object.freeze({
    productFamily: "securecall",
    activationTier: "pro",
    requiredIfrTier: "pro",
    successUrl: "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://stealthx.tech/#pricing",
  }),
  premium_lifetime: Object.freeze({
    productFamily: "securecall",
    activationTier: "premium",
    requiredIfrTier: "premium",
    successUrl: "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://stealthx.tech/#pricing",
  }),
  securechat_pro_lifetime: Object.freeze({
    productFamily: "securechat",
    activationTier: "pro",
    requiredIfrTier: "pro",
    successUrl: "https://securechat.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://securechat.stealthx.tech/#pricing",
  }),
  securechat_elite_lifetime: Object.freeze({
    productFamily: "securechat",
    activationTier: "elite",
    requiredIfrTier: "premium",
    successUrl: "https://securechat.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://securechat.stealthx.tech/#pricing",
  }),
  chameleon_pro_lifetime: Object.freeze({
    productFamily: "chameleon",
    activationTier: "pro",
    requiredIfrTier: "pro",
    successUrl: "https://chameleon.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://chameleon.stealthx.tech/#pricing",
  }),
  chameleon_elite_lifetime: Object.freeze({
    productFamily: "chameleon",
    activationTier: "elite",
    requiredIfrTier: "premium",
    successUrl: "https://chameleon.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://chameleon.stealthx.tech/#pricing",
  }),
});

// Returns the catalog entry for a tier, or null when the tier is not
// allowlisted for web checkout (unknown tier, or a deliberately excluded
// product such as stealthx_suite_lifetime).
function getCheckoutProduct(tier) {
  if (typeof tier !== "string" || tier.length === 0) return null;
  return IFR_CHECKOUT_PRODUCTS[tier] || null;
}

module.exports = { IFR_CHECKOUT_PRODUCTS, getCheckoutProduct };
