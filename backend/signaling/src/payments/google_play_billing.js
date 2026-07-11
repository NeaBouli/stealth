const ONE_TIME_PRODUCTS = Object.freeze({
  securecall_pro_lifetime: "pro",
  securecall_premium_lifetime: "premium",
  securecall_premium_activation_code: "premium"
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

function findCodeByPurchaseToken(giftCodes, purchaseToken) {
  for (const [code, record] of giftCodes.entries()) {
    if (record && record.purchaseToken === purchaseToken) return { code, record };
  }
  return null;
}

module.exports = { ONE_TIME_PRODUCTS, findCodeByPurchaseToken, resolveOneTimeProduct };
