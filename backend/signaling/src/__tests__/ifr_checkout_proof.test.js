"use strict";

/**
 * Deterministic HTTP-level tests for the IFR browser checkout proof:
 *   POST /stripe/ifr-discount-challenge
 *   POST /stripe/create-dynamic-checkout
 *
 * No real RPC, Stripe, network or secrets: express apps are bound to an
 * ephemeral localhost port, the IFR balance lookup and the Stripe client are
 * injected fakes, and signatures are produced offline with fixed test keys.
 */

const assert = require("assert");
const crypto = require("crypto");
const express = require("express");
const { ethers } = require("ethers");

const {
  setupIfrCheckoutRoutes,
  createChallengeContract,
  buildChallengeMessage,
  buildProofDigest,
  isChallengeContractIntact,
  IFR_PROOF_TTL_MS,
} = require("../payments/ifr_checkout");
const { getCheckoutProduct } = require("../services/ifr_checkout_catalog");

const NOW_0 = 1_800_000_000_000;
const holderWallet = new ethers.Wallet("0x" + "11".repeat(32));
const otherWallet = new ethers.Wallet("0x" + "22".repeat(32));

function buildTestServer(env) {
  const challengeStore = new Map();
  const calls = { balance: [], stripe: [] };
  const state = {
    nowMs: NOW_0,
    ifrResult: { success: true, holder: true, balanceAmount: "1733" },
    stripeError: null,
  };
  const app = express();
  app.use(express.json());
  setupIfrCheckoutRoutes(app, {
    env,
    challengeStore,
    licenses: {
      LICENSES: {
        pro_lifetime: { name: "SecureCall Pro Lifetime" },
        premium_lifetime: { name: "SecureCall Premium Lifetime" },
      },
      getCurrentPrice: () => 1500,
    },
    getCheckoutProduct,
    verifyIfrHolding: async (address) => {
      calls.balance.push(address);
      return state.ifrResult;
    },
    createStripe: () => ({
      checkout: {
        sessions: {
          create: async (params) => {
            if (state.stripeError) throw state.stripeError;
            calls.stripe.push(params);
            return { id: "cs_test_ifr_proof", url: "https://checkout.stripe.com/c/pay/cs_test_ifr_proof" };
          },
        },
      },
    }),
    checkoutRateLimit: (_req, _res, next) => next(),
    now: () => state.nowMs,
  });
  return { app, challengeStore, calls, state };
}

async function listen(app) {
  const server = await new Promise((resolve) => {
    const s = app.listen(0, "127.0.0.1", () => resolve(s));
  });
  const port = server.address().port;
  const post = async (routePath, body) => {
    const response = await fetch(`http://127.0.0.1:${port}${routePath}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    return { status: response.status, body: await response.json() };
  };
  return { server, post };
}

// Independent recomputation of the proof digest (never via the module under
// test), so a wrong digest construction cannot pass both sides by accident.
function expectedProofDigest(message, signature) {
  return crypto.createHash("sha256")
    .update("stripe_ifr_discount\n" + message + "\n" + signature, "utf8")
    .digest("hex");
}

async function main() {
  // --- Gate closed: both endpoints stay default-closed (410) ---------------
  {
    const { app, challengeStore, calls } = buildTestServer({});
    const { server, post } = await listen(app);
    const challenge = await post("/stripe/ifr-discount-challenge", { tier: "pro_lifetime", walletAddress: holderWallet.address });
    assert.strictEqual(challenge.status, 410);
    assert.deepStrictEqual(challenge.body, { error: "checkout_moved_to_vlabs" });
    const checkout = await post("/stripe/create-dynamic-checkout", { tier: "pro_lifetime", ifrDiscount: true });
    assert.strictEqual(checkout.status, 410);
    assert.deepStrictEqual(checkout.body, { error: "checkout_moved_to_vlabs" });
    assert.strictEqual(challengeStore.size, 0, "closed gate issues no challenge");
    assert.strictEqual(calls.balance.length, 0, "closed gate never checks balances");
    assert.strictEqual(calls.stripe.length, 0, "closed gate never calls Stripe");
    await new Promise((resolve) => server.close(resolve));
  }

  // --- Enabled fixture -------------------------------------------------------
  const { app, challengeStore, calls, state } = buildTestServer({
    LEGACY_STRIPE_CHECKOUT_ENABLED: "true",
    STRIPE_SECRET_KEY: "sk_test_fixture_not_a_secret",
  });
  const { server, post } = await listen(app);

  const issueChallenge = async (tier, walletAddress) => {
    const result = await post("/stripe/ifr-discount-challenge", { tier, walletAddress });
    assert.strictEqual(result.status, 200, "challenge issuance succeeds for " + tier);
    return result.body;
  };
  const sign = (wallet, message) => wallet.signMessage(message);

  // --- Invalid tier / wallet on both endpoints -------------------------------
  {
    assert.strictEqual((await post("/stripe/ifr-discount-challenge", { tier: "stealthx_suite_lifetime", walletAddress: holderWallet.address })).status, 400);
    assert.deepStrictEqual(
      (await post("/stripe/ifr-discount-challenge", { tier: "bogus", walletAddress: holderWallet.address })).body,
      { error: "invalid_tier" }
    );
    for (const badWallet of ["", "0x123", "notanaddress", holderWallet.address.slice(2)]) {
      assert.deepStrictEqual(
        (await post("/stripe/ifr-discount-challenge", { tier: "pro_lifetime", walletAddress: badWallet })).body,
        { error: "invalid_wallet" },
        "malformed wallet rejected: " + badWallet
      );
    }
    const mixedCaseBadChecksum = "0x" + holderWallet.address.slice(2).split("").map((c, i) => (i % 2 ? c.toUpperCase() : c.toLowerCase())).join("");
    if (mixedCaseBadChecksum !== holderWallet.address) {
      assert.deepStrictEqual(
        (await post("/stripe/ifr-discount-challenge", { tier: "pro_lifetime", walletAddress: mixedCaseBadChecksum })).body,
        { error: "invalid_wallet" },
        "mixed-case address with invalid checksum rejected"
      );
    }
    assert.deepStrictEqual(
      (await post("/stripe/create-dynamic-checkout", { tier: "stealthx_suite_lifetime" })).body,
      { error: "invalid_tier" },
      "suite stays excluded from web checkout"
    );
    assert.strictEqual((await post("/stripe/create-dynamic-checkout", { tier: "bogus" })).status, 400);
    assert.deepStrictEqual(
      (await post("/stripe/create-dynamic-checkout")).body,
      { error: "invalid_tier" },
      "an empty checkout body fails closed without throwing"
    );
    assert.deepStrictEqual(
      (await post("/stripe/create-dynamic-checkout", { tier: "pro_lifetime", ifrDiscount: true, walletAddress: "0x123", walletSignature: "0x", walletNonce: "ab" })).body,
      { error: "invalid_wallet" }
    );
    assert.strictEqual(challengeStore.size, 0, "rejected requests store no challenge");
  }

  // --- Challenge message contract ---------------------------------------------
  {
    const before = state.nowMs;
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    assert.deepStrictEqual(Object.keys(challenge).sort(), ["message", "nonce"], "challenge response carries only nonce + message");
    assert.match(challenge.nonce, /^[0-9a-f]{64}$/, "nonce is 32 random bytes hex");
    const stored = challengeStore.get(challenge.nonce);
    assert.ok(stored, "contract is stored under its nonce");
    assert.ok(Object.isFrozen(stored), "stored contract is immutable");
    assert.strictEqual(stored.domain, "stealthx.tech");
    assert.strictEqual(stored.uri, "https://stealthx.tech/ifr.html");
    assert.strictEqual(stored.chainId, 1, "bound to Ethereum Mainnet");
    assert.strictEqual(stored.version, "1");
    assert.strictEqual(stored.purpose, "stripe_ifr_discount");
    assert.strictEqual(stored.tier, "pro_lifetime");
    assert.strictEqual(stored.walletAddress, holderWallet.address, "wallet is stored EIP-55 normalized");
    assert.strictEqual(stored.createdAt, before, "createdAt is the integer server clock reading");
    assert.strictEqual(Date.parse(stored.issuedAt), before, "issuedAt matches the server clock");
    assert.strictEqual(Date.parse(stored.issuedAt), stored.createdAt, "issuedAt is exactly createdAt");
    assert.strictEqual(Date.parse(stored.expirationTime) - Date.parse(stored.issuedAt), IFR_PROOF_TTL_MS, "five-minute lifetime");
    assert.strictEqual(IFR_PROOF_TTL_MS, 5 * 60 * 1000);
    const expectedMessage =
      "stealthx.tech wants you to sign in with your Ethereum account:\n" +
      holderWallet.address + "\n" +
      "\n" +
      "StealthX IFR holder discount for product pro_lifetime (50% Stripe checkout). Sign this message only to prove wallet ownership; it cannot move tokens or grant spending permission.\n" +
      "\n" +
      "URI: https://stealthx.tech/ifr.html\n" +
      "Version: 1\n" +
      "Chain ID: 1\n" +
      "Nonce: " + challenge.nonce + "\n" +
      "Issued At: " + stored.issuedAt + "\n" +
      "Expiration Time: " + stored.expirationTime;
    assert.strictEqual(challenge.message, expectedMessage, "canonical EIP-4361-shaped message");
    assert.ok(isChallengeContractIntact(stored), "fresh contract passes its own integrity check");

    // A second challenge for the same wallet gets an independent nonce.
    const second = await issueChallenge("pro_lifetime", holderWallet.address);
    assert.notStrictEqual(second.nonce, challenge.nonce, "nonces are cryptographically random");
    challengeStore.clear();
  }

  // --- Missing signature: a typed wallet address alone never qualifies --------
  {
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    for (const body of [
      { tier: "pro_lifetime", ifrDiscount: true, walletAddress: holderWallet.address, walletNonce: challenge.nonce },
      { tier: "pro_lifetime", ifrDiscount: true, walletAddress: holderWallet.address, walletSignature: await sign(holderWallet, challenge.message) },
      { tier: "pro_lifetime", ifrDiscount: true, walletAddress: holderWallet.address },
    ]) {
      const result = await post("/stripe/create-dynamic-checkout", body);
      assert.strictEqual(result.status, 400);
      assert.deepStrictEqual(result.body, { error: "wallet_signature_required" });
    }
    assert.ok(challengeStore.has(challenge.nonce), "incomplete proof does not consume the challenge");
    assert.strictEqual(calls.balance.length, 0, "no balance lookup without a complete proof");
    assert.strictEqual(calls.stripe.length, 0, "no Stripe call without a complete proof");
    challengeStore.clear();
  }

  // --- Unknown nonce -----------------------------------------------------------
  {
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: "ab".repeat(32),
      walletSignature: await sign(holderWallet, "unknown nonce"),
    });
    assert.strictEqual(result.status, 403);
    assert.deepStrictEqual(result.body, { error: "invalid_wallet_challenge" });
    assert.strictEqual(calls.balance.length, 0);
    assert.strictEqual(calls.stripe.length, 0);
  }

  // --- Nonce-key drift: stored nonce must equal the lookup key -----------------
  {
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const foreignKey = "ef".repeat(32);
    challengeStore.set(foreignKey, challengeStore.get(challenge.nonce));
    challengeStore.delete(challenge.nonce);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: foreignKey,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(result.status, 403);
    assert.deepStrictEqual(result.body, { error: "invalid_wallet_challenge" }, "intact contract under a foreign key rejects");
    assert.strictEqual(calls.balance.length, 0, "nonce-key drift never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0, "nonce-key drift never reaches Stripe");
    challengeStore.clear();
  }
  // --- Expired nonce: rejects one ms past AND exactly at expirationTime --------
  {
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    state.nowMs += IFR_PROOF_TTL_MS + 1; // one ms past expirationTime
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(result.status, 403);
    assert.deepStrictEqual(result.body, { error: "wallet_challenge_expired" });
    assert.strictEqual(calls.balance.length, 0, "stale proof never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0, "stale proof never reaches Stripe");
    assert.ok(!challengeStore.has(challenge.nonce), "stale nonce is consumed");
    state.nowMs = NOW_0;

    // Exact boundary: now == expirationTime is already expired.
    const boundaryChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
    state.nowMs += IFR_PROOF_TTL_MS; // exactly at expirationTime
    const boundaryResult = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: boundaryChallenge.nonce,
      walletSignature: await sign(holderWallet, boundaryChallenge.message),
    });
    assert.strictEqual(boundaryResult.status, 403);
    assert.deepStrictEqual(boundaryResult.body, { error: "wallet_challenge_expired" }, "proof expires exactly at expirationTime");
    assert.strictEqual(calls.balance.length, 0, "boundary proof never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0, "boundary proof never reaches Stripe");
    assert.ok(!challengeStore.has(boundaryChallenge.nonce), "boundary nonce is consumed");
    state.nowMs = NOW_0;

    // One ms before the boundary the same proof still verifies end-to-end.
    const freshChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
    state.nowMs += IFR_PROOF_TTL_MS - 1; // one ms before expirationTime
    const freshResult = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: freshChallenge.nonce,
      walletSignature: await sign(holderWallet, freshChallenge.message),
    });
    assert.strictEqual(freshResult.status, 200, "proof is valid one ms before expirationTime");
    state.nowMs = NOW_0;
    calls.balance.length = 0;
    calls.stripe.length = 0;
  }

  // --- Wallet / tier / contract mismatch ---------------------------------------
  {
    // tier mismatch: proof for pro_lifetime presented for premium_lifetime
    const tierChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const tierResult = await post("/stripe/create-dynamic-checkout", {
      tier: "premium_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: tierChallenge.nonce,
      walletSignature: await sign(holderWallet, tierChallenge.message),
    });
    assert.strictEqual(tierResult.status, 403);
    assert.deepStrictEqual(tierResult.body, { error: "wallet_challenge_mismatch" });
    assert.ok(!challengeStore.has(tierChallenge.nonce), "mismatched nonce is consumed");

    // wallet mismatch: proof bound to the holder presented with another wallet
    const walletChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const walletResult = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: otherWallet.address,
      walletNonce: walletChallenge.nonce,
      walletSignature: await sign(otherWallet, walletChallenge.message),
    });
    assert.strictEqual(walletResult.status, 403);
    assert.deepStrictEqual(walletResult.body, { error: "wallet_challenge_mismatch" });

    // contract drift: any tampering with the stored domain/URI/chain fails closed
    for (const drift of [
      { domain: "evil.example" },
      { uri: "https://evil.example/ifr.html" },
      { chainId: 137 },
      { version: "2" },
      { purpose: "other_purpose" },
    ]) {
      const driftChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
      challengeStore.set(driftChallenge.nonce, { ...challengeStore.get(driftChallenge.nonce), ...drift });
      const driftResult = await post("/stripe/create-dynamic-checkout", {
        tier: "pro_lifetime",
        ifrDiscount: true,
        walletAddress: holderWallet.address,
        walletNonce: driftChallenge.nonce,
        walletSignature: await sign(holderWallet, driftChallenge.message),
      });
      assert.strictEqual(driftResult.status, 403, "drift rejected: " + JSON.stringify(drift));
      assert.deepStrictEqual(driftResult.body, { error: "invalid_wallet_challenge" }, "drift error: " + JSON.stringify(drift));
    }

    // message swap: replacing the stored message with a self-made one fails,
    // even when the attacker holds a valid signature over the forged message
    const forged = createChallengeContract({
      tier: "pro_lifetime",
      walletAddress: otherWallet.address,
      nonce: "cd".repeat(32),
      now: state.nowMs,
    });
    const swapChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
    challengeStore.set(swapChallenge.nonce, { ...challengeStore.get(swapChallenge.nonce), message: forged.message });
    const swapResult = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: otherWallet.address,
      walletNonce: swapChallenge.nonce,
      walletSignature: await sign(otherWallet, forged.message),
    });
    assert.strictEqual(swapResult.status, 403);
    assert.deepStrictEqual(swapResult.body, { error: "invalid_wallet_challenge" }, "message swap is caught by integrity check");

    assert.strictEqual(calls.balance.length, 0, "mismatch/drift never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0, "mismatch/drift never reaches Stripe");
    challengeStore.clear();
  }

  // --- Temporal drift: createdAt / issuedAt / TTL binding -----------------------
  {
    // createdAt is not part of the signed message, so only the explicit
    // integrity rules catch its drift.
    for (const drift of [
      { createdAt: "not-a-number" },
      { createdAt: state.nowMs + 0.5 },
      { createdAt: state.nowMs + 1000 },
    ]) {
      const driftChallenge = await issueChallenge("pro_lifetime", holderWallet.address);
      challengeStore.set(driftChallenge.nonce, { ...challengeStore.get(driftChallenge.nonce), ...drift });
      const driftResult = await post("/stripe/create-dynamic-checkout", {
        tier: "pro_lifetime",
        ifrDiscount: true,
        walletAddress: holderWallet.address,
        walletNonce: driftChallenge.nonce,
        walletSignature: await sign(holderWallet, driftChallenge.message),
      });
      assert.strictEqual(driftResult.status, 403, "createdAt drift rejected: " + JSON.stringify(drift));
      assert.deepStrictEqual(driftResult.body, { error: "invalid_wallet_challenge" }, "createdAt drift error: " + JSON.stringify(drift));
      challengeStore.clear();
    }

    // Self-consistent forgeries (valid signature over a canonically rebuilt
    // message) that violate only the temporal binding must still reject.
    for (const mutate of [
      // issuedAt shifted together with expirationTime: TTL holds, but
      // issuedAt no longer equals createdAt.
      (c) => {
        c.issuedAt = new Date(state.nowMs + 1000).toISOString();
        c.expirationTime = new Date(state.nowMs + 1000 + IFR_PROOF_TTL_MS).toISOString();
      },
      // expirationTime extended: issuedAt equals createdAt, but the lifetime
      // is no longer exactly IFR_PROOF_TTL_MS.
      (c) => {
        c.expirationTime = new Date(state.nowMs + 2 * IFR_PROOF_TTL_MS).toISOString();
      },
    ]) {
      const forged = { ...createChallengeContract({
        tier: "pro_lifetime",
        walletAddress: holderWallet.address,
        nonce: "ab".repeat(32),
        now: state.nowMs,
      }) };
      mutate(forged);
      forged.message = buildChallengeMessage(forged);
      assert.ok(!isChallengeContractIntact(forged), "temporal forgery fails the integrity check");
      challengeStore.set(forged.nonce, forged);
      const result = await post("/stripe/create-dynamic-checkout", {
        tier: "pro_lifetime",
        ifrDiscount: true,
        walletAddress: holderWallet.address,
        walletNonce: forged.nonce,
        walletSignature: await sign(holderWallet, forged.message),
      });
      assert.strictEqual(result.status, 403);
      assert.deepStrictEqual(result.body, { error: "invalid_wallet_challenge" }, "temporal forgery rejected despite valid signature");
      challengeStore.clear();
    }

    assert.strictEqual(calls.balance.length, 0, "temporal drift never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0, "temporal drift never reaches Stripe");
  }

  // --- Wrong signer --------------------------------------------------------------
  {
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(otherWallet, challenge.message),
    });
    assert.strictEqual(result.status, 403);
    assert.deepStrictEqual(result.body, { error: "wallet_signature_invalid" });
    assert.strictEqual(calls.balance.length, 0, "bad signature never reaches the balance check");
    assert.strictEqual(calls.stripe.length, 0);
  }

  // --- Zero IFR balance ------------------------------------------------------------
  {
    state.ifrResult = { success: false, holder: false, error: "insufficient", balanceAmount: "0" };
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(result.status, 403);
    assert.deepStrictEqual(result.body, { error: "ifr_not_eligible", balanceAmount: "0" });
    assert.deepStrictEqual(calls.balance, [holderWallet.address], "balance checked once for the normalized wallet");
    assert.strictEqual(calls.stripe.length, 0, "zero balance never reaches Stripe");
    calls.balance.length = 0;
    state.ifrResult = { success: true, holder: true, balanceAmount: "1733" };
  }

  // --- Successful positive-balance checkout: exact 50% server-side math ----------
  {
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const signature = await sign(holderWallet, challenge.message);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: signature,
    });
    assert.strictEqual(result.status, 200);
    assert.deepStrictEqual(result.body, {
      url: "https://checkout.stripe.com/c/pay/cs_test_ifr_proof",
      sessionId: "cs_test_ifr_proof",
      price: 750,
      originalPrice: 1500,
      ifrDiscountApplied: true,
      ifrHolder: true,
      ifrBalanceAmount: "1733",
    }, "holder pays exactly half of the 1500-cent server price; balance stays in the API response");
    assert.deepStrictEqual(calls.balance, [holderWallet.address]);
    assert.strictEqual(calls.stripe.length, 1);
    const params = calls.stripe[0];
    assert.strictEqual(params.mode, "payment");
    assert.strictEqual(params.line_items[0].quantity, 1);
    assert.deepStrictEqual(params.line_items[0].price_data, {
      currency: "eur",
      unit_amount: 750,
      product_data: { name: "SecureCall Pro Lifetime" },
    });
    assert.strictEqual(params.success_url, "https://stealthx.tech/payment-success.html?session_id={CHECKOUT_SESSION_ID}");
    assert.strictEqual(params.cancel_url, "https://stealthx.tech/#pricing");
    const digest = expectedProofDigest(challenge.message, signature);
    assert.deepStrictEqual(params.metadata, {
      tier: "pro",
      product: "pro_lifetime",
      licenseTier: "pro_lifetime",
      type: "lifetime_dynamic",
      ifrDiscount: "true",
      ifrHolder: "true",
      ifrProofDigest: digest,
      ifrProofVersion: "1",
      originalPrice: "1500",
      checkoutPrice: "750",
      discountPercent: "50",
    }, "metadata carries only digest/version/eligibility/price facts");
    assert.strictEqual(params.metadata.ifrProofDigest, buildProofDigest(challenge.message, signature), "digest matches the exported helper");
    assert.match(digest, /^[0-9a-f]{64}$/, "digest is one-way SHA-256 hex");
    assert.notStrictEqual(digest, expectedProofDigest(challenge.message, await sign(otherWallet, challenge.message)), "digest is bound to the exact signature");
    assert.ok(!Object.prototype.hasOwnProperty.call(params.metadata, "ifrWallet"), "no raw wallet metadata key");
    assert.ok(!Object.prototype.hasOwnProperty.call(params.metadata, "ifrBalanceAmount"), "no raw balance metadata key");
    for (const [key, value] of Object.entries(params.metadata)) {
      assert.ok(!String(value).toLowerCase().includes(holderWallet.address.toLowerCase()), "no raw wallet in metadata." + key);
      assert.ok(!String(value).includes("1733"), "no raw balance in metadata." + key);
    }
    assert.ok(!Object.prototype.hasOwnProperty.call(params, "payment_method_types"), "payment methods stay provider-configured");
    assert.ok(!challengeStore.has(challenge.nonce), "successful proof consumes the nonce");

    // Replay of the exact same proof fails closed.
    const replay = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(replay.status, 403);
    assert.deepStrictEqual(replay.body, { error: "invalid_wallet_challenge" }, "replayed nonce rejected");
    assert.strictEqual(calls.stripe.length, 1, "replay never reaches Stripe");
    calls.balance.length = 0;
    calls.stripe.length = 0;
  }

  // --- Lowercase typed address normalizes to the same bound wallet ---------------
  {
    const lowercase = holderWallet.address.toLowerCase();
    const challenge = await issueChallenge("pro_lifetime", lowercase);
    assert.strictEqual(challengeStore.get(challenge.nonce).walletAddress, holderWallet.address, "stored bound wallet is EIP-55 normalized");
    const signature = await sign(holderWallet, challenge.message);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: lowercase,
      walletNonce: challenge.nonce,
      walletSignature: signature,
    });
    assert.strictEqual(result.status, 200, "lowercase presentation of the same wallet verifies");
    assert.strictEqual(calls.stripe[0].metadata.ifrProofDigest, expectedProofDigest(challenge.message, signature), "digest binds the normalized wallet's canonical message");
    calls.balance.length = 0;
    calls.stripe.length = 0;
  }

  // --- Full-price path without discount request ------------------------------------
  {
    const result = await post("/stripe/create-dynamic-checkout", { tier: "pro_lifetime" });
    assert.strictEqual(result.status, 200);
    assert.deepStrictEqual(result.body, {
      url: "https://checkout.stripe.com/c/pay/cs_test_ifr_proof",
      sessionId: "cs_test_ifr_proof",
      price: 1500,
      originalPrice: 1500,
      ifrDiscountApplied: false,
      ifrHolder: false,
      ifrBalanceAmount: "",
    });
    assert.strictEqual(calls.stripe[0].line_items[0].price_data.unit_amount, 1500, "full price without proof");
    assert.deepStrictEqual(calls.stripe[0].metadata, {
      tier: "pro",
      product: "pro_lifetime",
      licenseTier: "pro_lifetime",
      type: "lifetime_dynamic",
      ifrDiscount: "false",
      ifrHolder: "false",
      ifrProofDigest: "",
      ifrProofVersion: "",
      originalPrice: "1500",
      checkoutPrice: "1500",
      discountPercent: "0",
    });
    assert.ok(!Object.prototype.hasOwnProperty.call(calls.stripe[0], "payment_method_types"), "payment methods stay provider-configured");
    assert.strictEqual(calls.balance.length, 0, "no balance lookup without a discount request");
    calls.stripe.length = 0;
  }

  // --- Stripe failure: generic 500, no entitlement/code ------------------------------
  {
    state.stripeError = new Error("stripe_down_with_internal_detail");
    const challenge = await issueChallenge("pro_lifetime", holderWallet.address);
    const result = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(result.status, 500);
    assert.deepStrictEqual(result.body, { error: "checkout_unavailable" }, "Stripe failure leaks no internal error detail");
    assert.strictEqual(calls.stripe.length, 0, "failed Stripe call records no session");
    assert.ok(!challengeStore.has(challenge.nonce), "failed checkout still consumes the proof");
    // This route owns no entitlement or activation-code store; the only path to a
    // code is the paid webhook. A failed session therefore leaves no entitlement:
    // replaying the burned nonce cannot reopen one either.
    const replay = await post("/stripe/create-dynamic-checkout", {
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: holderWallet.address,
      walletNonce: challenge.nonce,
      walletSignature: await sign(holderWallet, challenge.message),
    });
    assert.strictEqual(replay.status, 403);
    state.stripeError = null;
    calls.balance.length = 0;
  }

  await new Promise((resolve) => server.close(resolve));
  console.log("ifr_checkout_proof.test PASSED - EIP-4361 wallet proof enforced fail-closed on both endpoints");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
