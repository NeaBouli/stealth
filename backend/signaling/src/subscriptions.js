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
    return true;
  } catch (e) {
    console.error("[SUBSCRIPTION] Save failed:", e.message);
    return false;
  }
}

// ─── Core Functions ─────────────────────────────────────────

/**
 * Verifies (stores/updates) a subscription for the given clientId.
 * Returns { tier, expiresAt }.
 */
function recordVerifiedSubscription(clientId, purchaseToken, productId, tier, expiresAt, packageName, catalogVersion) {
  if (!clientId || !purchaseToken || !productId || !packageName || !catalogVersion
      || !["pro", "premium"].includes(tier) || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    throw new Error("invalid_verified_subscription");
  }
  const now = Date.now();

  const entry = {
    clientId,
    tier,
    purchaseToken,
    productId,
    packageName,
    catalogVersion,
    expiresAt,
    verifiedAt: now
  };

  const previous = subscriptions.get(clientId);
  subscriptions.set(clientId, entry);
  if (!saveSubscriptions()) {
    if (previous) subscriptions.set(clientId, previous);
    else subscriptions.delete(clientId);
    throw new Error("subscription_persistence_failed");
  }
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
  const previous = subscriptions.get(clientId);
  if (!previous) return false;
  subscriptions.delete(clientId);
  if (!saveSubscriptions()) {
    subscriptions.set(clientId, previous);
    throw new Error("subscription_persistence_failed");
  }
  return true;
}

function expireByPurchaseToken(purchaseToken) {
  const removed = [];
  for (const [clientId, entry] of subscriptions) {
    if (entry.purchaseToken !== purchaseToken) continue;
    removed.push([clientId, entry]);
    subscriptions.delete(clientId);
  }
  if (removed.length > 0 && !saveSubscriptions()) {
    for (const [clientId, entry] of removed) subscriptions.set(clientId, entry);
    throw new Error("subscription_persistence_failed");
  }
  return removed.length;
}

function refreshByPurchaseToken(purchaseToken, productId, tier, expiresAt, packageName, catalogVersion) {
  if (!purchaseToken || !productId || !packageName || !catalogVersion
      || !["pro", "premium"].includes(tier) || !Number.isFinite(expiresAt) || expiresAt <= Date.now()) {
    throw new Error("invalid_verified_subscription");
  }
  const previousEntries = [];
  for (const [clientId, entry] of subscriptions) {
    if (entry.purchaseToken !== purchaseToken) continue;
    previousEntries.push([clientId, entry]);
    subscriptions.set(clientId, {
      ...entry,
      productId,
      tier,
      expiresAt,
      packageName,
      catalogVersion,
      verifiedAt: Date.now()
    });
  }
  if (previousEntries.length > 0 && !saveSubscriptions()) {
    for (const [clientId, entry] of previousEntries) subscriptions.set(clientId, entry);
    throw new Error("subscription_persistence_failed");
  }
  return previousEntries.length;
}

/**
 * Returns the tier string for a clientId, or 'FREE' if no subscription
 * exists or if the subscription has expired.
 */
function getTier(clientId) {
  const entry = subscriptions.get(clientId);
  if (!entry) return "FREE";
  if (!entry.packageName || !entry.catalogVersion) return "FREE";
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
