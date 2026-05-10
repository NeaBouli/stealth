/**
 * Subscription Management Module
 *
 * Persistent subscription store for SecureCall.
 * Tracks client subscription tiers, purchase tokens, and expiry.
 * Persisted to data/subscriptions.json (survives server restarts).
 *
 * Data structure:
 *   Map<clientId, { clientId, tier, purchaseToken, productId, expiresAt, verifiedAt }>
 *
 * Usage:
 *   const subscriptions = require("./subscriptions");
 *   const result = subscriptions.verifySubscription(clientId, purchaseToken, productId);
 *   const sub = subscriptions.getSubscription(clientId);
 */

const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("./utils/json_store");

const SUBS_FILE = path.join(__dirname, "..", "data", "subscriptions.json");

// Persistent store: clientId -> subscription entry
const subscriptions = new Map();

// ─── Persistence ────────────────────────────────────────────

function loadSubscriptions() {
  try {
    if (fs.existsSync(SUBS_FILE)) {
      const data = JSON.parse(fs.readFileSync(SUBS_FILE, "utf8"));
      for (const [k, v] of Object.entries(data)) {
        subscriptions.set(k, v);
      }
      console.log(`[SUBSCRIPTION] Loaded ${subscriptions.size} persisted subscriptions`);
    }
  } catch (e) {
    console.error("[SUBSCRIPTION] Load failed:", e.message);
  }
}

function saveSubscriptions() {
  try {
    const obj = {};
    for (const [k, v] of subscriptions) obj[k] = v;
    writeJsonAtomic(SUBS_FILE, obj);
  } catch (e) {
    console.error("[SUBSCRIPTION] Save failed:", e.message);
  }
}

// ─── Tier + Expiry Logic ────────────────────────────────────

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
 * - Contains 'lifetime' -> 100 years (effectively never)
 * - Contains 'yearly'   -> 365 days from now
 * - Contains 'monthly'  -> 30 days from now (default)
 */
function calculateExpiry(productId) {
  const lower = productId.toLowerCase();
  const now = Date.now();
  const DAY = 24 * 60 * 60 * 1000;
  if (lower.includes("lifetime") || lower.includes("activation_code")) {
    return now + 100 * 365 * DAY; // ~100 years
  }
  if (lower.includes("yearly")) {
    return now + 365 * DAY;
  }
  // Default to monthly (30 days)
  return now + 30 * DAY;
}

// ─── Core Functions ─────────────────────────────────────────

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
  saveSubscriptions();
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
  const deleted = subscriptions.delete(clientId);
  if (deleted) saveSubscriptions();
  return deleted;
}

/**
 * Returns the tier string for a clientId, or 'FREE' if no subscription
 * exists or if the subscription has expired.
 */
function getTier(clientId) {
  const entry = subscriptions.get(clientId);
  if (!entry) return "FREE";
  if (Date.now() > entry.expiresAt) return "FREE";
  return entry.tier;
}

// ─── Init ───────────────────────────────────────────────────

loadSubscriptions();

module.exports = {
  verifySubscription,
  getSubscription,
  expireSubscription,
  getTier
};
