"use strict";

/**
 * IFR Checkout Catalog — centralized allowlist of web checkout products
 * eligible for the IFR holder discount flow.
 *
 * Only currently modeled individual products are listed. The bundle
 * stealthx_suite_lifetime is intentionally excluded until its fulfillment
 * path is ready. This module contains no prices, keys, or secrets.
 *
 * Any wallet with a positive IFR balance is eligible for the seller-displayed
 * holder discount. Product access is determined by the purchased product and
 * its activation code, never by the token balance.
 */

const IFR_CHECKOUT_PRODUCTS = Object.freeze({
  pro_lifetime: Object.freeze({
    productFamily: "securecall",
    activationTier: "pro",
    successUrl: "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://stealthx.tech/#pricing",
  }),
  premium_lifetime: Object.freeze({
    productFamily: "securecall",
    activationTier: "premium",
    successUrl: "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://stealthx.tech/#pricing",
  }),
  securechat_pro_lifetime: Object.freeze({
    productFamily: "securechat",
    activationTier: "pro",
    successUrl: "https://securechat.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://securechat.stealthx.tech/#pricing",
  }),
  securechat_elite_lifetime: Object.freeze({
    productFamily: "securechat",
    activationTier: "elite",
    successUrl: "https://securechat.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://securechat.stealthx.tech/#pricing",
  }),
  chameleon_pro_lifetime: Object.freeze({
    productFamily: "chameleon",
    activationTier: "pro",
    successUrl: "https://chameleon.stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}",
    cancelUrl: "https://chameleon.stealthx.tech/#pricing",
  }),
  chameleon_elite_lifetime: Object.freeze({
    productFamily: "chameleon",
    activationTier: "elite",
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
