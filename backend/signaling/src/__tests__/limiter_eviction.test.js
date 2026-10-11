"use strict";

// STX-17 (spawned server): inviteRateLimits, checkoutRateLimits and
// siweChallenges must be bounded — stale-window prune, hard caps
// (fail-closed for HTTP limiters, oldest-first for challenges) — while the
// per-IP limits themselves stay unchanged.
// Run: node src/__tests__/limiter_eviction.test.js

const assert = require("assert");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const SIGNALING_ROOT = path.resolve(__dirname, "../..");
const WALLET = "0x00000000000000000000000000000000000000ab";

function post(port, route, ip, body) {
  return new Promise((resolve, reject) => {
    const request = http.request({
      host: "127.0.0.1",
      port,
      path: route,
      method: "POST",
      timeout: 2000,
      headers: { "content-type": "application/json", "x-forwarded-for": ip },
    }, (response) => {
      let data = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { data += chunk; });
      response.on("end", () => {
        let json = null;
        try { json = JSON.parse(data); } catch { /* keep null */ }
        resolve({ statusCode: response.statusCode, json });
      });
    });
    request.once("timeout", () => request.destroy(new Error("request timed out")));
    request.once("error", reject);
    request.end(JSON.stringify(body));
  });
}

async function waitForPort(child, output, lifecycle) {
  const deadline = Date.now() + 10000;
  while (Date.now() < deadline) {
    const match = output.value.match(/Server running on port (\d+)/);
    if (match) return Number(match[1]);
    if (lifecycle.error || child.exitCode !== null) {
      throw new Error(`server did not start: ${lifecycle.error?.message || child.exitCode}\n${output.value}`);
    }
    await new Promise((resolve) => setTimeout(resolve, 25));
  }
  throw new Error(`server did not report its port\n${output.value}`);
}

async function stopChild(child) {
  if (!child.pid || child.exitCode !== null) return;
  const exited = new Promise((resolve) => child.once("exit", resolve));
  child.kill("SIGTERM");
  const stopped = await Promise.race([
    exited.then(() => true),
    new Promise((resolve) => setTimeout(() => resolve(false), 3000)),
  ]);
  if (!stopped && child.exitCode === null) child.kill("SIGKILL");
}

(async () => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-limiter-eviction-"));
  const output = { value: "" };
  const lifecycle = { error: null };
  const child = spawn(process.execPath, ["src/server.js"], {
    cwd: SIGNALING_ROOT,
    env: {
      HOME: process.env.HOME || "",
      PATH: process.env.PATH || "",
      NODE_ENV: "test",
      PORT: "0",
      DATA_DIR: dataDir,
      WALLETS_FILE: path.join(dataDir, "wallets.json"),
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_RTDN_ENABLED: "false",
      LEGACY_STRIPE_CHECKOUT_ENABLED: "true",
      STRIPE_SECRET_KEY: "sk_test_synthetic_not_a_secret",
      TRUST_PROXY: "1",
      INVITE_RATE_LIMIT_MAX_KEYS: "3",
      CHECKOUT_RATE_LIMIT_MAX_KEYS: "4",
      SIWE_CHALLENGE_MAX: "2",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });
  fs.writeFileSync(path.join(dataDir, "wallets.json"), JSON.stringify({ wallets: [] }));
  child.once("error", (error) => { lifecycle.error = error; });
  child.stdout.on("data", (chunk) => { output.value += chunk.toString(); });
  child.stderr.on("data", (chunk) => { output.value += chunk.toString(); });

  try {
    const port = await waitForPort(child, output, lifecycle);

    // --- inviteRateLimits: per-IP limit unchanged (3 per 10 min) ---
    for (let i = 0; i < 3; i++) {
      assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.1", {})).statusCode, 400, output.value);
    }
    assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.1", {})).statusCode, 429, "invite per-IP limit unchanged");

    // Fill the map to the cap (A,B,C); a 4th new IP is still served (oldest evicted).
    assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.2", {})).statusCode, 400);
    assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.3", {})).statusCode, 400);
    assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.4", {})).statusCode, 400, "new IP served at capacity (oldest evicted, no lockout)");
    assert.strictEqual((await post(port, "/invite/accepted", "198.51.100.2", {})).statusCode, 400, "tracked IP unaffected by capacity");

    // --- checkoutRateLimits + siweChallenges caps ---
    const challenge = (ip) => post(port, "/stripe/ifr-discount-challenge", ip, { tier: "pro_lifetime", walletAddress: WALLET });
    const nonces = [];
    for (const ip of ["203.0.113.1", "203.0.113.2", "203.0.113.3", "203.0.113.4"]) {
      const res = await challenge(ip);
      assert.strictEqual(res.statusCode, 200, output.value);
      nonces.push(res.json.nonce);
    }
    assert.strictEqual((await challenge("203.0.113.5")).statusCode, 200, "checkout stays available at capacity (oldest evicted)");

    // SIWE_CHALLENGE_MAX=2: the first two challenges were evicted oldest-first.
    const checkoutBody = (nonce) => ({
      tier: "pro_lifetime",
      ifrDiscount: true,
      walletAddress: WALLET,
      walletSignature: "0x11",
      walletNonce: nonce,
    });
    const evicted = await post(port, "/stripe/create-dynamic-checkout", "203.0.113.1", checkoutBody(nonces[0]));
    assert.strictEqual(evicted.statusCode, 403);
    assert.strictEqual(evicted.json.error, "invalid_wallet_challenge", "oldest challenge evicted at cap");
    const kept = await post(port, "/stripe/create-dynamic-checkout", "203.0.113.2", checkoutBody(nonces[3]));
    assert.strictEqual(kept.statusCode, 403);
    assert.strictEqual(kept.json.error, "wallet_signature_invalid", "newest challenge survives (fails later at signature)");

    console.log("limiter_eviction.test.js: PASS");
  } finally {
    await stopChild(child);
    fs.rmSync(dataDir, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
