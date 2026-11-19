"use strict";

/**
 * Deterministic tests for the IFR holder eligibility routes:
 *   POST /vlabs/ifr-eligibility/challenge
 *   POST /vlabs/ifr-eligibility
 *
 * No real RPC, checkout, network or secrets: the express app listens on an ephemeral
 * localhost port, the balance reader is an injected fake, wallet signatures use fixed
 * test keys, and every configuration value is a synthetic fixture.
 */

const assert = require("assert");
const crypto = require("crypto");
const express = require("express");
const { ethers } = require("ethers");

const {
  setupIfrEligibilityRoutes,
  eligibilityConfig,
  ELIGIBILITY_LIFETIME_S,
} = require("../payments/ifr_eligibility");
const { createChallengeContract } = require("../payments/ifr_checkout");
const { PRODUCTS } = require("../payments/vlabs_fulfillment");
const { readConfiguredTokenBalance } = require("../services/ifr");

const NOW_0 = 1_800_000_000_000;
const ORIGIN = "https://shop-entry.example.test";
const TOKEN = ethers.getAddress("0x" + "ab".repeat(20));
const SECRET = "fixture-eligibility-secret-".padEnd(48, "x");
const PRODUCT_ID = "stealthx-securecall-pro-lifetime";
const holderWallet = new ethers.Wallet("0x" + "11".repeat(32));
const otherWallet = new ethers.Wallet("0x" + "22".repeat(32));

const ENV = Object.freeze({
  VLABS_IFR_ELIGIBILITY_ENABLED: "true",
  VLABS_IFR_ELIGIBILITY_ORIGIN: ORIGIN,
  VLABS_IFR_ELIGIBILITY_CHAIN_ID: "1",
  VLABS_IFR_ELIGIBILITY_TOKEN_ADDRESS: TOKEN,
  VLABS_IFR_ELIGIBILITY_RPC_URLS: "https://rpc-a.example.test,https://rpc-b.example.test",
  VLABS_IFR_ELIGIBILITY_KEY_ID: "sx-ifr-fixture-1",
  VLABS_IFR_ELIGIBILITY_SECRET: SECRET,
});

function buildServer(env) {
  const challengeStore = new Map();
  const state = { nowMs: NOW_0, balanceResult: { success: true, balance: 1733n * 10n ** 9n }, reads: [] };
  const app = express();
  app.use(express.json());
  setupIfrEligibilityRoutes(app, {
    env,
    challengeStore,
    readHolderBalance: async (input) => {
      state.reads.push(input);
      if (state.balanceResult instanceof Error) throw state.balanceResult;
      return state.balanceResult;
    },
    rateLimit: (_req, _res, next) => next(),
    now: () => state.nowMs,
  });
  return { app, challengeStore, state };
}

async function listen(app) {
  const server = await new Promise((resolve) => { const s = app.listen(0, "127.0.0.1", () => resolve(s)); });
  const port = server.address().port;
  const post = async (routePath, body, origin = ORIGIN) => {
    const response = await fetch(`http://127.0.0.1:${port}${routePath}`, {
      method: "POST",
      headers: { "Content-Type": "application/json", ...(origin ? { Origin: origin } : {}) },
      body: JSON.stringify(body),
    });
    return { status: response.status, body: await response.json() };
  };
  return { server, post };
}

// Independent recomputation of the central verifier's expectation.
function verifyEnvelopeIndependently(envelope) {
  const raw = Buffer.from(envelope.payload, "base64url");
  const key = crypto.createHmac("sha256", SECRET).update("vlabs.stealthx-ifr-eligibility.v1").digest("hex");
  const expected = crypto.createHmac("sha256", key).update(`${envelope.timestamp}.`).update(raw).digest("hex");
  assert.strictEqual(envelope.signature, expected, "envelope signature matches the shared HMAC envelope");
  return JSON.parse(raw.toString("utf8"));
}

async function issue(post, wallet = holderWallet, productId = PRODUCT_ID) {
  const challenge = await post("/vlabs/ifr-eligibility/challenge", { productId, walletAddress: wallet.address });
  assert.strictEqual(challenge.status, 200, JSON.stringify(challenge.body));
  return challenge.body;
}

async function main() {
  let passed = 0;
  const check = (label) => { passed++; console.log("  ok -", label); };

  // --- Closed without complete approved configuration ------------------------
  for (const missing of [null, ...Object.keys(ENV)]) {
    const env = { ...ENV };
    if (missing) delete env[missing];
    if (!missing) env.VLABS_IFR_ELIGIBILITY_ENABLED = "false";
    assert.strictEqual(eligibilityConfig(env), null, String(missing));
    const { app, challengeStore, state } = buildServer(env);
    const { server, post } = await listen(app);
    const challenge = await post("/vlabs/ifr-eligibility/challenge", { productId: PRODUCT_ID, walletAddress: holderWallet.address });
    const eligibility = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: "a", signature: "0x" });
    assert.strictEqual(challenge.status, 503);
    assert.deepStrictEqual(challenge.body, { error: "ifr_eligibility_unavailable" });
    assert.strictEqual(eligibility.status, 503);
    assert.strictEqual(challengeStore.size, 0);
    assert.strictEqual(state.reads.length, 0);
    await new Promise((resolve) => server.close(resolve));
  }
  for (const [name, value] of [["VLABS_IFR_ELIGIBILITY_ORIGIN", "http://shop-entry.example.test"],
    ["VLABS_IFR_ELIGIBILITY_ORIGIN", ORIGIN + "/ifr.html"], ["VLABS_IFR_ELIGIBILITY_CHAIN_ID", "0"],
    ["VLABS_IFR_ELIGIBILITY_TOKEN_ADDRESS", "0x1234"], ["VLABS_IFR_ELIGIBILITY_RPC_URLS", "http://rpc.example.test"],
    ["VLABS_IFR_ELIGIBILITY_SECRET", "short"], ["VLABS_IFR_ELIGIBILITY_KEY_ID", "bad key"]]) {
    assert.strictEqual(eligibilityConfig({ ...ENV, [name]: value }), null, name);
  }
  check("closed (503) unless every approved input is configured; no invented defaults");

  const { app, challengeStore, state } = buildServer({ ...ENV });
  const { server, post } = await listen(app);
  try {
    // --- Origin and product binding ------------------------------------------
    assert.strictEqual((await post("/vlabs/ifr-eligibility/challenge", { productId: PRODUCT_ID, walletAddress: holderWallet.address }, "https://evil.example.test")).status, 403);
    assert.strictEqual((await post("/vlabs/ifr-eligibility/challenge", { productId: PRODUCT_ID, walletAddress: holderWallet.address }, null)).status, 403);
    assert.strictEqual((await post("/vlabs/ifr-eligibility/challenge", { productId: "stealthx-custom-id-standard", walletAddress: holderWallet.address })).status, 400);
    assert.strictEqual((await post("/vlabs/ifr-eligibility/challenge", { productId: "pro_lifetime", walletAddress: holderWallet.address })).status, 400);
    assert.strictEqual((await post("/vlabs/ifr-eligibility/challenge", { productId: PRODUCT_ID, walletAddress: "0x123" })).status, 400);
    assert.strictEqual(challengeStore.size, 0);
    check("exact origin, immutable lifetime offer and valid wallet are required");

    // --- Fresh valid proof -> signed eligibility -------------------------------
    const challenge = await issue(post);
    assert.ok(challenge.message.startsWith("shop-entry.example.test wants you to sign in with your Ethereum account:\n" + holderWallet.address));
    for (const bound of [`URI: ${ORIGIN}`, "Chain ID: 1", `Nonce: ${challenge.nonce}`, PRODUCTS[PRODUCT_ID].offerVersion, TOKEN]) {
      assert.ok(challenge.message.includes(bound), bound);
    }
    assert.ok(!/50%|discount|price/i.test(challenge.message), "the wallet message makes no price claim");
    const signature = await holderWallet.signMessage(challenge.message);
    const result = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: challenge.nonce, signature });
    assert.strictEqual(result.status, 200, JSON.stringify(result.body));
    const envelope = result.body.eligibility;
    assert.deepStrictEqual(Object.keys(envelope).sort(), ["keyId", "payload", "signature", "source", "timestamp"]);
    assert.strictEqual(envelope.source, "stealthx");
    assert.strictEqual(envelope.keyId, ENV.VLABS_IFR_ELIGIBILITY_KEY_ID);
    const payload = verifyEnvelopeIndependently(envelope);
    assert.strictEqual(payload.schema, "vlabs.stealthx.ifr_eligibility.v1");
    assert.match(payload.requestId, /^sxr_[A-Za-z0-9_-]{32}$/);
    assert.match(payload.proofRef, /^[a-f0-9]{64}$/);
    assert.strictEqual(payload.productId, PRODUCT_ID);
    assert.strictEqual(payload.offerVersion, PRODUCTS[PRODUCT_ID].offerVersion);
    assert.strictEqual(payload.catalogVersion, PRODUCTS[PRODUCT_ID].catalogVersion);
    assert.strictEqual(payload.chainId, 1);
    assert.strictEqual(payload.tokenAddress, TOKEN);
    assert.strictEqual(String(payload.issuedAt), envelope.timestamp);
    assert.strictEqual(payload.expiresAt - payload.issuedAt, ELIGIBILITY_LIFETIME_S);
    const serialized = JSON.stringify(result.body).toLowerCase() + Buffer.from(envelope.payload, "base64url").toString("utf8").toLowerCase();
    assert.ok(!serialized.includes(holderWallet.address.slice(2).toLowerCase()), "no wallet address leaves the server");
    assert.ok(!serialized.includes(signature.slice(2, 40).toLowerCase()), "no wallet signature leaves the server");
    assert.ok(!serialized.includes("1733"), "no balance leaves the server");
    assert.deepStrictEqual(state.reads.at(-1), { walletAddress: holderWallet.address, chainId: 1, tokenAddress: TOKEN,
      rpcUrls: ["https://rpc-a.example.test", "https://rpc-b.example.test"] });
    check("fresh signed proof over a server nonce yields a signed eligibility without wallet, signature or balance");

    // --- Single use --------------------------------------------------------------
    const replay = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: challenge.nonce, signature });
    assert.strictEqual(replay.status, 403);
    assert.strictEqual(replay.body.error, "invalid_wallet_challenge");
    check("the nonce is single use");

    // --- Forged, expired, mismatched -------------------------------------------
    const readsBefore = state.reads.length;
    const forgedChallenge = await issue(post);
    const forged = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address,
      nonce: forgedChallenge.nonce, signature: await otherWallet.signMessage(forgedChallenge.message) });
    assert.strictEqual(forged.status, 403);
    assert.strictEqual(forged.body.error, "wallet_signature_invalid");
    assert.strictEqual(challengeStore.has(forgedChallenge.nonce), false, "a failed proof burns the nonce");
    const typedOnly = await issue(post);
    assert.strictEqual((await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: typedOnly.nonce })).status, 400);
    const expiring = await issue(post);
    const expiringSignature = await holderWallet.signMessage(expiring.message);
    state.nowMs += 5 * 60 * 1000;
    const expired = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: expiring.nonce, signature: expiringSignature });
    assert.strictEqual(expired.status, 403);
    assert.strictEqual(expired.body.error, "wallet_challenge_expired");
    state.nowMs = NOW_0;
    const mismatch = await issue(post);
    const mismatchSignature = await holderWallet.signMessage(mismatch.message);
    const wrongProduct = await post("/vlabs/ifr-eligibility", { productId: "stealthx-securecall-premium-lifetime",
      walletAddress: holderWallet.address, nonce: mismatch.nonce, signature: mismatchSignature });
    assert.strictEqual(wrongProduct.status, 403);
    assert.strictEqual(wrongProduct.body.error, "wallet_challenge_mismatch");
    const otherWalletChallenge = await issue(post);
    const wrongWallet = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: otherWallet.address,
      nonce: otherWalletChallenge.nonce, signature: await otherWallet.signMessage(otherWalletChallenge.message) });
    assert.strictEqual(wrongWallet.status, 403);
    assert.strictEqual(state.reads.length, readsBefore, "no provider read for forged, expired or mismatched proofs");
    check("forged, signature-less, expired, product- and wallet-mismatched proofs are rejected before any provider read");

    // --- Legacy challenge cannot be used here ------------------------------------
    const legacy = createChallengeContract({ tier: "pro_lifetime", walletAddress: holderWallet.address, nonce: "c".repeat(64), now: NOW_0 });
    challengeStore.set(legacy.nonce, legacy);
    const legacyResult = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address,
      nonce: legacy.nonce, signature: await holderWallet.signMessage(legacy.message) });
    assert.strictEqual(legacyResult.status, 403);
    assert.strictEqual(legacyResult.body.error, "invalid_wallet_challenge");
    check("a retired-flow challenge is purpose-separated and rejected");

    // --- Configuration drift between challenge and proof --------------------------
    const drift = await issue(post);
    const driftSignature = await holderWallet.signMessage(drift.message);
    const env = { ...ENV, VLABS_IFR_ELIGIBILITY_TOKEN_ADDRESS: ethers.getAddress("0x" + "cd".repeat(20)) };
    const drifted = buildServer(env);
    drifted.challengeStore.set(drift.nonce, challengeStore.get(drift.nonce));
    const { server: driftServer, post: driftPost } = await listen(drifted.app);
    const driftResult = await driftPost("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address, nonce: drift.nonce, signature: driftSignature });
    await new Promise((resolve) => driftServer.close(resolve));
    assert.strictEqual(driftResult.status, 403);
    assert.strictEqual(drifted.state.reads.length, 0);
    check("a token/chain configuration change invalidates outstanding challenges");

    // --- Ownership: zero balance and provider failure -------------------------------
    state.balanceResult = { success: true, balance: 0n };
    const zero = await issue(post);
    const zeroResult = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address,
      nonce: zero.nonce, signature: await holderWallet.signMessage(zero.message) });
    assert.strictEqual(zeroResult.status, 403);
    assert.deepStrictEqual(zeroResult.body, { error: "ifr_not_eligible" });
    for (const failure of [{ success: false, error: "all_rpc_failed" }, new Error("rpc down"), { success: true, balance: "5" }]) {
      state.balanceResult = failure;
      const down = await issue(post);
      const downResult = await post("/vlabs/ifr-eligibility", { productId: PRODUCT_ID, walletAddress: holderWallet.address,
        nonce: down.nonce, signature: await holderWallet.signMessage(down.message) });
      assert.strictEqual(downResult.status, 503);
      assert.deepStrictEqual(downResult.body, { error: "ifr_provider_unavailable" });
    }
    check("zero balance is not eligible; provider failures fail closed without eligibility");
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }

  // --- Configured read-only provider: approved chain only, no default endpoints ---
  const encoded = ethers.AbiCoder.defaultAbiCoder().encode(["uint256"], [7n]);
  const fakeProvider = (chainHex, destroyed) => () => ({
    send: async (method) => { assert.strictEqual(method, "eth_chainId"); return chainHex; },
    call: async () => encoded,
    destroy: () => { destroyed.count++; },
  });
  const warn = console.warn;
  console.warn = () => undefined;
  try {
    const destroyed = { count: 0 };
    assert.deepStrictEqual(await readConfiguredTokenBalance({ walletAddress: holderWallet.address, chainId: 1, tokenAddress: TOKEN, rpcUrls: [] }),
      { success: false, error: "eth_unavailable" });
    const ok = await readConfiguredTokenBalance({ walletAddress: holderWallet.address, chainId: 1, tokenAddress: TOKEN,
      rpcUrls: ["https://rpc-a.example.test"], createProvider: fakeProvider("0x1", destroyed) });
    assert.deepStrictEqual(ok, { success: true, balance: 7n });
    const wrongChain = await readConfiguredTokenBalance({ walletAddress: holderWallet.address, chainId: 1, tokenAddress: TOKEN,
      rpcUrls: ["https://rpc-a.example.test", "https://rpc-b.example.test"], createProvider: fakeProvider("0x89", destroyed) });
    assert.deepStrictEqual(wrongChain, { success: false, error: "all_rpc_failed" });
    assert.strictEqual(destroyed.count, 3, "every provider is released");
  } finally {
    console.warn = warn;
  }
  check("the read-only provider requires the approved chain id and has no default endpoints");

  console.log(`ifr_eligibility: ${passed} checks passed`);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
