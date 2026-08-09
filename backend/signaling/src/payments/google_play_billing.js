const crypto = require("crypto");

const ONE_TIME_PRODUCTS = Object.freeze({
  securecall_pro_lifetime: "pro",
  securecall_premium_lifetime: "premium",
  securecall_premium_activation_code: "premium"
});

const SUBSCRIPTION_PRODUCTS = Object.freeze({
  securecall_pro_monthly: "pro",
  securecall_pro_yearly: "pro",
  securecall_premium_monthly: "premium",
  securecall_premium_yearly: "premium"
});

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
  return tier ? { productId, tier } : null;
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
  return { expiresAt, tier: SUBSCRIPTION_PRODUCTS[expectedProductId] || null };
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
  return { ...product, packageName };
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
    if (boundProduct !== productId || (boundPackage && boundPackage !== packageName)) {
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
  ONE_TIME_PRODUCTS,
  SUBSCRIPTION_PRODUCTS,
  findCodeByPurchaseToken,
  issuePlayActivationCode,
  parseSubscriptionPurchase,
  parseSubscriptionToken,
  resolveOneTimeProduct,
  verifyPlaySubscription,
  verifyPlaySubscriptionToken,
  verifyPlayOneTimePurchase
};
