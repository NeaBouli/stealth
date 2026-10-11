"use strict";

// STX-20 (spawned server): x-powered-by is disabled; HSTS
// (max-age=31536000; includeSubDomains) is emitted only when the TRUST_PROXY
// deployment contract reports an HTTPS front connection; /licenses/status
// stays public with aggregate-only data.
// Run: node src/__tests__/http_hygiene.test.js

const assert = require("assert");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const SIGNALING_ROOT = path.resolve(__dirname, "../..");
const HSTS = "max-age=31536000; includeSubDomains";

function get(port, route, headers = {}) {
  return new Promise((resolve, reject) => {
    const request = http.get({ host: "127.0.0.1", port, path: route, timeout: 2000, headers }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { body += chunk; });
      response.on("end", () => resolve({ statusCode: response.statusCode, headers: response.headers, body }));
    });
    request.once("timeout", () => request.destroy(new Error("request timed out")));
    request.once("error", reject);
  });
}

async function startServer(extraEnv) {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-http-hygiene-"));
  const walletsFile = path.join(dataDir, "wallets.json");
  fs.writeFileSync(walletsFile, JSON.stringify({ wallets: [] }));
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
  return { child, port, dataDir, output };
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

(async () => {
  // Trusted proxy deployment: HTTPS front connection gets HSTS, plain one does not.
  const proxied = await startServer({ TRUST_PROXY: "1" });
  try {
    const https = await get(proxied.port, "/health", { "x-forwarded-proto": "https" });
    assert.strictEqual(https.statusCode, 200);
    assert.strictEqual(https.headers["strict-transport-security"], HSTS, "HSTS on proxied HTTPS response");
    assert.ok(!("x-powered-by" in https.headers), "x-powered-by disabled");

    const plain = await get(proxied.port, "/health");
    assert.ok(!("strict-transport-security" in plain.headers), "no HSTS without an HTTPS front signal");
    assert.ok(!("x-powered-by" in plain.headers), "x-powered-by disabled");

    const licenses = await get(proxied.port, "/licenses/status", { "x-forwarded-proto": "https" });
    assert.strictEqual(licenses.statusCode, 200);
    assert.strictEqual(licenses.headers["strict-transport-security"], HSTS, "HSTS on public API response");
    const payload = JSON.parse(licenses.body);
    assert.ok(Object.keys(payload).length > 0);
    for (const [tier, info] of Object.entries(payload)) {
      assert.match(tier, /^[a-z0-9_]+$/, "aggregate tier keys only");
      assert.deepEqual(
        Object.keys(info).sort(),
        ["currentPrice", "nextPrice", "remaining", "sold", "soldOut"],
        `${tier} exposes aggregate counts/prices only`
      );
      assert.strictEqual(typeof info.sold, "number");
      assert.strictEqual(typeof info.soldOut, "boolean");
    }
  } finally {
    await stopServer(proxied);
  }

  // Without TRUST_PROXY the client-supplied X-Forwarded-Proto is ignored.
  const direct = await startServer({});
  try {
    const spoofed = await get(direct.port, "/health", { "x-forwarded-proto": "https" });
    assert.strictEqual(spoofed.statusCode, 200);
    assert.ok(!("strict-transport-security" in spoofed.headers), "no HSTS without the trusted-proxy contract");
    assert.ok(!("x-powered-by" in spoofed.headers), "x-powered-by disabled");
  } finally {
    await stopServer(direct);
  }

  console.log("http_hygiene.test.js: PASS");
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
