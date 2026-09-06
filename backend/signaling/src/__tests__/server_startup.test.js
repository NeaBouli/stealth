"use strict";

const assert = require("assert");
const fs = require("fs");
const http = require("http");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const SIGNALING_ROOT = path.resolve(__dirname, "../..");

function getHealth(port) {
  return new Promise((resolve, reject) => {
    const request = http.get({
      host: "127.0.0.1",
      port,
      path: "/health",
      timeout: 500,
    }, (response) => {
      let body = "";
      response.setEncoding("utf8");
      response.on("data", (chunk) => { body += chunk; });
      response.on("end", () => resolve({ statusCode: response.statusCode, body }));
    });
    request.once("timeout", () => request.destroy(new Error("health request timed out")));
    request.once("error", reject);
  });
}

function assertChildRunning(child, output, lifecycle, phase) {
  if (lifecycle.error) {
    throw new Error(`signaling server failed to spawn during ${phase}: ${lifecycle.error.message}\n${output.value}`);
  }
  if (child.exitCode !== null || child.signalCode !== null) {
    const result = child.exitCode !== null ? child.exitCode : child.signalCode;
    throw new Error(`signaling server exited before ${phase} (${result})\n${output.value}`);
  }
}

async function waitForListeningPort(child, output, lifecycle) {
  const deadline = Date.now() + 10000;

  while (Date.now() < deadline) {
    const match = output.value.match(/Server running on port (\d+)/);
    if (match) return Number(match[1]);
    assertChildRunning(child, output, lifecycle, "listening");
    await new Promise((resolve) => setTimeout(resolve, 25));
  }

  throw new Error(`signaling server did not report its listening port\n${output.value}`);
}

async function waitForHealth(child, port, output, lifecycle) {
  const deadline = Date.now() + 10000;
  let lastError;

  while (Date.now() < deadline) {
    assertChildRunning(child, output, lifecycle, "health check");
    try {
      return await getHealth(port);
    } catch (error) {
      lastError = error;
      await new Promise((resolve) => setTimeout(resolve, 100));
    }
  }

  throw new Error(`signaling server did not become healthy: ${lastError?.message || "unknown"}\n${output.value}`);
}

async function stopChild(child) {
  if (!child.pid || child.exitCode !== null || child.signalCode !== null) return;

  const exited = new Promise((resolve) => child.once("exit", resolve));
  child.kill("SIGTERM");
  const stopped = await Promise.race([
    exited.then(() => true),
    new Promise((resolve) => setTimeout(() => resolve(false), 3000)),
  ]);
  if (!stopped && child.exitCode === null) {
    if (child.kill("SIGKILL")) await exited;
  }
}

(async () => {
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-startup-"));
  const walletsFile = path.join(dataDir, "wallets.json");
  fs.writeFileSync(walletsFile, JSON.stringify({ wallets: [] }));
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
      WALLETS_FILE: walletsFile,
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_RTDN_ENABLED: "false",
      LEGACY_STRIPE_CHECKOUT_ENABLED: "false",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });

  child.once("error", (error) => { lifecycle.error = error; });
  child.stdout.on("data", (chunk) => { output.value += chunk.toString(); });
  child.stderr.on("data", (chunk) => { output.value += chunk.toString(); });

  try {
    const port = await waitForListeningPort(child, output, lifecycle);
    const response = await waitForHealth(child, port, output, lifecycle);
    assert.strictEqual(response.statusCode, 200, output.value);
    assert.strictEqual(JSON.parse(response.body).status, "ok", output.value);
    assert.match(output.value, /Server running on port/, output.value);
    console.log("server_startup.test.js: PASS");
  } finally {
    await stopChild(child);
    fs.rmSync(dataDir, { recursive: true, force: true });
  }
})().catch((error) => {
  console.error(error.stack || error.message);
  process.exitCode = 1;
});
