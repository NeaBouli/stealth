"use strict";

/**
 * IFR browser checkout — wallet-proof challenge + dynamic Stripe checkout.
 *
 * Both routes stay default-closed behind LEGACY_STRIPE_CHECKOUT_ENABLED !== 'true'
 * (HTTP 410 checkout_moved_to_vlabs). Nothing in this module enables sales.
 *
 * Wallet proof contract:
 * - The challenge message is a canonical EIP-4361-shaped (Sign-In with Ethereum)
 *   message generated entirely server-side. It binds exactly:
 *     domain stealthx.tech, URI https://stealthx.tech/ifr.html,
 *     Ethereum Mainnet chainId 1, the EIP-55 normalized wallet address,
 *     the selected product/tier, a cryptographically random nonce and
 *     issuedAt/expirationTime with a five-minute lifetime.
 * - The complete contract is frozen and kept in the existing in-memory
 *   single-use challenge store (siweChallenges).
 * - At checkout the nonce is consumed exactly once, before any Stripe call or
 *   balance lookup, and every failure keeps it burned (fail-closed): unknown
 *   nonce, stale proof (expired exactly at expirationTime), wallet/tier
 *   mismatch, contract drift (domain/URI/chain/version/purpose, nonce-key
 *   equality, createdAt/issuedAt/expirationTime consistency, message
 *   integrity) and invalid signature all reject.
 * - A typed wallet address alone never qualifies; a valid personal_sign
 *   signature over the exact stored message is required.
 * - Any positive IFR balance is eligible for the discount; there is no balance
 *   threshold and no redemption cap.
 * - Stripe metadata carries only a one-way SHA-256 proof digest (bound to the
 *   canonical message and signature), the proof version and eligibility/price
 *   facts — never the raw wallet address or the raw IFR balance. Payment
 *   method selection stays provider-configured (no payment_method_types).
 */

const crypto = require("crypto");
const { ethers } = require("ethers");

const IFR_PROOF_DOMAIN = "stealthx.tech";
const IFR_PROOF_URI = "https://stealthx.tech/ifr.html";
const IFR_PROOF_CHAIN_ID = 1;
const IFR_PROOF_VERSION = "1";
const IFR_PROOF_PURPOSE = "stripe_ifr_discount";
const IFR_PROOF_TTL_MS = 5 * 60 * 1000; // 5 minutes

// EIP-55 checksum normalization. Requires the 0x-prefixed 40-hex form and
// rejects malformed input and mixed-case addresses with an invalid checksum.
function normalizeWalletAddress(walletAddress) {
  const text = String(walletAddress || "").trim();
  if (!/^0x[0-9a-fA-F]{40}$/.test(text)) throw new Error("invalid_wallet");
  return ethers.getAddress(text);
}

function buildChallengeStatement(tier) {
  return "StealthX IFR holder discount for product " + tier + " (50% Stripe checkout). " +
    "Sign this message only to prove wallet ownership; it cannot move tokens or grant spending permission.";
}

// Canonical EIP-4361-shaped message (statement and header fields carry no
// line breaks: tier comes from the server-side catalog allowlist).
function buildChallengeMessage(contract) {
  return contract.domain + " wants you to sign in with your Ethereum account:\n" +
    contract.walletAddress + "\n" +
    "\n" +
    buildChallengeStatement(contract.tier) + "\n" +
    "\n" +
    "URI: " + contract.uri + "\n" +
    "Version: " + contract.version + "\n" +
    "Chain ID: " + contract.chainId + "\n" +
    "Nonce: " + contract.nonce + "\n" +
    "Issued At: " + contract.issuedAt + "\n" +
    "Expiration Time: " + contract.expirationTime;
}

// One-way proof reference for Stripe metadata: binds the exact canonical
// message and the presented signature without revealing either. Domain-
// separated by the proof purpose so digests cannot be reused across purposes.
function buildProofDigest(message, signature) {
  return crypto.createHash("sha256")
    .update(IFR_PROOF_PURPOSE + "\n" + message + "\n" + signature, "utf8")
    .digest("hex");
}

function createChallengeContract({ tier, walletAddress, nonce, now }) {
  const contract = {
    purpose: IFR_PROOF_PURPOSE,
    domain: IFR_PROOF_DOMAIN,
    uri: IFR_PROOF_URI,
    chainId: IFR_PROOF_CHAIN_ID,
    version: IFR_PROOF_VERSION,
    tier,
    walletAddress,
    nonce,
    issuedAt: new Date(now).toISOString(),
    expirationTime: new Date(now + IFR_PROOF_TTL_MS).toISOString(),
    createdAt: now, // numeric ms; used by the server cleanup interval
  };
  contract.message = buildChallengeMessage(contract);
  return Object.freeze(contract);
}

// Structural + cryptographic integrity of a stored contract: every bound
// field must match the server-side constants, the temporal fields must be
// mutually consistent (integer createdAt, issuedAt == createdAt, expiration
// exactly IFR_PROOF_TTL_MS after issuedAt) and the stored message must be
// exactly the canonical message rebuilt from the stored fields.
function isChallengeContractIntact(challenge) {
  if (!challenge || typeof challenge !== "object") return false;
  if (challenge.purpose !== IFR_PROOF_PURPOSE) return false;
  if (challenge.domain !== IFR_PROOF_DOMAIN) return false;
  if (challenge.uri !== IFR_PROOF_URI) return false;
  if (challenge.chainId !== IFR_PROOF_CHAIN_ID) return false;
  if (challenge.version !== IFR_PROOF_VERSION) return false;
  for (const field of ["tier", "walletAddress", "nonce", "issuedAt", "expirationTime", "message"]) {
    if (typeof challenge[field] !== "string" || challenge[field].length === 0) return false;
  }
  if (!Number.isInteger(challenge.createdAt)) return false;
  const issuedAtMs = Date.parse(challenge.issuedAt);
  const expirationMs = Date.parse(challenge.expirationTime);
  if (!Number.isFinite(issuedAtMs) || !Number.isFinite(expirationMs)) return false;
  if (issuedAtMs !== challenge.createdAt) return false;
  if (expirationMs !== issuedAtMs + IFR_PROOF_TTL_MS) return false;
  try {
    return buildChallengeMessage(challenge) === challenge.message;
  } catch (_e) {
    return false;
  }
}

// Rate limit: 5 checkout requests per IP per 10 minutes (unchanged behavior).
function createCheckoutRateLimiter({ limits, getClientIp, now = () => Date.now(), max = 5, windowMs = 600000 }) {
  return function checkoutRateLimit(req, res, next) {
    const ip = getClientIp(req);
    const nowMs = now();
    if (!limits.has(ip)) limits.set(ip, []);
    const attempts = limits.get(ip);
    while (attempts.length > 0 && nowMs - attempts[0] > windowMs) attempts.shift();
    if (attempts.length >= max) {
      return res.status(429).json({ error: "rate_limited", retry_after_seconds: Math.round(windowMs / 1000) });
    }
    attempts.push(nowMs);
    next();
  };
}

function setupIfrCheckoutRoutes(app, {
  env = process.env,
  challengeStore,
  licenses,
  getCheckoutProduct,
  verifyIfrHolding,
  createStripe = (secretKey) => require("stripe")(secretKey),
  checkoutRateLimit,
  now = () => Date.now(),
  randomBytes = crypto.randomBytes,
}) {
  app.post("/stripe/ifr-discount-challenge", checkoutRateLimit, (req, res) => {
    if (env.LEGACY_STRIPE_CHECKOUT_ENABLED !== "true") {
      return res.status(410).json({ error: "checkout_moved_to_vlabs" });
    }
    const tier = (req.body?.tier || "").trim();
    const walletAddress = (req.body?.walletAddress || "").trim();
    // Only catalog-allowlisted products may enter the IFR discount flow.
    if (!getCheckoutProduct(tier)) {
      return res.status(400).json({ error: "invalid_tier" });
    }
    let normalizedWallet;
    try {
      normalizedWallet = normalizeWalletAddress(walletAddress);
    } catch (_e) {
      return res.status(400).json({ error: "invalid_wallet" });
    }

    const contract = createChallengeContract({
      tier,
      walletAddress: normalizedWallet,
      nonce: randomBytes(32).toString("hex"),
      now: now(),
    });
    challengeStore.set(contract.nonce, contract);
    res.json({ nonce: contract.nonce, message: contract.message });
  });

  app.post("/stripe/create-dynamic-checkout", checkoutRateLimit, async (req, res) => {
    if (env.LEGACY_STRIPE_CHECKOUT_ENABLED !== "true") {
      return res.status(410).json({ error: "checkout_moved_to_vlabs" });
    }
    const tier = typeof req.body?.tier === "string" ? req.body.tier.trim() : "";
    // Only catalog-allowlisted products may enter web checkout; the
    // stealthx_suite_lifetime bundle is excluded until fulfillment is ready.
    const checkoutProduct = getCheckoutProduct(tier);
    if (!checkoutProduct) {
      return res.status(400).json({ error: "invalid_tier" });
    }
    const price = licenses.getCurrentPrice(tier);
    if (price === null) {
      return res.status(410).json({ error: "Sold out" });
    }

    const lic = licenses.LICENSES[tier];
    const requestedIfrDiscount = req.body?.ifrDiscount === true || req.body?.ifrDiscount === "true";
    const walletAddress = (req.body?.walletAddress || "").trim();
    const walletSignature = (req.body?.walletSignature || "").trim();
    const walletNonce = (req.body?.walletNonce || "").trim();
    let checkoutPrice = price;
    let ifrDiscountApplied = false;
    let ifrBalanceAmount = "";
    let ifrProofDigest = "";
    let normalizedWallet = "";

    if (requestedIfrDiscount) {
      try {
        normalizedWallet = normalizeWalletAddress(walletAddress);
      } catch (_e) {
        return res.status(400).json({ error: "invalid_wallet" });
      }
      // A typed wallet address alone never qualifies: the single-use signed
      // server challenge is mandatory.
      if (!walletSignature || !walletNonce) {
        return res.status(400).json({ error: "wallet_signature_required" });
      }

      // Consume once — before any Stripe call or balance lookup. The nonce is
      // burned on every outcome below, so replays and oracle probing fail.
      const challenge = challengeStore.get(walletNonce);
      challengeStore.delete(walletNonce);
      if (!challenge || challenge.purpose !== IFR_PROOF_PURPOSE || challenge.nonce !== walletNonce || !isChallengeContractIntact(challenge)) {
        return res.status(403).json({ error: "invalid_wallet_challenge" });
      }
      if (now() >= Date.parse(challenge.expirationTime)) {
        return res.status(403).json({ error: "wallet_challenge_expired" });
      }
      if (challenge.tier !== tier || challenge.walletAddress !== normalizedWallet) {
        return res.status(403).json({ error: "wallet_challenge_mismatch" });
      }

      try {
        const recovered = ethers.verifyMessage(challenge.message, walletSignature);
        if (recovered.toLowerCase() !== normalizedWallet.toLowerCase()) {
          return res.status(403).json({ error: "wallet_signature_invalid" });
        }
      } catch (e) {
        return res.status(403).json({ error: "wallet_signature_invalid" });
      }

      // One-way proof reference: only this digest (never the raw wallet,
      // signature or balance) may leave the server in payment metadata.
      ifrProofDigest = buildProofDigest(challenge.message, walletSignature);

      const ifr = await verifyIfrHolding(normalizedWallet);
      ifrBalanceAmount = ifr.balanceAmount || ifr.lockedAmount || "";
      if (!ifr.success) {
        const errorCode = ifr.error === "insufficient" ? "ifr_not_eligible" : (ifr.error || "ifr_not_eligible");
        return res.status(403).json({ error: errorCode, balanceAmount: ifrBalanceAmount });
      }

      checkoutPrice = Math.max(50, Math.round(price * 0.5));
      ifrDiscountApplied = true;
    }

    const secretKey = env.STRIPE_SECRET_KEY;
    if (!secretKey) {
      return res.status(503).json({ error: "Stripe not configured" });
    }
    try {
      const stripe = createStripe(secretKey);
      const priceData = {
        currency: "eur",
        unit_amount: checkoutPrice
      };
      if (lic.stripeProductId) {
        priceData.product = lic.stripeProductId;
      } else {
        priceData.product_data = { name: lic.name || tier };
      }
      const session = await stripe.checkout.sessions.create({
        line_items: [{
          price_data: priceData,
          quantity: 1
        }],
        mode: "payment",
        success_url: checkoutProduct.successUrl,
        cancel_url: checkoutProduct.cancelUrl,
        metadata: {
          tier: checkoutProduct.activationTier,
          product: tier,
          licenseTier: tier,
          type: "lifetime_dynamic",
          ifrDiscount: ifrDiscountApplied ? "true" : "false",
          ifrHolder: ifrDiscountApplied ? "true" : "false",
          ifrProofDigest,
          ifrProofVersion: ifrDiscountApplied ? IFR_PROOF_VERSION : "",
          originalPrice: String(price),
          checkoutPrice: String(checkoutPrice),
          discountPercent: ifrDiscountApplied ? "50" : "0"
        }
        // No payment_method_types: available methods stay provider-configured.
      });
      res.json({ url: session.url, sessionId: session.id, price: checkoutPrice, originalPrice: price, ifrDiscountApplied, ifrHolder: ifrDiscountApplied, ifrBalanceAmount });
    } catch (err) {
      console.error("[LICENSES] Checkout session failed:", (err && err.type) || "checkout_error");
      res.status(500).json({ error: "checkout_unavailable" });
    }
  });
}

module.exports = {
  setupIfrCheckoutRoutes,
  createCheckoutRateLimiter,
  createChallengeContract,
  buildChallengeMessage,
  buildProofDigest,
  isChallengeContractIntact,
  normalizeWalletAddress,
  IFR_PROOF_DOMAIN,
  IFR_PROOF_URI,
  IFR_PROOF_CHAIN_ID,
  IFR_PROOF_VERSION,
  IFR_PROOF_PURPOSE,
  IFR_PROOF_TTL_MS,
};
