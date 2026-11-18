const crypto = require("crypto");
const fs = require("fs");

const PLAY_CATALOG_VERSION = "securecall-play-v1";

function isPlayBillingEnabled() {
  return String(process.env.PLAY_BILLING_ENABLED || "").trim().toLowerCase() === "true";
}

const ONE_TIME_PRODUCTS = Object.freeze({
  securecall_pro_lifetime: "pro",
  securecall_premium_lifetime: "premium",
  securecall_premium_activation_code: "premium"
});

const SUBSCRIPTION_PRODUCTS = Object.freeze({
  pro_monthly: "pro",
  premium_monthly: "premium",
  securecall_pro_monthly: "pro",
  securecall_pro_yearly: "pro",
  securecall_premium_monthly: "premium",
  securecall_premium_yearly: "premium"
});

// Same-package Pro/Premium lifetime products. The tier comes from this catalog only;
// the activation-code product keeps its own code path and is never a lifetime grant.
const LIFETIME_PRODUCTS = Object.freeze({
  securecall_pro_lifetime: "pro",
  securecall_premium_lifetime: "premium"
});

const LIFETIME_REBIND_AUTHORITIES = Object.freeze(["none", "identity_registry_alias"]);
const LIFETIME_POLICY_KEYS = Object.freeze(["catalogVersion", "offerVersion", "products", "proofTtlMs", "rebindAuthority"]);
const LIFETIME_POLICY_MAX_BYTES = 4096;
// Largest valid JavaScript Date; a proof expiry beyond it is an overflow, never "forever".
const MAX_PROOF_EXPIRY_MS = 8.64e15;

function isPlainObject(value) {
  return !!value && typeof value === "object" && !Array.isArray(value)
    && Object.getPrototypeOf(value) === Object.prototype;
}

function allowedPackages() {
  const configured = String(process.env.GOOGLE_PLAY_ALLOWED_PACKAGES || "com.securecall.app.free")
    .split(",")
    .map(value => value.trim())
    .filter(Boolean);
  return new Set(configured);
}

function resolveOneTimeProduct(packageName, productId) {
  if (!allowedPackages().has(packageName)) return null;
  const tier = ONE_TIME_PRODUCTS[productId];
  return tier ? { productId, tier, catalogVersion: PLAY_CATALOG_VERSION } : null;
}

/**
 * Validates the owner-approved same-package lifetime policy. Public source supplies
 * no policy and no defaults: an absent or incomplete policy means lifetime entitlement
 * is unavailable. Required inputs:
 * - catalogVersion: must equal PLAY_CATALOG_VERSION (version-bound catalog)
 * - offerVersion: approved offer version string
 * - products: { [productId]: { packageName } } approved package/product tuples
 * - proofTtlMs: positive finite offline proof/refresh bound; also bounds how long a
 *   client may keep access after server-side revocation
 * - rebindAuthority: "none" (same identity only) or "identity_registry_alias"
 */
function normalizeLifetimePolicy(policy, now = Date.now()) {
  if (!isPlainObject(policy)) return null;
  if (!Number.isSafeInteger(now) || now < 0 || now >= MAX_PROOF_EXPIRY_MS) return null;
  if (Object.keys(policy).some(key => !LIFETIME_POLICY_KEYS.includes(key))) return null;
  const { catalogVersion, offerVersion, products, proofTtlMs, rebindAuthority } = policy;
  if (catalogVersion !== PLAY_CATALOG_VERSION) return null;
  if (typeof offerVersion !== "string" || !/^[\x21-\x7e]{1,128}$/.test(offerVersion)) return null;
  if (!Number.isSafeInteger(proofTtlMs) || proofTtlMs <= 0 || proofTtlMs >= MAX_PROOF_EXPIRY_MS - now) return null;
  if (!LIFETIME_REBIND_AUTHORITIES.includes(rebindAuthority)) return null;
  if (!isPlainObject(products)) return null;
  const approved = {};
  for (const [productId, tuple] of Object.entries(products)) {
    if (!Object.prototype.hasOwnProperty.call(LIFETIME_PRODUCTS, productId) || !isPlainObject(tuple)
        || Object.keys(tuple).some(key => key !== "packageName")
        || tuple.packageName !== "com.securecall.app.free" || !allowedPackages().has(tuple.packageName)) {
      return null;
    }
    approved[productId] = Object.freeze({ packageName: tuple.packageName });
  }
  if (Object.keys(approved).length === 0) return null;
  return Object.freeze({
    catalogVersion, offerVersion, proofTtlMs, rebindAuthority, products: Object.freeze(approved)
  });
}

/**
 * Read-only operator input seam for the owner-approved lifetime policy, following the
 * existing *_FILE configuration convention: GOOGLE_PLAY_LIFETIME_POLICY_FILE names a
 * small JSON document with exactly the fields normalizeLifetimePolicy requires. The file
 * is read once, never written, and carries no defaults: absent, unreadable, oversized,
 * malformed or unsupported input returns null (lifetime entitlement unavailable).
 * It is not an enable flag; PLAY_BILLING_ENABLED/BILLING_ENABLED still gate commerce.
 */
function loadLifetimePolicy(env = process.env) {
  const file = String(env.GOOGLE_PLAY_LIFETIME_POLICY_FILE || "").trim();
  if (!file) return null;
  let fd;
  try {
    fd = fs.openSync(file, fs.constants.O_RDONLY | fs.constants.O_NONBLOCK);
    const stat = fs.fstatSync(fd);
    if (!stat.isFile() || stat.size <= 0 || stat.size > LIFETIME_POLICY_MAX_BYTES) throw new Error("size");
    const buffer = Buffer.alloc(stat.size);
    if (fs.readSync(fd, buffer, 0, stat.size, 0) !== stat.size) throw new Error("short_read");
    const normalized = normalizeLifetimePolicy(JSON.parse(buffer.toString("utf8")));
    if (!normalized) throw new Error("unsupported");
    return normalized;
  } catch {
    console.warn("[GOOGLE-PLAY] Lifetime policy input unavailable or unsupported; lifetime entitlement stays closed");
    return null;
  } finally {
    if (fd !== undefined) {
      try { fs.closeSync(fd); } catch { /* read-only descriptor */ }
    }
  }
}

function resolveLifetimeProduct(policy, packageName, productId, catalogVersion) {
  const normalized = normalizeLifetimePolicy(policy);
  if (!normalized || catalogVersion !== normalized.catalogVersion) return null;
  if (!Object.prototype.hasOwnProperty.call(normalized.products, productId)) return null;
  const tuple = normalized.products[productId];
  if (!tuple || tuple.packageName !== packageName || !allowedPackages().has(packageName)) return null;
  return {
    productId,
    tier: LIFETIME_PRODUCTS[productId],
    packageName,
    catalogVersion: normalized.catalogVersion,
    offerVersion: normalized.offerVersion,
    proofTtlMs: normalized.proofTtlMs,
    rebindAuthority: normalized.rebindAuthority,
  };
}

/**
 * Builds the /subscription/status answer for a stored lifetime record. Access is a
 * finite, policy-bound proof; revoked, unacknowledged or policy-less records are FREE.
 */
function lifetimeStatusResponse(record, policy, now = Date.now()) {
  if (!record) return null;
  if (record.revoked === true) return { valid: false, tier: "FREE", reason: "revoked" };
  if (record.acknowledged !== true) return { valid: false, tier: "FREE", reason: "pending_acknowledgement" };
  const product = resolveLifetimeProduct(policy, record.packageName, record.productId, record.catalogVersion);
  if (!product || product.tier !== record.tier || product.offerVersion !== record.offerVersion) {
    return { valid: false, tier: "FREE", reason: "lifetime_entitlement_unavailable" };
  }
  const expiresAt = now + product.proofTtlMs;
  if (!Number.isSafeInteger(expiresAt) || expiresAt > MAX_PROOF_EXPIRY_MS) {
    return { valid: false, tier: "FREE", reason: "lifetime_entitlement_unavailable" };
  }
  return {
    valid: true,
    tier: record.tier,
    productId: record.productId,
    packageName: record.packageName,
    catalogVersion: record.catalogVersion,
    expiresAt,
    verifiedAt: record.verifiedAt
  };
}

function parseSubscriptionPurchase(value, expectedProductId, now = Date.now()) {
  const allowedStates = new Set([
    "SUBSCRIPTION_STATE_ACTIVE",
    "SUBSCRIPTION_STATE_IN_GRACE_PERIOD",
    "SUBSCRIPTION_STATE_CANCELED"
  ]);
  if (!value || !allowedStates.has(value.subscriptionState) || !Array.isArray(value.lineItems)) return null;
  const line = value.lineItems.find(item => item?.productId === expectedProductId);
  const expiresAt = Date.parse(line?.expiryTime || "");
  if (!Number.isFinite(expiresAt) || expiresAt <= now) return null;
  return {
    expiresAt,
    tier: SUBSCRIPTION_PRODUCTS[expectedProductId] || null,
    catalogVersion: PLAY_CATALOG_VERSION,
    needsAcknowledgement: value.acknowledgementState !== "ACKNOWLEDGEMENT_STATE_ACKNOWLEDGED",
  };
}

function parseSubscriptionToken(value, now = Date.now()) {
  if (!value || !Array.isArray(value.lineItems)) return null;
  for (const line of value.lineItems) {
    if (!SUBSCRIPTION_PRODUCTS[line?.productId]) continue;
    const parsed = parseSubscriptionPurchase(value, line.productId, now);
    if (parsed?.tier) return { ...parsed, productId: line.productId };
  }
  return null;
}

async function publisherClient() {
  const encoded = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64;
  if (!encoded) throw new Error("google_play_verification_not_configured");
  const { GoogleAuth } = require("google-auth-library");
  const credentials = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
  const auth = new GoogleAuth({
    credentials,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"]
  });
  return auth.getClient();
}

async function verifyPlaySubscription(packageName, productId, purchaseToken) {
  if (!allowedPackages().has(packageName) || !SUBSCRIPTION_PRODUCTS[productId]) {
    throw new Error("unsupported_package_or_product");
  }
  const client = await publisherClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await client.request({ url: endpoint, method: "GET" });
  const result = parseSubscriptionPurchase(response.data, productId);
  if (!result?.tier) throw new Error("subscription_not_active");
  return result;
}

async function verifyPlaySubscriptionToken(packageName, purchaseToken) {
  if (!allowedPackages().has(packageName)) throw new Error("unsupported_package");
  const client = await publisherClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await client.request({ url: endpoint, method: "GET" });
  const result = parseSubscriptionToken(response.data);
  if (!result?.tier) throw new Error("subscription_not_active");
  return result;
}

async function verifyPlayOneTimePurchase(packageName, productId, purchaseToken) {
  const product = resolveOneTimeProduct(packageName, productId);
  if (!product || typeof purchaseToken !== "string" || !purchaseToken) {
    throw new Error("unsupported_package_or_product");
  }
  const client = await publisherClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(productId)}`
    + `/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await client.request({ url: endpoint, method: "GET" });
  if (response?.data?.purchaseState !== 0) throw new Error("purchase_not_completed");
  // A consumed one-time purchase can no longer back a non-consumable lifetime right.
  if (LIFETIME_PRODUCTS[productId] && response?.data?.consumptionState === 1) {
    throw new Error("purchase_not_completed");
  }
  return {
    ...product,
    packageName,
    needsAcknowledgement: response?.data?.acknowledgementState !== 1,
  };
}

async function acknowledgePlaySubscription(packageName, productId, purchaseToken) {
  if (!allowedPackages().has(packageName) || !SUBSCRIPTION_PRODUCTS[productId] || !purchaseToken) {
    throw new Error("unsupported_package_or_product");
  }
  const client = await publisherClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/subscriptions/${encodeURIComponent(productId)}`
    + `/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
  await client.request({ url: endpoint, method: "POST", data: {} });
}

async function acknowledgePlayOneTimePurchase(packageName, productId, purchaseToken) {
  if (!resolveOneTimeProduct(packageName, productId) || !purchaseToken) {
    throw new Error("unsupported_package_or_product");
  }
  const client = await publisherClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/products/${encodeURIComponent(productId)}`
    + `/tokens/${encodeURIComponent(purchaseToken)}:acknowledge`;
  await client.request({ url: endpoint, method: "POST", data: {} });
}

function findCodeByPurchaseToken(giftCodes, purchaseToken) {
  if (Array.isArray(giftCodes)) {
    const record = giftCodes.find(candidate => candidate?.purchaseToken === purchaseToken);
    return record ? { code: record.code, record } : null;
  }
  for (const [code, record] of giftCodes.entries()) {
    if (record && record.purchaseToken === purchaseToken) return { code, record };
  }
  return null;
}

function issuePlayActivationCode({
  activationCodes,
  giftCodes,
  saveActivationCodes,
  purchaseToken,
  productId,
  packageName,
  now = Date.now(),
}) {
  const product = resolveOneTimeProduct(packageName, productId);
  if (!Array.isArray(activationCodes) || !giftCodes || typeof saveActivationCodes !== "function"
      || typeof purchaseToken !== "string" || !purchaseToken || !product) {
    throw new Error("invalid_purchase_activation_request");
  }

  const existing = findCodeByPurchaseToken(activationCodes, purchaseToken)
    || findCodeByPurchaseToken(giftCodes, purchaseToken);
  if (existing) {
    const boundProduct = existing.record.productKey || existing.record.productId;
    const boundPackage = existing.record.purchasePackage || existing.record.packageName;
    const boundCatalogVersion = existing.record.catalogVersion;
    if (boundProduct !== productId
        || (boundPackage && boundPackage !== packageName)
        || boundCatalogVersion !== PLAY_CATALOG_VERSION) {
      throw new Error("purchase_binding_mismatch");
    }
    return {
      code: existing.code,
      tier: existing.record.tier,
      expires: existing.record.expires,
      productId,
      duplicate: true,
    };
  }

  const prefix = product.tier === "pro" ? "PRO" : "PREM";
  let code;
  do {
    code = `${prefix}-${crypto.randomBytes(4).toString("hex").toUpperCase()}`;
  } while (activationCodes.some(entry => entry?.code === code) || (giftCodes instanceof Map && giftCodes.has(code)));

  const expires = new Date(now + 365 * 24 * 60 * 60 * 1000).toISOString();
  const entry = {
    code,
    tier: product.tier,
    productKey: productId,
    purchasePackage: packageName,
    catalogVersion: PLAY_CATALOG_VERSION,
    note: `Purchased via Google Play (${productId})`,
    createdAt: new Date(now).toISOString(),
    expires,
    maxUses: 2,
    currentUses: 0,
    usedBy: [],
    purchaseToken,
  };
  activationCodes.push(entry);
  try {
    if (saveActivationCodes() === false) throw new Error("purchase_persistence_failed");
  } catch (error) {
    const index = activationCodes.indexOf(entry);
    if (index >= 0) activationCodes.splice(index, 1);
    throw error;
  }
  return { code, tier: product.tier, expires, productId, duplicate: false };
}

module.exports = {
  acknowledgePlayOneTimePurchase,
  acknowledgePlaySubscription,
  PLAY_CATALOG_VERSION,
  ONE_TIME_PRODUCTS,
  SUBSCRIPTION_PRODUCTS,
  findCodeByPurchaseToken,
  issuePlayActivationCode,
  isPlayBillingEnabled,
  LIFETIME_PRODUCTS,
  MAX_PROOF_EXPIRY_MS,
  lifetimeStatusResponse,
  loadLifetimePolicy,
  normalizeLifetimePolicy,
  resolveLifetimeProduct,
  parseSubscriptionPurchase,
  parseSubscriptionToken,
  resolveOneTimeProduct,
  verifyPlaySubscription,
  verifyPlaySubscriptionToken,
  verifyPlayOneTimePurchase
};
