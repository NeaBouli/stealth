const crypto = require("crypto");
const { PRODUCTS, CATALOG_VERSION } = require("./vlabs_fulfillment");

const TOKEN_VERSION = "1";
const TOKEN_VERSION_V2 = "2";
const TOKEN_TTL_SECONDS = 30 * 24 * 60 * 60;
const SIGNING_UNAVAILABLE_CODE = "ENTITLEMENT_SIGNING_UNAVAILABLE";

function signingUnavailableError() {
  const error = new Error("Entitlement signing is unavailable");
  error.code = SIGNING_UNAVAILABLE_CODE;
  return error;
}

function signingPrivateKey() {
  const encoded = process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM_BASE64;
  if (encoded) {
    try {
      return Buffer.from(encoded, "base64").toString("utf8");
    } catch {
      return null;
    }
  }
  return process.env.ENTITLEMENT_SIGNING_PRIVATE_KEY_PEM || null;
}

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

function productForKey(productKey) {
  for (const productId of Object.keys(PRODUCTS)) {
    if (PRODUCTS[productId].productKey === productKey) return PRODUCTS[productId];
  }
  return null;
}

function validClaim(value, maxLength) {
  return typeof value === "string" && value.length > 0 && value.length <= maxLength && /^[a-zA-Z0-9_.:-]+$/.test(value);
}

function validateV2Contract({ productKey, normalizedTier, catalogVersion, offerVersion, releaseId }) {
  if (!validClaim(catalogVersion, 120) || !validClaim(offerVersion, 160) || !validClaim(releaseId, 160)) {
    throw new Error("Invalid entitlement claims");
  }
  if (catalogVersion !== CATALOG_VERSION) throw new Error("Invalid entitlement claims");
  const product = productForKey(productKey);
  if (!product) throw new Error("Invalid entitlement claims");
  if (product.tier !== normalizedTier.toLowerCase()
    || product.offerVersion !== offerVersion
    || product.releaseId !== releaseId) {
    throw new Error("Invalid entitlement claims");
  }
  return product;
}

function issueEntitlementToken({ subject, productKey, tier, externalOrderId, catalogVersion, offerVersion, releaseId, nowSeconds = Math.floor(Date.now() / 1000) }) {
  const privateKeyPem = signingPrivateKey();
  if (!privateKeyPem) return null;

  const audience = audienceForProduct(productKey);
  const normalizedTier = String(tier || "").toUpperCase();
  if (!validClaim(subject, 160) || !validClaim(productKey, 120) || !["PRO", "PREMIUM", "ELITE"].includes(normalizedTier)) {
    throw new Error("Invalid entitlement claims");
  }
  // v2 is issued only when the complete immutable contract tuple is present and
  // exactly matches the static catalog entry for the product. Anything less
  // stays on the legacy v1 shape.
  const wantsV2 = catalogVersion !== undefined || offerVersion !== undefined || releaseId !== undefined;
  if (wantsV2) {
    validateV2Contract({ productKey, normalizedTier, catalogVersion, offerVersion, releaseId });
  }
  const hashedOrder = orderHash(externalOrderId);
  const expiresAt = nowSeconds + TOKEN_TTL_SECONDS;
  const lines = [
    `v=${wantsV2 ? TOKEN_VERSION_V2 : TOKEN_VERSION}`,
    "iss=stealthx",
    `aud=${audience}`,
    `sub=${subject}`,
    `tier=${normalizedTier}`,
    `product=${productKey}`,
    `iat=${nowSeconds}`,
    `exp=${expiresAt}`,
    `order=${hashedOrder}`,
  ];
  if (wantsV2) {
    lines.push(`catalog=${catalogVersion}`, `offer=${offerVersion}`, `release=${releaseId}`);
  }
  const encodedPayload = base64Url(lines.join("\n"));
  const signature = crypto.sign(null, Buffer.from(encodedPayload, "utf8"), privateKeyPem);
  return `${encodedPayload}.${signature.toString("base64url")}`;
}

function parseClaims(encodedPayload) {
  const lines = Buffer.from(encodedPayload, "base64url").toString("utf8").split("\n");
  const claims = Object.fromEntries(lines.map(line => {
    const separator = line.indexOf("=");
    if (separator <= 0) throw new Error("Invalid entitlement claims");
    return [line.slice(0, separator), line.slice(separator + 1)];
  }));
  return { lines, claims };
}

function verifyEntitlementToken(token, { expectedSubject, nowSeconds = Math.floor(Date.now() / 1000), expiryGraceSeconds = 0 } = {}) {
  const privateKeyPem = signingPrivateKey();
  if (!privateKeyPem) throw signingUnavailableError();
  if (typeof token !== "string" || token.length > 4096) throw new Error("Invalid entitlement token");
  const parts = token.split(".");
  if (parts.length !== 2 || parts.some(part => !part)) throw new Error("Invalid entitlement token");
  const [encodedPayload, encodedSignature] = parts;
  const signature = Buffer.from(encodedSignature, "base64url");
  const publicKey = crypto.createPublicKey(privateKeyPem);
  if (signature.length !== 64 || !crypto.verify(null, Buffer.from(encodedPayload, "utf8"), publicKey, signature)) {
    throw new Error("Invalid entitlement signature");
  }
  const { lines, claims } = parseClaims(encodedPayload);
  const isV2 = claims.v === TOKEN_VERSION_V2;
  if (claims.v !== TOKEN_VERSION && !isV2) throw new Error("Invalid entitlement claims");
  const expectedKeys = isV2
    ? ["aud", "catalog", "exp", "iat", "iss", "offer", "order", "product", "release", "sub", "tier", "v"]
    : ["aud", "exp", "iat", "iss", "order", "product", "sub", "tier", "v"];
  if (lines.length !== expectedKeys.length || Object.keys(claims).sort().join(",") !== expectedKeys.join(",")) {
    throw new Error("Invalid entitlement claims");
  }
  const issuedAt = Number(claims.iat);
  const expiresAt = Number(claims.exp);
  if (claims.iss !== "stealthx" || claims.sub !== expectedSubject) throw new Error("Invalid entitlement claims");
  if (!validClaim(claims.aud, 40) || !validClaim(claims.product, 120) || !["PRO", "PREMIUM", "ELITE"].includes(claims.tier)) {
    throw new Error("Invalid entitlement claims");
  }
  if (isV2) {
    // Exact product/tier/audience/contract mapping; any drift fails closed.
    const product = productForKey(claims.product);
    if (!product
      || product.tier !== claims.tier.toLowerCase()
      || claims.aud !== audienceForProduct(product.productKey)
      || claims.catalog !== CATALOG_VERSION
      || claims.offer !== product.offerVersion
      || claims.release !== product.releaseId) {
      throw new Error("Invalid entitlement claims");
    }
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
  orderHash,
  signingPrivateKey,
  SIGNING_UNAVAILABLE_CODE,
  TOKEN_TTL_SECONDS,
  TOKEN_VERSION_V2,
};
