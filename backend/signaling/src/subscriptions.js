/**
 * Subscription Management Module
 *
 * Map-based in-memory subscription store for SecureCall.
 * Tracks client subscription tiers, purchase tokens, and expiry.
 *
 * Data structure:
 *   Map<clientId, { clientId, tier, purchaseToken, productId, expiresAt, verifiedAt }>
 *
 * Usage:
 *   const subscriptions = require("./subscriptions");
 *   const result = subscriptions.verifySubscription(clientId, purchaseToken, productId);
 *   const sub = subscriptions.getSubscription(clientId);
 */

// In-Memory Store: clientId -> { clientId, tier, purchaseToken, productId, expiresAt, verifiedAt }
const subscriptions = new Map();

/**
 * Derives the subscription tier from a productId string.
 * - Contains 'premium' -> 'PREMIUM'
 * - Contains 'pro'     -> 'PRO'
 * - Otherwise          -> 'FREE'
 */
function deriveTier(productId) {
  const lower = productId.toLowerCase();
  if (lower.includes("premium")) return "PREMIUM";
  if (lower.includes("pro")) return "PRO";
  return "FREE";
}

/**
 * Calculates expiry date based on productId billing period.
 * - Contains 'yearly'  -> 365 days from now
 * - Contains 'monthly' -> 30 days from now (default)
 */
function calculateExpiry(productId) {
  const lower = productId.toLowerCase();
  const now = Date.now();
  if (lower.includes("yearly")) {
    return now + 365 * 24 * 60 * 60 * 1000;
  }
  // Default to monthly (30 days)
  return now + 30 * 24 * 60 * 60 * 1000;
}

/**
 * Verifies (stores/updates) a subscription for the given clientId.
 * Returns { tier, expiresAt }.
 */
function verifySubscription(clientId, purchaseToken, productId) {
  const tier = deriveTier(productId);
  const expiresAt = calculateExpiry(productId);
  const now = Date.now();

  const entry = {
    clientId,
    tier,
    purchaseToken,
    productId,
    expiresAt,
    verifiedAt: now
  };

  subscriptions.set(clientId, entry);
  return { tier, expiresAt };
}

/**
 * Returns the subscription entry for a clientId, or null if not found.
 */
function getSubscription(clientId) {
  return subscriptions.get(clientId) || null;
}

/**
 * Removes the subscription for a clientId.
 * Returns true if found and deleted, false otherwise.
 */
function expireSubscription(clientId) {
  return subscriptions.delete(clientId);
}

/**
 * Returns the tier string for a clientId, or 'FREE' if no subscription exists.
 */
function getTier(clientId) {
  const entry = subscriptions.get(clientId);
  return entry ? entry.tier : "FREE";
}

module.exports = {
  verifySubscription,
  getSubscription,
  expireSubscription,
  getTier
};
