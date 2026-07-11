const crypto = require("crypto");

const TOKEN_VERSION = "1";
const TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

function audienceForProduct(productKey) {
  if (typeof productKey !== "string") return "securecall";
  if (productKey.includes("securechat")) return "securechat";
  if (productKey.includes("chameleon")) return "chameleon";
  if (productKey.includes("suite")) return "stealthx-suite";
  return "securecall";
}

function validClaim(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength && /^[a-zA-Z0-9_.:-]+$/.test(value);
}

function issueEntitlementToken({ subject, productKey, tier, externalOrderId, nowSeconds = Math.floor(Date.now() / 1000) }) {
  const privateKeyPem = process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
  if (!privateKeyPem) return null;

  const audience = audienceForProduct(productKey);
  const normalizedTier = String(tier || "").toUpperCase();
  if (!validClaim(subject, 160) || !validClaim(productKey, 120) || !["PRO", "PREMIUM", "ELITE"].includes(normalizedTier)) {
    throw new Error("Invalid entitlement claims");
  }
  const orderHash = crypto.createHash("sha256").update(String(externalOrderId || "unknown")).digest("hex").slice(0, 32);
  const expiresAt = nowSeconds + TOKEN_TTL_SECONDS;
  const payload = [
    `v=${TOKEN_VERSION}`,
    "iss=stealthx",
    `aud=${audience}`,
    `sub=${subject}`,
    `tier=${normalizedTier}`,
    `product=${productKey}`,
    `iat=${nowSeconds}`,
    `exp=${expiresAt}`,
    `order=${orderHash}`,
  ].join("\n");
  const encodedPayload = base64Url(payload);
  const signature = crypto.sign(null, Buffer.from(encodedPayload, "utf8"), privateKeyPem);
  return `${encodedPayload}.${signature.toString("base64url")}`;
}

module.exports = { issueEntitlementToken, audienceForProduct, TOKEN_TTL_SECONDS };
