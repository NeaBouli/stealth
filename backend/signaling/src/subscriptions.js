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

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("./utils/json_store");

const SUBS_FILE = process.env.SUBS_FILE || path.join(__dirname, "..", "data", "subscriptions.json");

// Persistent store: clientId -> subscription entry, plus same-package Play lifetime
// records under "inapp:<sha256(purchaseToken)>" (":" never occurs in a client ID).
const subscriptions = new Map();
const LIFETIME_KIND = "play_lifetime";

function lifetimeKey(purchaseToken) {
  return "inapp:" + crypto.createHash("sha256").update(String(purchaseToken)).digest("hex");
}

function isLifetime(entry) {
  return entry?.kind === LIFETIME_KIND;
}

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
  const entry = subscriptions.get(clientId);
  return entry && !isLifetime(entry) ? entry : null;
}

/**
 * Removes the subscription for a clientId.
 * Returns true if found and deleted, false otherwise.
 */
function expireSubscription(clientId) {
  const previous = subscriptions.get(clientId);
  if (!previous || isLifetime(previous)) return false;
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
    if (isLifetime(entry) || entry.purchaseToken !== purchaseToken) continue;
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
    if (isLifetime(entry) || entry.purchaseToken !== purchaseToken) continue;
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
  if (!entry || isLifetime(entry)) return "FREE";
  if (!entry.packageName || !entry.catalogVersion) return "FREE";
  if (Date.now() > entry.expiresAt) return "FREE";
  return entry.tier;
}

// ─── Same-package Play lifetime entitlements ───────────────

function persistLifetime(key, next, previous) {
  subscriptions.set(key, next);
  if (!saveSubscriptions()) {
    if (previous) subscriptions.set(key, previous);
    else subscriptions.delete(key);
    throw new Error("subscription_persistence_failed");
  }
}

/**
 * Durably binds a Play-verified one-time lifetime purchase token to one identity.
 * The first verified claim owns the token. The same identity restores idempotently;
 * another identity is accepted only if it lists the owner in allowedSubjects (the
 * policy-approved rebind authority). Revoked tokens are never granted again.
 */
function recordVerifiedLifetime({
  clientId, purchaseToken, productId, tier, packageName, catalogVersion, offerVersion,
  acknowledged, allowedSubjects
}) {
  if (!clientId || typeof purchaseToken !== "string" || !purchaseToken || !productId
      || !packageName || !catalogVersion || typeof offerVersion !== "string" || !offerVersion
      || !["pro", "premium"].includes(tier) || !Array.isArray(allowedSubjects)
      || !allowedSubjects.includes(clientId)) {
    throw new Error("invalid_verified_lifetime");
  }
  const key = lifetimeKey(purchaseToken);
  const existing = subscriptions.get(key);
  if (existing) {
    if (existing.revoked === true) throw new Error("purchase_revoked");
    if (existing.purchaseToken !== purchaseToken || existing.productId !== productId
        || existing.packageName !== packageName || existing.catalogVersion !== catalogVersion
        || existing.tier !== tier || existing.offerVersion !== offerVersion) {
      throw new Error("purchase_binding_mismatch");
    }
    if (existing.clientId === clientId) return { ...existing, duplicate: true };
    if (!allowedSubjects.includes(existing.clientId)) throw new Error("purchase_binding_mismatch");
    const rebound = { ...existing, clientId, previousClientId: existing.clientId, reboundAt: Date.now() };
    persistLifetime(key, rebound, existing);
    return { ...rebound, duplicate: true };
  }
  const entry = {
    kind: LIFETIME_KIND,
    clientId,
    tier,
    purchaseToken,
    productId,
    packageName,
    catalogVersion,
    offerVersion,
    acknowledged: acknowledged === true,
    revoked: false,
    verifiedAt: Date.now()
  };
  persistLifetime(key, entry, null);
  return { ...entry, duplicate: false };
}

function markLifetimeAcknowledged(purchaseToken) {
  const key = lifetimeKey(purchaseToken);
  const existing = subscriptions.get(key);
  if (!isLifetime(existing) || existing.revoked === true) throw new Error("purchase_revoked");
  if (existing.acknowledged === true) return false;
  persistLifetime(key, { ...existing, acknowledged: true }, existing);
  return true;
}

/** Returns the lifetime record bound to this identity, token, package and catalog, or null. */
function getLifetimeEntitlement(clientId, purchaseToken, packageName, catalogVersion) {
  if (!clientId || typeof purchaseToken !== "string" || !purchaseToken) return null;
  const entry = subscriptions.get(lifetimeKey(purchaseToken));
  if (!isLifetime(entry) || entry.purchaseToken !== purchaseToken || entry.clientId !== clientId
      || entry.packageName !== packageName || entry.catalogVersion !== catalogVersion) {
    return null;
  }
  return { ...entry };
}

function hasLifetimePurchase(purchaseToken) {
  return isLifetime(subscriptions.get(lifetimeKey(purchaseToken)));
}

/**
 * Refund/revoke keeps a durable tombstone on the token's record so it can never be
 * (re-)granted. An unknown token gets an unbound tombstone (clientId null) before the
 * caller records success, so a revoke that arrives before the first verified record,
 * including one racing a stale provider answer, still blocks the later grant.
 * Throws subscription_persistence_failed when the tombstone is not durable (retryable).
 */
function revokeLifetimeByPurchaseToken(purchaseToken) {
  if (typeof purchaseToken !== "string" || !purchaseToken) throw new Error("invalid_purchase_token");
  const key = lifetimeKey(purchaseToken);
  const existing = subscriptions.get(key);
  if (existing !== undefined && !isLifetime(existing)) throw new Error("lifetime_key_conflict");
  if (existing?.revoked === true) return 0;
  const revokedAt = Date.now();
  const next = existing
    ? { ...existing, revoked: true, revokedAt }
    : { kind: LIFETIME_KIND, clientId: null, purchaseToken, revoked: true, revokedAt };
  persistLifetime(key, next, existing || null);
  return 1;
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
  getTier,
  recordVerifiedLifetime,
  markLifetimeAcknowledged,
  getLifetimeEntitlement,
  hasLifetimePurchase,
  revokeLifetimeByPurchaseToken
};
