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

const SUBS_FILE = process.env.SUBS_FILE || path.join(__dirname, "..", "data", "subscriptions.json");

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

// ─── Core Functions ─────────────────────────────────────────

/**
 * Verifies (stores/updates) a subscription for the given clientId.
 * Returns { tier, expiresAt }.
 */
function recordVerifiedSubscription(clientId, purchaseToken, productId, tier, expiresAt) {
  if (!clientId || !purchaseToken || !productId || !["pro", "premium"].includes(tier) || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    throw new Error("invalid_verified_subscription");
  }
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

function verifySubscription() {
  throw new Error("external_google_play_verification_required");
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

function expireByPurchaseToken(purchaseToken) {
  let expired = 0;
  for (const [clientId, entry] of subscriptions) {
    if (entry.purchaseToken !== purchaseToken) continue;
    subscriptions.delete(clientId);
    expired += 1;
  }
  if (expired > 0) saveSubscriptions();
  return expired;
}

function refreshByPurchaseToken(purchaseToken, productId, tier, expiresAt) {
  if (!purchaseToken || !productId || !["pro", "premium"].includes(tier) || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    throw new Error("invalid_verified_subscription");
  }
  let refreshed = 0;
  for (const [clientId, entry] of subscriptions) {
    if (entry.purchaseToken !== purchaseToken) continue;
    subscriptions.set(clientId, { ...entry, productId, tier, expiresAt, verifiedAt: Date.now() });
    refreshed += 1;
  }
  if (refreshed > 0) saveSubscriptions();
  return refreshed;
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
  recordVerifiedSubscription,
  getSubscription,
  expireSubscription,
  expireByPurchaseToken,
  refreshByPurchaseToken,
  getTier
};
