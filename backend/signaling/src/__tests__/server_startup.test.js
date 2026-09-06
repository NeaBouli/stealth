"use strict";

const assert = require("assert");
const fs = require("fs");
const http = require("http");
const net = require("net");
const os = require("os");
const path = require("path");
const { spawn } = require("child_process");

const SIGNALING_ROOT = path.resolve(__dirname, "../..");

function allocatePort() {
  return new Promise((resolve, reject) => {
    const server = net.createServer();
    server.once("error", reject);
    server.listen(0, "127.0.0.1", () => {
      const address = server.address();
      server.close((error) => {
        if (error) reject(error);
        else resolve(address.port);
      });
    });
  });
}

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

async function waitForHealth(child, port, output) {
  const deadline = Date.now() + 10000;
  let lastError;

  while (Date.now() < deadline) {
    if (child.exitCode !== null) {
      throw new Error(`signaling server exited before health check (${child.exitCode})\n${output.value}`);
    }
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
  if (child.exitCode !== null) return;

  const exited = new Promise((resolve) => child.once("exit", resolve));
  child.kill("SIGTERM");
  const stopped = await Promise.race([
    exited.then(() => true),
    new Promise((resolve) => setTimeout(() => resolve(false), 3000)),
  ]);
  if (!stopped && child.exitCode === null) {
    child.kill("SIGKILL");
    await exited;
  }
}

(async () => {
  const port = await allocatePort();
  const dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-startup-"));
  const output = { value: "" };
  const child = spawn(process.execPath, ["src/server.js"], {
    cwd: SIGNALING_ROOT,
    env: {
      HOME: process.env.HOME || "",
      PATH: process.env.PATH || "",
      NODE_ENV: "test",
      PORT: String(port),
      DATA_DIR: dataDir,
      GOOGLE_PLAY_BILLING_ENABLED: "false",
      GOOGLE_PLAY_RTDN_ENABLED: "false",
      LEGACY_STRIPE_CHECKOUT_ENABLED: "false",
    },
    stdio: ["ignore", "pipe", "pipe"],
  });

  child.stdout.on("data", (chunk) => { output.value += chunk.toString(); });
  child.stderr.on("data", (chunk) => { output.value += chunk.toString(); });

  try {
    const response = await waitForHealth(child, port, output);
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
