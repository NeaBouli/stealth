const crypto = require("crypto");

const TOKEN_VERSION = "1";
const TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

function orderHash(externalOrderId) {
  return crypto.createHash("sha256").update(String(externalOrderId || "unknown")).digest("hex").slice(0, 32);
}

function audienceForProduct(productKey) {
  if (typeof productKey !== "string") return "securecall";
  if (productKey.includes("securechat")) return "securechat";
  if (productKey.includes("chameleon")) return "chameleon";
  if (productKey.includes("suite")) return "stealthx-suite";
  return "securecall";
}

function expectedTierForProduct(productKey) {
  const value = String(productKey || "").toLowerCase();
  if (value === "securechat_pro_lifetime" || value === "chameleon_pro_lifetime") return "PRO";
  if (value === "securechat_elite_lifetime" || value === "chameleon_elite_lifetime") return "ELITE";
  if (value === "vlabs_securecall_pro_lifetime" || value === "pro_lifetime" || value === "pro_monthly") return "PRO";
  if (
    value === "vlabs_securecall_premium_lifetime"
    || value === "premium_lifetime"
    || value === "premium_monthly"
  ) return "PREMIUM";
  if (value === "stealthx_suite_lifetime") return "ELITE";
  return null;
}

function validClaim(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength && /^[a-zA-Z0-9_.:-]+$/.test(value);
}

function issueEntitlementToken({ subject, productKey, tier, externalOrderId, nowSeconds = Math.floor(Date.now() / 1000) }) {
  const privateKeyPem = process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
  if (!privateKeyPem) return null;

  const audience = audienceForProduct(productKey);
  const normalizedTier = String(tier || "").toUpperCase();
  const expectedTier = expectedTierForProduct(productKey);
  if (!validClaim(subject, 160) || !validClaim(productKey, 120) || !["PRO", "PREMIUM", "ELITE"].includes(normalizedTier)) {
    throw new Error("Invalid entitlement claims");
  }
  if ((audience !== "securecall" || expectedTier) && expectedTier !== normalizedTier) {
    throw new Error("Entitlement product and tier mismatch");
  }
  const hashedOrder = orderHash(externalOrderId);
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
    `order=${hashedOrder}`,
  ].join("\n");
  const encodedPayload = base64Url(payload);
  const signature = crypto.sign(null, Buffer.from(encodedPayload, "utf8"), privateKeyPem);
  return `${encodedPayload}.${signature.toString("base64url")}`;
}

function verifyEntitlementToken(token, { expectedSubject, nowSeconds = Math.floor(Date.now() / 1000), expiryGraceSeconds = 0 } = {}) {
  const privateKeyPem = process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM;
  if (!privateKeyPem || typeof token !== "string" || token.length > 4096) throw new Error("Invalid entitlement token");
  const parts = token.split(".");
  if (parts.length !== 2 || parts.some(part => !part)) throw new Error("Invalid entitlement token");
  const [encodedPayload, encodedSignature] = parts;
  const signature = Buffer.from(encodedSignature, "base64url");
  const publicKey = crypto.createPublicKey(privateKeyPem);
  if (signature.length !== 64 || !crypto.verify(null, Buffer.from(encodedPayload, "utf8"), publicKey, signature)) {
    throw new Error("Invalid entitlement signature");
  }
  const lines = Buffer.from(encodedPayload, "base64url").toString("utf8").split("\n");
  if (lines.length !== 9) throw new Error("Invalid entitlement claims");
  const claims = Object.fromEntries(lines.map(line => {
    const separator = line.indexOf("=");
    if (separator <= 0) throw new Error("Invalid entitlement claims");
    return [line.slice(0, separator), line.slice(separator + 1)];
  }));
  const expectedKeys = ["aud", "exp", "iat", "iss", "order", "product", "sub", "tier", "v"];
  if (Object.keys(claims).sort().join(",") !== expectedKeys.join(",")) throw new Error("Invalid entitlement claims");
  const issuedAt = Number(claims.iat);
  const expiresAt = Number(claims.exp);
  if (claims.v !== TOKEN_VERSION || claims.iss !== "stealthx" || claims.sub !== expectedSubject) throw new Error("Invalid entitlement claims");
  if (!validClaim(claims.aud, 40) || !validClaim(claims.product, 120) || !["PRO", "PREMIUM", "ELITE"].includes(claims.tier)) {
    throw new Error("Invalid entitlement claims");
  }
  const expectedTier = expectedTierForProduct(claims.product);
  if (audienceForProduct(claims.product) !== claims.aud || (claims.aud !== "securecall" || expectedTier) && expectedTier !== claims.tier) {
    throw new Error("Entitlement product and tier mismatch");
  }
  if (!Number.isSafeInteger(issuedAt) || !Number.isSafeInteger(expiresAt) || expiresAt <= issuedAt || expiresAt - issuedAt > TOKEN_TTL_SECONDS) {
    throw new Error("Invalid entitlement lifetime");
  }
  if (issuedAt > nowSeconds + 300 || expiresAt + expiryGraceSeconds <= nowSeconds || !/^[a-f0-9]{32}$/.test(claims.order)) {
    throw new Error("Expired entitlement token");
  }
  return { ...claims, issuedAt, expiresAt };
}

module.exports = {
  issueEntitlementToken,
  verifyEntitlementToken,
  audienceForProduct,
  expectedTierForProduct,
  orderHash,
  TOKEN_TTL_SECONDS,
};
