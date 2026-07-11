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

async function verifyPlaySubscription(packageName, productId, purchaseToken) {
  if (!allowedPackages().has(packageName) || !SUBSCRIPTION_PRODUCTS[productId]) {
    throw new Error("unsupported_package_or_product");
  }
  const encoded = process.env.GOOGLE_PLAY_SERVICE_ACCOUNT_BASE64;
  if (!encoded) throw new Error("google_play_verification_not_configured");
  const { GoogleAuth } = require("google-auth-library");
  const credentials = JSON.parse(Buffer.from(encoded, "base64").toString("utf8"));
  const auth = new GoogleAuth({
    credentials,
    scopes: ["https://www.googleapis.com/auth/androidpublisher"]
  });
  const client = await auth.getClient();
  const endpoint = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications/"
    + `${encodeURIComponent(packageName)}/purchases/subscriptionsv2/tokens/${encodeURIComponent(purchaseToken)}`;
  const response = await client.request({ url: endpoint, method: "GET" });
  const result = parseSubscriptionPurchase(response.data, productId);
  if (!result?.tier) throw new Error("subscription_not_active");
  return result;
}

function findCodeByPurchaseToken(giftCodes, purchaseToken) {
  for (const [code, record] of giftCodes.entries()) {
    if (record && record.purchaseToken === purchaseToken) return { code, record };
  }
  return null;
}

module.exports = {
  ONE_TIME_PRODUCTS,
  SUBSCRIPTION_PRODUCTS,
  findCodeByPurchaseToken,
  parseSubscriptionPurchase,
  resolveOneTimeProduct,
  verifyPlaySubscription
};
