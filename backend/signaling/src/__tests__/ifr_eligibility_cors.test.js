"use strict";

// Spawned server: the IFR eligibility routes are reachable from a browser only for the
// configured landing origin, through the existing global CORS middleware and the route
// origin check. Other origins get no CORS grant and a refusal. A configured origin
// outside the global allow-list keeps the path closed. The signing secret is read from
// a protected file by the existing bounded reader. Synthetic values only; no provider
// call is reached (the POST is refused before signature recovery).
// Run: node src/__tests__/ifr_eligibility_cors.test.js

const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const SIGNALING_ROOT = path.resolve(__dirname, "../..");
const LANDING_ORIGIN = "https://stealthx.tech";
const FOREIGN_ORIGIN = "https://attacker.example.test";
const PRODUCT_ID = "stealthx-securecall-pro-lifetime";
const WALLET = "0x" + "12".repeat(20);

function request(port, method, route, headers = {}, body = null) {
  return new Promise((resolve, reject) => {
    const payload = body === null ? null : JSON.stringify(body);
    const req = http.request({
      host: "127.0.0.1", port, path: route, method, timeout: 3000,
      headers: { ...headers, ...(payload ? { "Content-Type": "application/json", "Content-Length": Buffer.byteLength(payload) } : {}) },
    }, (response) => {
      let text = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { text += chunk; });
      response.on("end", () => {
        let json = null;
        try { json = JSON.parse(text); } catch (_e) { /* not json */ }
        resolve({ statusCode: response.statusCode, headers: response.headers, json });
      });
    });
    req.once("timeout", () => req.destroy(new Error("request timed out")));
    req.once("error", reject);
    if (payload) req.write(payload);
    req.end();
  });
}

async function startServer(extraEnv, secretMode = 0o600) {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-ifr-cors-"));
  const walletsFile = path.join(dataDir, "wallets.json");
  fs.writeFileSync(walletsFile, JSON.stringify({ wallets: [] }));
  const secretFile = path.join(dataDir, "ifr-eligibility.secret");
  fs.writeFileSync(secretFile, crypto.randomBytes(32).toString("hex") + "\n", { mode: 0o600 });
  fs.chmodSync(secretFile, secretMode);
  const output = { value: "" };
  const child = spawn(process.execPath, ["src/server.js"], {
    cwd: SIGNALING_ROOT,
    env: {
      HOME: process.env.HOME || "",
      PATH: process.env.PATH || "",
      NODE_ENV: "test",
      PORT: "0",
      DATA_DIR: dataDir,
      WALLETS_FILE: walletsFile,
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_RTDN_ENABLED: "false",
      LEGACY_STRIPE_CHECKOUT_ENABLED: "false",
      VLABS_IFR_ELIGIBILITY_ENABLED: "true",
      VLABS_IFR_ELIGIBILITY_ORIGIN: LANDING_ORIGIN,
      VLABS_IFR_ELIGIBILITY_CHAIN_ID: "1",
      VLABS_IFR_ELIGIBILITY_TOKEN_ADDRESS: "0x" + "ab".repeat(20),
      VLABS_IFR_ELIGIBILITY_RPC_URLS: "https://rpc.example.test",
      VLABS_IFR_ELIGIBILITY_KEY_ID: "sx-ifr-fixture-1",
      VLABS_IFR_ELIGIBILITY_SECRET_FILE: secretFile,
      ...extraEnv,
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  child.stdout.on("data", (chunk) => { output.value += chunk.toString(); });
  child.stderr.on("data", (chunk) => { output.value += chunk.toString(); });
  const deadline = Date.now() + 10000;
  let port = null;
  while (Date.now() < deadline && port === null) {
    const match = output.value.match(/Server running on port (\d+)/);
    if (match) port = Number(match[1]);
    else await new Promise((resolve) => setTimeout(resolve, 25));
  }
  if (port === null) throw new Error(`server did not start\n${output.value}`);
  return { child, port, dataDir };
}

async function stopServer({ child, dataDir }) {
  if (child.pid && child.exitCode === null) {
    const exited = new Promise((resolve) => child.once("exit", resolve));
    child.kill("SIGTERM");
    const stopped = await Promise.race([
      exited.then(() => true),
      new Promise((resolve) => setTimeout(() => resolve(false), 3000)),
    ]);
    if (!stopped && child.exitCode === null) child.kill("SIGKILL");
  }
  fs.rmSync(dataDir, { recursive: true, force: true });
}

const preflightHeaders = (origin) => ({
  Origin: origin,
  "Access-Control-Request-Method": "POST",
  "Access-Control-Request-Headers": "content-type",
});

(async () => {
  // Default global allow-list (ALLOWED_ORIGINS unset) contains the landing origin.
  const server = await startServer({});
  try {
    for (const route of ["/vlabs/ifr-eligibility/challenge", "/vlabs/ifr-eligibility"]) {
      const ok = await request(server.port, "OPTIONS", route, preflightHeaders(LANDING_ORIGIN));
      assert.strictEqual(ok.statusCode, 204, `${route} preflight`);
      assert.strictEqual(ok.headers["access-control-allow-origin"], LANDING_ORIGIN, `${route} preflight origin`);
      assert.match(ok.headers["access-control-allow-methods"], /\bPOST\b/);
      assert.match(ok.headers["access-control-allow-headers"], /Content-Type/i);

      const foreign = await request(server.port, "OPTIONS", route, preflightHeaders(FOREIGN_ORIGIN));
      assert.ok(!("access-control-allow-origin" in foreign.headers), `${route} foreign preflight gets no grant`);
    }

    const challenge = await request(server.port, "POST", "/vlabs/ifr-eligibility/challenge",
      { Origin: LANDING_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(challenge.statusCode, 200, JSON.stringify(challenge.json));
    assert.strictEqual(challenge.headers["access-control-allow-origin"], LANDING_ORIGIN);
    assert.match(challenge.json.nonce, /^[a-f0-9]{64}$/);
    assert.ok(challenge.json.message.includes(`URI: ${LANDING_ORIGIN}`), "challenge bound to the landing origin");

    const foreignChallenge = await request(server.port, "POST", "/vlabs/ifr-eligibility/challenge",
      { Origin: FOREIGN_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(foreignChallenge.statusCode, 403);
    assert.strictEqual(foreignChallenge.json.error, "origin_not_allowed");
    assert.ok(!("access-control-allow-origin" in foreignChallenge.headers));

    const noOrigin = await request(server.port, "POST", "/vlabs/ifr-eligibility/challenge",
      {}, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(noOrigin.statusCode, 403, "non-browser callers without the origin are refused");

    // Refused before signature recovery and before any provider call.
    const forged = await request(server.port, "POST", "/vlabs/ifr-eligibility",
      { Origin: LANDING_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET, nonce: "f".repeat(64), signature: "0x" + "1".repeat(130) });
    assert.strictEqual(forged.statusCode, 403);
    assert.strictEqual(forged.json.error, "invalid_wallet_challenge");
    assert.strictEqual(forged.headers["access-control-allow-origin"], LANDING_ORIGIN);

    const foreignPost = await request(server.port, "POST", "/vlabs/ifr-eligibility",
      { Origin: FOREIGN_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET, nonce: challenge.json.nonce, signature: "0x" + "1".repeat(130) });
    assert.strictEqual(foreignPost.statusCode, 403);
    assert.strictEqual(foreignPost.json.error, "origin_not_allowed");

    // Legacy IFR checkout routes stay retired.
    const legacy = await request(server.port, "POST", "/stripe/ifr-discount-challenge", { Origin: LANDING_ORIGIN }, { tier: "pro_lifetime", walletAddress: WALLET });
    assert.strictEqual(legacy.statusCode, 410);
  } finally {
    await stopServer(server);
  }

  // A configured origin outside the global allow-list keeps the path closed.
  const outside = await startServer({ ALLOWED_ORIGINS: "https://securechat.stealthx.tech" });
  try {
    const closed = await request(outside.port, "POST", "/vlabs/ifr-eligibility/challenge",
      { Origin: LANDING_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(closed.statusCode, 503);
    assert.strictEqual(closed.json.error, "ifr_eligibility_unavailable");
  } finally {
    await stopServer(outside);
  }

  // An unreadable/invalid secret file keeps the path closed (no raw secret fallback).
  const badSecret = await startServer({ VLABS_IFR_ELIGIBILITY_SECRET_FILE: "/nonexistent/ifr.secret" });
  try {
    const closed = await request(badSecret.port, "POST", "/vlabs/ifr-eligibility/challenge",
      { Origin: LANDING_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(closed.statusCode, 503);
  } finally {
    await stopServer(badSecret);
  }

  const exposed = await startServer({}, 0o644);
  try {
    const closed = await request(exposed.port, "POST", "/vlabs/ifr-eligibility/challenge",
      { Origin: LANDING_ORIGIN }, { productId: PRODUCT_ID, walletAddress: WALLET });
    assert.strictEqual(closed.statusCode, 503, "the new signing authority requires an owner-only file");
  } finally {
    await stopServer(exposed);
  }

  console.log("ifr_eligibility_cors.test.js: PASS");
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
