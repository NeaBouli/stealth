"use strict";

/**
 * IFR holder eligibility for the central web checkout (journey stealthx_ifr_holder_v1).
 *
 * This server never creates a checkout and never prices anything. It proves, for one
 * immutable product offer, that a browser wallet holds a positive balance of the
 * approved token, and returns a short-lived eligibility command signed for the central
 * checkout:
 *
 *  1. POST /vlabs/ifr-eligibility/challenge issues a server-side EIP-4361-shaped
 *     message bound to the exact configured origin, the approved chain and token, the
 *     product and its offer version, a random single-use nonce and a 5-minute lifetime.
 *  2. POST /vlabs/ifr-eligibility consumes the nonce exactly once (burned on every
 *     outcome), verifies the personal_sign signature over the stored message, reads the
 *     balance through the configured read-only provider and, only for a positive
 *     balance, signs the eligibility with the HMAC envelope shared with the central
 *     checkout (purpose-separated key, key id, source, timestamp).
 *
 * Every configuration value is a required operator input with no default. Missing
 * configuration keeps both routes closed (503). The legacy /stripe/* IFR routes stay
 * retired. Wallet addresses, signatures and balances never leave this server; the
 * command carries only a one-way proof reference.
 */

const crypto = require("crypto");
const fs = require("fs");
const { ethers } = require("ethers");
const {
  buildChallengeMessage,
  buildProofDigest,
  normalizeWalletAddress,
  IFR_PROOF_TTL_MS,
} = require("./ifr_checkout");
const { PRODUCTS } = require("./vlabs_fulfillment");
const { resolveTurnSecret } = require("../security/turn_secret");

const ELIGIBILITY_PURPOSE = "vlabs_ifr_eligibility";
const ELIGIBILITY_SIGNING_PURPOSE = "vlabs.stealthx-ifr-eligibility.v1";
const ELIGIBILITY_SCHEMA = "vlabs.stealthx.ifr_eligibility.v1";
const ELIGIBILITY_SOURCE = "stealthx";
const ELIGIBILITY_VERSION = "1";
const ELIGIBILITY_LIFETIME_S = 300;
const MIN_SECRET_LENGTH = 32;
// Raw value or a protected 64-hex secret file, read by the existing bounded reader.
const ELIGIBILITY_SECRET_NAMES = Object.freeze({
  value: "VLABS_IFR_ELIGIBILITY_SECRET",
  file: "VLABS_IFR_ELIGIBILITY_SECRET_FILE",
  code: "invalid_ifr_eligibility_secret_configuration",
  protectedFile: true,
});

// Returns the complete approved configuration, or null when anything is missing.
function eligibilityConfig(env, fileSystem = fs) {
  if (env.VLABS_IFR_ELIGIBILITY_ENABLED !== "true") return null;
  try {
    const origin = new URL(String(env.VLABS_IFR_ELIGIBILITY_ORIGIN || ""));
    if (origin.protocol !== "https:" || origin.origin !== env.VLABS_IFR_ELIGIBILITY_ORIGIN) return null;
    const chainText = String(env.VLABS_IFR_ELIGIBILITY_CHAIN_ID || "");
    if (!/^[1-9]\d{0,15}$/.test(chainText)) return null;
    const tokenAddress = normalizeWalletAddress(env.VLABS_IFR_ELIGIBILITY_TOKEN_ADDRESS);
    const rpcUrls = String(env.VLABS_IFR_ELIGIBILITY_RPC_URLS || "").split(",").map((url) => url.trim()).filter(Boolean);
    if (!rpcUrls.length || rpcUrls.some((url) => new URL(url).protocol !== "https:")) return null;
    const keyId = String(env.VLABS_IFR_ELIGIBILITY_KEY_ID || "");
    const secret = resolveTurnSecret(env, fileSystem, ELIGIBILITY_SECRET_NAMES);
    if (!/^[A-Za-z0-9._-]{1,64}$/.test(keyId) || typeof secret !== "string" || secret.length < MIN_SECRET_LENGTH) return null;
    return { origin: origin.origin, domain: origin.host, chainId: Number(chainText), tokenAddress, rpcUrls, keyId, secret };
  } catch (_e) {
    return null;
  }
}

// Only immutable lifetime offers of the existing receiver contract can be signalled.
function eligibleProduct(productId) {
  if (typeof productId !== "string" || !Object.prototype.hasOwnProperty.call(PRODUCTS, productId)) return null;
  const product = PRODUCTS[productId];
  if (product.kind === "custom_id" || !product.offerVersion || !product.catalogVersion) return null;
  return product;
}

function buildEligibilityStatement(contract) {
  return "StealthX IFR holder eligibility for product " + contract.productId + " offer " + contract.offerVersion +
    " (token " + contract.tokenAddress + " on chain " + contract.chainId + "). " +
    "Sign this message only to prove wallet ownership; it cannot move tokens or grant spending permission.";
}

function createEligibilityChallenge({ config, productId, product, walletAddress, nonce, now }) {
  const contract = {
    purpose: ELIGIBILITY_PURPOSE,
    domain: config.domain,
    uri: config.origin,
    chainId: config.chainId,
    version: ELIGIBILITY_VERSION,
    tokenAddress: config.tokenAddress,
    productId,
    catalogVersion: product.catalogVersion,
    offerVersion: product.offerVersion,
    walletAddress,
    nonce,
    issuedAt: new Date(now).toISOString(),
    expirationTime: new Date(now + IFR_PROOF_TTL_MS).toISOString(),
    createdAt: now,
  };
  contract.statement = buildEligibilityStatement(contract);
  contract.message = buildChallengeMessage(contract);
  return Object.freeze(contract);
}

// Every bound field must still match the current approved configuration and product
// offer; the stored message must be exactly the canonical rebuild.
function isEligibilityChallengeIntact(challenge, config) {
  if (!challenge || typeof challenge !== "object" || challenge.purpose !== ELIGIBILITY_PURPOSE) return false;
  const product = eligibleProduct(challenge.productId);
  if (!product || challenge.domain !== config.domain || challenge.uri !== config.origin
    || challenge.chainId !== config.chainId || challenge.tokenAddress !== config.tokenAddress
    || challenge.version !== ELIGIBILITY_VERSION || challenge.catalogVersion !== product.catalogVersion
    || challenge.offerVersion !== product.offerVersion || !Number.isInteger(challenge.createdAt)) return false;
  const issuedAtMs = Date.parse(challenge.issuedAt);
  if (issuedAtMs !== challenge.createdAt || Date.parse(challenge.expirationTime) !== issuedAtMs + IFR_PROOF_TTL_MS) return false;
  try {
    return challenge.statement === buildEligibilityStatement(challenge) && buildChallengeMessage(challenge) === challenge.message;
  } catch (_e) {
    return false;
  }
}

function eligibilitySigningKey(secret) {
  return crypto.createHmac("sha256", secret).update(ELIGIBILITY_SIGNING_PURPOSE).digest("hex");
}

// HMAC-SHA256 over "<timestamp>." + raw payload bytes, the envelope verified centrally.
function signEligibility(config, payload, timestamp) {
  const raw = Buffer.from(JSON.stringify(payload), "utf8");
  const signature = crypto.createHmac("sha256", eligibilitySigningKey(config.secret))
    .update(Buffer.from(`${timestamp}.`, "utf8")).update(raw).digest("hex");
  return { source: ELIGIBILITY_SOURCE, keyId: config.keyId, timestamp: String(timestamp), signature, payload: raw.toString("base64url") };
}

function setupIfrEligibilityRoutes(app, {
  env = process.env,
  challengeStore,
  readHolderBalance,
  rateLimit,
  maxChallenges = 10000,
  now = () => Date.now(),
  randomBytes = crypto.randomBytes,
  // The global CORS allow-list; a configured origin outside it could never reach the
  // routes from a browser, so the path stays closed instead of half-open.
  corsAllowedOrigins = null,
}) {
  const closed = (res) => res.status(503).json({ error: "ifr_eligibility_unavailable" });
  const routeConfig = () => {
    const config = eligibilityConfig(env);
    if (config && Array.isArray(corsAllowedOrigins) && !corsAllowedOrigins.includes(config.origin)) return null;
    return config;
  };
  const requireOrigin = (req, config) => req.get("origin") === config.origin;

  app.post("/vlabs/ifr-eligibility/challenge", rateLimit, (req, res) => {
    const config = routeConfig();
    if (!config) return closed(res);
    if (!requireOrigin(req, config)) return res.status(403).json({ error: "origin_not_allowed" });
    const productId = typeof req.body?.productId === "string" ? req.body.productId : "";
    const product = eligibleProduct(productId);
    if (!product) return res.status(400).json({ error: "invalid_product" });
    let walletAddress;
    try {
      walletAddress = normalizeWalletAddress(req.body?.walletAddress);
    } catch (_e) {
      return res.status(400).json({ error: "invalid_wallet" });
    }
    const contract = createEligibilityChallenge({
      config, productId, product, walletAddress, nonce: randomBytes(32).toString("hex"), now: now(),
    });
    while (challengeStore.size >= maxChallenges) {
      const oldest = challengeStore.keys().next();
      if (oldest.done) break;
      challengeStore.delete(oldest.value);
    }
    challengeStore.set(contract.nonce, contract);
    res.json({ nonce: contract.nonce, message: contract.message, expiresAt: contract.expirationTime });
  });

  app.post("/vlabs/ifr-eligibility", rateLimit, async (req, res) => {
    const config = routeConfig();
    if (!config) return closed(res);
    if (!requireOrigin(req, config)) return res.status(403).json({ error: "origin_not_allowed" });
    const nonce = typeof req.body?.nonce === "string" ? req.body.nonce : "";
    const signature = typeof req.body?.signature === "string" ? req.body.signature.trim() : "";
    const productId = typeof req.body?.productId === "string" ? req.body.productId : "";
    let walletAddress;
    try {
      walletAddress = normalizeWalletAddress(req.body?.walletAddress);
    } catch (_e) {
      return res.status(400).json({ error: "invalid_wallet" });
    }
    if (!nonce || !signature) return res.status(400).json({ error: "wallet_signature_required" });

    // Consume once, before signature recovery or any provider call; burned on every outcome.
    const challenge = challengeStore.get(nonce);
    challengeStore.delete(nonce);
    if (!challenge || challenge.nonce !== nonce || !isEligibilityChallengeIntact(challenge, config)) {
      return res.status(403).json({ error: "invalid_wallet_challenge" });
    }
    const nowMs = now();
    if (nowMs >= Date.parse(challenge.expirationTime)) return res.status(403).json({ error: "wallet_challenge_expired" });
    if (challenge.productId !== productId || challenge.walletAddress !== walletAddress) {
      return res.status(403).json({ error: "wallet_challenge_mismatch" });
    }
    try {
      if (ethers.verifyMessage(challenge.message, signature).toLowerCase() !== walletAddress.toLowerCase()) {
        return res.status(403).json({ error: "wallet_signature_invalid" });
      }
    } catch (_e) {
      return res.status(403).json({ error: "wallet_signature_invalid" });
    }

    let balance;
    try {
      const result = await readHolderBalance({
        walletAddress, chainId: config.chainId, tokenAddress: config.tokenAddress, rpcUrls: config.rpcUrls,
      });
      if (!result || result.success !== true || typeof result.balance !== "bigint") throw new Error("provider");
      balance = result.balance;
    } catch (_e) {
      return res.status(503).json({ error: "ifr_provider_unavailable" });
    }
    if (balance <= 0n) return res.status(403).json({ error: "ifr_not_eligible" });

    const issuedAt = Math.floor(nowMs / 1000);
    const payload = {
      schema: ELIGIBILITY_SCHEMA,
      requestId: "sxr_" + randomBytes(24).toString("base64url"),
      proofRef: buildProofDigest(challenge.message, signature, ELIGIBILITY_PURPOSE),
      productId: challenge.productId,
      catalogVersion: challenge.catalogVersion,
      offerVersion: challenge.offerVersion,
      chainId: config.chainId,
      tokenAddress: config.tokenAddress,
      issuedAt,
      expiresAt: issuedAt + ELIGIBILITY_LIFETIME_S,
    };
    res.set("Cache-Control", "no-store");
    res.json({ eligibility: signEligibility(config, payload, issuedAt), expiresAt: payload.expiresAt });
  });
}

module.exports = {
  setupIfrEligibilityRoutes,
  eligibilityConfig,
  eligibleProduct,
  createEligibilityChallenge,
  isEligibilityChallengeIntact,
  eligibilitySigningKey,
  signEligibility,
  ELIGIBILITY_PURPOSE,
  ELIGIBILITY_SIGNING_PURPOSE,
  ELIGIBILITY_SCHEMA,
  ELIGIBILITY_LIFETIME_S,
};
