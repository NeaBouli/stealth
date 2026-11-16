"use strict";

// This file has host-only source/receipt modes and a confined-container mode.
// No mode executes on import; syntax checks do not start the application.
const fs = require("node:fs");
const crypto = require("node:crypto");
const { execFileSync, spawn } = require("node:child_process");
const { createRequire } = require("node:module");

const BASE = "eea4aadd8d658bfc3831615d4a3344e73b81b2b6";
const RUNTIME_NODE = "v22.23.4";
const ADDITIONS = [".github/scripts/sc180-runtime.cjs", ".github/scripts/sc180-runtime.test.cjs",
  ".github/workflows/sc180-isolated-runtime.yml"];
const CHECKS = ["confined_environment", "enforce_startup_health", "closed_http_routes",
  "legacy_register_refused", "bad_signature_unbound", "canonical_registration",
  "controlled_restart", "identity_continuity", "closed_routes_after_restart"];
const STAGES = ["bootstrap", "qualification", "cleanup", "complete", "collector"];
const REASONS = ["none", "bootstrap_failed", "qualification_failed", "cleanup_failed",
  "timeout", "signal", "read_failed", "empty_input", "oversize_input", "parse_failed",
  "invalid_shape", "version_mismatch", "incomplete", "child_exit", "reported_failure"];
const SIGNALS = ["SIGHUP", "SIGINT", "SIGQUIT", "SIGILL", "SIGTRAP", "SIGABRT",
  "SIGBUS", "SIGFPE", "SIGKILL", "SIGUSR1", "SIGSEGV", "SIGUSR2", "SIGPIPE",
  "SIGALRM", "SIGTERM", "SIGCHLD", "SIGCONT", "SIGSTOP", "SIGTSTP", "SIGTTIN",
  "SIGTTOU", "SIGURG", "SIGXCPU", "SIGXFSZ", "SIGVTALRM", "SIGPROF", "SIGWINCH",
  "SIGIO", "SIGPWR", "SIGSYS"];

function requireTrue(value) {
  // Never serialize assertions, server responses, keys, logs or exception text.
  if (!value) throw new Error("qualification_failed");
}

function writeJson(file, value) {
  fs.writeFileSync(file, `${JSON.stringify(value)}\n`, { flag: "wx", mode: 0o600 });
}

function sourceReceipt(file) {
  const git = (...args) => execFileSync("git", args, {
    encoding: "utf8", timeout: 10000, maxBuffer: 1024 * 1024, stdio: ["ignore", "pipe", "ignore"],
  }).trim();
  requireTrue(process.env.GITHUB_EVENT_NAME === "pull_request" && process.env.GITHUB_RUN_ATTEMPT === "1");
  const event = JSON.parse(fs.readFileSync(process.env.GITHUB_EVENT_PATH, "utf8"));
  const pr = event.pull_request;
  requireTrue(event.action === "opened" && event.repository.full_name === "NeaBouli/stealth"
    && event.repository.private === false && pr.head.repo.full_name === event.repository.full_name
    && pr.head.ref === "agent/codex/SC-180-RUNTIME-DIAG-CI-20261116"
    && pr.base.ref === "main" && pr.base.sha === BASE);
  requireTrue(git("rev-parse", "HEAD") === pr.head.sha && git("rev-parse", "HEAD^") === BASE);
  const parents = git("show", "--no-patch", "--format=%P", "HEAD").split(" ");
  requireTrue(parents.length === 1 && parents[0] === BASE);
  requireTrue(git("status", "--porcelain", "--untracked-files=all") === "");
  requireTrue(git("diff", "--name-status", "--no-renames", BASE, "HEAD")
    === ADDITIONS.map(name => `A\t${name}`).join("\n"));
  for (const name of ADDITIONS) {
    requireTrue(git("ls-tree", "HEAD", "--", name).startsWith("100644 blob "));
    requireTrue(fs.lstatSync(name).isFile() && !fs.lstatSync(name).isSymbolicLink());
  }
  const backendTree = git("rev-parse", "HEAD:backend/signaling");
  requireTrue(backendTree === git("rev-parse", `${BASE}:backend/signaling`));
  requireTrue(fs.readFileSync("backend/signaling/Dockerfile", "utf8").startsWith(
    "FROM node:22-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 AS base\n"));
  const names = [...ADDITIONS, "backend/signaling/Dockerfile", "backend/signaling/package.json",
    "backend/signaling/package-lock.json", "backend/signaling/src/security/identity_protocol.js"];
  writeJson(file, { schema: 1, base: BASE, head: pr.head.sha, backendTree,
    sourceGuard: "PASS", hashes: Object.fromEntries(names.map(name => [name,
      crypto.createHash("sha256").update(fs.readFileSync(name)).digest("hex")])) });
}

function collectReceipt(input, output) {
  const reject = reason => {
    writeJson(output, { schema: 1, result: "FAIL", diagnostic: { stage: "collector", reason } });
    requireTrue(false);
  };
  let bytes;
  let fd;
  let readFailure;
  try {
    fd = fs.openSync(input, fs.constants.O_RDONLY | fs.constants.O_NONBLOCK | fs.constants.O_NOFOLLOW);
    const stat = fs.fstatSync(fd);
    if (!stat.isFile()) readFailure = "read_failed";
    else if (stat.size > 8192) readFailure = "oversize_input";
    else {
      bytes = Buffer.alloc(8193);
      let count = 0;
      let read;
      while (count < bytes.length && (read = fs.readSync(fd, bytes, count, bytes.length - count, null)) > 0) {
        count += read;
      }
      bytes = bytes.subarray(0, count);
    }
  } catch { readFailure = "read_failed"; }
  finally {
    if (fd !== undefined) {
      try { fs.closeSync(fd); } catch { readFailure = "read_failed"; }
    }
  }
  if (readFailure) return reject(readFailure);
  if (bytes.length > 8192) return reject("oversize_input");
  if (bytes.length === 0) return reject("empty_input");
  let value;
  try { value = JSON.parse(bytes.toString("utf8")); }
  catch { return reject("parse_failed"); }
  if (!(value && !Array.isArray(value) && value.schema === 1 && ["PASS", "FAIL"].includes(value.result)
    && typeof value.nodeVersion === "string" && /^v\d{1,3}\.\d{1,3}\.\d{1,3}$/.test(value.nodeVersion)
    && Number.isInteger(value.plannedRestarts) && value.plannedRestarts >= 0 && value.plannedRestarts <= 1
    && Array.isArray(value.checkpoints) && value.checkpoints.length <= CHECKS.length
    && value.checkpoints.every((name, index) => name === CHECKS[index])
    && Array.isArray(value.childExits) && value.childExits.length <= 2
    && value.childExits.every(exit => exit && !Array.isArray(exit)
      && (exit.code === null || (Number.isInteger(exit.code) && exit.code >= -2147483648 && exit.code <= 2147483647))
      && (exit.signal === null || typeof exit.signal === "string"))
    && typeof value.cleanup?.childStopped === "boolean" && typeof value.cleanup?.dataRemoved === "boolean"
    && (value.diagnostic === undefined || (value.diagnostic && !Array.isArray(value.diagnostic)
      && STAGES.includes(value.diagnostic.stage) && REASONS.includes(value.diagnostic.reason))))) {
    return reject("invalid_shape");
  }
  const success = value.result === "PASS" && value.nodeVersion === RUNTIME_NODE
    && (!value.diagnostic || (value.diagnostic.stage === "complete" && value.diagnostic.reason === "none"))
    && value.checkpoints.length === CHECKS.length
    && value.plannedRestarts === 1 && value.childExits.length === 2
    && value.childExits.every(exit => exit.code === 0 && exit.signal === null)
    && value.cleanup.childStopped && value.cleanup.dataRemoved;
  let diagnostic = value.diagnostic && { stage: value.diagnostic.stage, reason: value.diagnostic.reason };
  if (value.nodeVersion !== RUNTIME_NODE) diagnostic = { stage: "collector", reason: "version_mismatch" };
  else if (!success && (!diagnostic || diagnostic.reason === "none")) {
    diagnostic = { stage: "collector", reason: value.result === "FAIL" ? "reported_failure" : "incomplete" };
  }
  // Reconstruct an allowlist; unexpected keys can never reach the artifact.
  writeJson(output, { schema: 1, result: success ? "PASS" : "FAIL", nodeVersion: value.nodeVersion,
    checkpoints: value.checkpoints, plannedRestarts: value.plannedRestarts,
    childExits: value.childExits.map(exit => ({ code: exit.code,
      signal: exit.signal === null ? null : SIGNALS.includes(exit.signal) ? exit.signal : "OTHER" })),
    cleanup: { childStopped: value.cleanup.childStopped, dataRemoved: value.cleanup.dataRemoved },
    ...(diagnostic ? { diagnostic } : {}) });
  requireTrue(success);
}

async function runtime() {
  const http = require("node:http");
  let WebSocket;
  let protocol;
  let stage = "bootstrap";
  const checkpoints = [];
  const childExits = [];
  const sockets = new Set();
  let child;
  let childDone;
  let childError = false;
  let stopping = false;
  let finalizing = false;
  let unexpectedExit = false;
  let logs = Buffer.alloc(0);
  let plannedRestarts = 0;
  const root = "/sc180";
  const data = `${root}/data`;
  const registryFile = `${data}/identity_registry.json`;
  const delay = ms => new Promise(resolve => setTimeout(resolve, ms));
  const active = () => requireTrue(!finalizing && !unexpectedExit && !childError);
  const checkpoint = name => {
    active();
    requireTrue(name === CHECKS[checkpoints.length]);
    checkpoints.push(name);
  };
  const running = () => child && child.exitCode === null && child.signalCode === null && !childError;

  async function stopChild() {
    stopping = true;
    if (running()) {
      child.kill("SIGTERM");
      await Promise.race([childDone, delay(3000)]);
      if (running()) {
        child.kill("SIGKILL");
        await Promise.race([childDone, delay(2000)]);
      }
    }
    requireTrue(!running());
    // Logs stay in bounded private memory and are discarded, never printed.
    logs.fill(0);
    logs = Buffer.alloc(0);
  }

  async function finish(success, reason = "qualification_failed") {
    if (finalizing) return;
    finalizing = true;
    clearTimeout(deadline);
    for (const socket of sockets) socket.terminate();
    let childStopped = false;
    let dataRemoved = false;
    try { await stopChild(); childStopped = true; } catch { success = false; }
    try {
      fs.rmSync(data, { recursive: true, force: true });
      fs.rmSync(`${root}/home`, { recursive: true, force: true });
      dataRemoved = !fs.existsSync(data) && !fs.existsSync(`${root}/home`);
    } catch { success = false; }
    success = success && !unexpectedExit && !childError && childStopped && dataRemoved
      && checkpoints.length === CHECKS.length && childExits.length === 2
      && childExits.every(exit => exit.code === 0 && exit.signal === null);
    const receipt = { schema: 1, result: success ? "PASS" : "FAIL", nodeVersion: process.version, checkpoints,
      plannedRestarts, childExits, cleanup: { childStopped, dataRemoved },
      diagnostic: success ? { stage: "complete", reason: "none" }
        : !childStopped || !dataRemoved ? { stage: "cleanup", reason: "cleanup_failed" }
          : { stage, reason: unexpectedExit || childError ? "child_exit" : reason } };
    process.stdout.write(`${JSON.stringify(receipt)}\n`, () => process.exit(success ? 0 : 1));
  }
  const deadline = setTimeout(() => { void finish(false, "timeout"); }, 90000);
  process.once("SIGTERM", () => { void finish(false, "signal"); });
  process.once("SIGINT", () => { void finish(false, "signal"); });

  function request(method, route, body, headers = {}) {
    active();
    requireTrue(new Set(["/health", "/status/live", "/routing/list", "/admin/gifts",
      "/internal/vlabs/fulfill", "/internal/vlabs/revoke"]).has(route));
    return new Promise((resolve, reject) => {
      const encoded = body === undefined ? null : JSON.stringify(body);
      const req = http.request({ host: "127.0.0.1", port: 8080, path: route, method,
        agent: false, timeout: 3000,
        headers: { ...headers, ...(encoded ? { "Content-Type": "application/json",
          "Content-Length": Buffer.byteLength(encoded) } : {}) } }, response => {
        let bytes = 0;
        let text = "";
        response.on("error", reject);
        response.on("data", chunk => {
          bytes += chunk.length;
          if (bytes > 32768) response.destroy(new Error("response_limit"));
          else text += chunk.toString("utf8");
        });
        response.on("end", () => {
          try { resolve({ status: response.statusCode, body: JSON.parse(text) }); }
          catch { reject(new Error("response_shape")); }
        });
      });
      req.once("timeout", () => req.destroy(new Error("request_timeout")));
      req.once("error", reject);
      req.end(encoded);
    });
  }

  async function startChild(adminKey) {
    active();
    requireTrue(!running() && childExits.length === plannedRestarts && plannedRestarts <= 1);
    requireTrue(plannedRestarts === 0 ? adminKey === undefined
      : typeof adminKey === "string" && /^[a-f0-9]{64}$/.test(adminKey));
    stopping = false;
    logs = Buffer.alloc(0);
    child = spawn(process.execPath, ["src/server.js"], { cwd: "/app",
      env: { PATH: "/usr/local/bin:/usr/bin:/bin", HOME: `${root}/home`, TMPDIR: root,
        NODE_ENV: "production", DATA_DIR: data, PORT: "8080", IDENTITY_PROTOCOL_MODE: "enforce",
        PLAY_BILLING_ENABLED: "false", LEGACY_STRIPE_CHECKOUT_ENABLED: "false",
        STUN_URL: "stun:synthetic.invalid:3478", ...(adminKey ? { ADMIN_API_KEY: adminKey } : {}) },
      stdio: ["ignore", "pipe", "pipe"] });
    childDone = new Promise(resolve => {
      child.once("error", () => { childError = true; resolve(); });
      child.once("exit", (code, signal) => {
        childExits.push({ code, signal });
        if (!stopping) unexpectedExit = true;
        resolve();
      });
    });
    const capture = chunk => {
      logs = Buffer.concat([logs, chunk]).subarray(-65536);
    };
    child.stdout.on("data", capture);
    child.stderr.on("data", capture);
    // Observation only: one spawn, no health request retry, no failure restart.
    const until = Date.now() + 10000;
    while (!logs.includes(Buffer.from("Server running on port 8080"))) {
      active();
      requireTrue(running() && Date.now() < until);
      await delay(25);
    }
    const health = await request("GET", "/health");
    requireTrue(health.status === 200 && health.body.status === "ok");
  }

  async function connect() {
    active();
    const socket = new WebSocket("ws://127.0.0.1:8080/signal", {
      handshakeTimeout: 3000, maxPayload: 32768, perMessageDeflate: false,
    });
    sockets.add(socket);
    const queue = [];
    let failed = false;
    let closeCode;
    socket.on("error", () => { failed = true; });
    socket.on("close", code => { closeCode = code; });
    socket.on("message", bytes => {
      try {
        const message = JSON.parse(bytes.toString("utf8"));
        if (queue.length >= 32) throw new Error("queue_limit");
        queue.push(message);
      } catch { failed = true; socket.terminate(); }
    });
    await new Promise((resolve, reject) => {
      socket.once("open", resolve);
      socket.once("error", reject);
    });
    async function receive(type) {
      const until = Date.now() + 3000;
      while (true) {
        active();
        requireTrue(!failed && Date.now() < until);
        const index = queue.findIndex(message => message.type === type || message.type === "ERROR");
        if (index !== -1) return queue.splice(index, 1)[0];
        requireTrue(closeCode === undefined);
        await delay(10);
      }
    }
    return { socket, receive, send: message => { active(); socket.send(JSON.stringify(message)); },
      async closed(expected) {
        const until = Date.now() + 3000;
        while (closeCode === undefined) { active(); requireTrue(Date.now() < until); await delay(10); }
        requireTrue(closeCode === expected);
      } };
  }

  function keyMaterial() {
    const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
    const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
    return { pair, ...protocol.parseIdentityPublicKey(encoded) };
  }
  function sign(pair, transcript) {
    return crypto.sign("sha256", Buffer.from(transcript, "utf8"), pair.privateKey).toString("base64url");
  }
  async function begin(peer, identity) {
    peer.send({ type: "IDENTITY_REGISTER_BEGIN", protocol: 2, clientId: identity.identityId,
      identityId: identity.identityId, publicKey: identity.publicKeyBase64Url });
    const challenge = await peer.receive("IDENTITY_REGISTER_CHALLENGE");
    requireTrue(challenge.type === "IDENTITY_REGISTER_CHALLENGE" && challenge.protocol === 2);
    const parts = challenge.challenge.split("\n");
    requireTrue(parts.length === 7);
    const transcript = protocol.registrationTranscript({ challengeId: challenge.challengeId,
      requestedClientId: identity.identityId, identityId: identity.identityId,
      keyHash: identity.keyHash, expiresAt: challenge.expiresAt, nonce: parts[6] });
    const now = Math.floor(Date.now() / 1000);
    requireTrue(transcript === challenge.challenge && challenge.expiresAt > now && challenge.expiresAt <= now + 61);
    return { challengeId: challenge.challengeId, transcript };
  }
  async function register(peer, identity) {
    const challenge = await begin(peer, identity);
    peer.send({ type: "IDENTITY_REGISTER_COMPLETE", protocol: 2, challengeId: challenge.challengeId,
      signature: sign(identity.pair, challenge.transcript) });
    const registered = await peer.receive("REGISTERED");
    requireTrue(registered.type === "REGISTERED" && registered.protocol === 2
      && registered.clientId === identity.identityId && registered.identityId === identity.identityId
      && registered.iceServers.length === 1 && registered.iceServers[0].urls === "stun:synthetic.invalid:3478");
  }
  async function closedRoutes(adminKey) {
    for (const [route, field] of [["/routing/list", "routes"], ["/admin/gifts", "gifts"]]) {
      const wrong = crypto.randomBytes(32).toString("hex");
      requireTrue(wrong !== adminKey);
      for (const headers of [{}, { "X-Admin-Key": wrong }]) {
        const response = await request("GET", route, undefined, headers);
        requireTrue(response.status === (adminKey ? 401 : 403)
          && Object.keys(response.body).length === 1
          && response.body.error === (adminKey ? "unauthorized" : "admin_api_disabled"));
      }
      if (adminKey) {
        const response = await request("GET", route, undefined, { "X-Admin-Key": adminKey });
        requireTrue(response.status === 200 && Object.keys(response.body).length === 1
          && Array.isArray(response.body[field]) && response.body[field].length === 0);
      }
    }
    for (const route of ["/internal/vlabs/fulfill", "/internal/vlabs/revoke"]) {
      const response = await request("POST", route, {});
      requireTrue(response.status === 503 && response.body.ok === false
        && response.body.error === "Fulfillment is not configured");
    }
  }
  function registry() { return JSON.parse(fs.readFileSync(registryFile, "utf8")); }

  try {
    requireTrue(process.version === RUNTIME_NODE && process.getuid() === 1001 && process.getgid() === 1001
      && process.env.HOME === `${root}/home` && process.env.NODE_ENV === "production"
      && Object.keys(process.env).sort().join(",") === "HOME,NODE_ENV,PATH,TMPDIR");
    requireTrue(fs.readFileSync("/proc/self/status", "utf8").includes("NoNewPrivs:\t1")
      && /CapEff:\s+0+\n/.test(fs.readFileSync("/proc/self/status", "utf8")));
    requireTrue(fs.readdirSync("/sys/class/net").join(",") === "lo"
      && !fs.existsSync("/var/run/docker.sock") && fs.readdirSync(root).length === 0);
    const tmp = fs.lstatSync("/tmp");
    requireTrue(tmp.isDirectory() && (tmp.mode & 0o7777) === 0o1777);
    let readOnly = false;
    try { fs.writeFileSync("/tmp/sc180-write-probe", "probe", { flag: "wx" }); }
    catch (error) { readOnly = error.code === "EROFS"; }
    requireTrue(readOnly);
    fs.mkdirSync(`${root}/home`, { mode: 0o700 });
    fs.mkdirSync(data, { mode: 0o700 });
    writeJson(`${data}/wallets.json`, { wallets: [] });
    checkpoint("confined_environment");
    const requireApp = createRequire("/app/package.json");
    WebSocket = requireApp("ws");
    protocol = requireApp("./src/security/identity_protocol.js");
    stage = "qualification";
    await startChild();
    checkpoint("enforce_startup_health");
    await closedRoutes();
    checkpoint("closed_http_routes");

    const legacy = await connect();
    legacy.send({ type: "REGISTER", clientId: "sc180-synthetic-legacy" });
    const refusal = await legacy.receive("ERROR");
    requireTrue(refusal.error === "identity_protocol_required");
    await legacy.closed(4003);
    checkpoint("legacy_register_refused");

    const identity = keyMaterial();
    const attacker = keyMaterial();
    const bad = await connect();
    const challenge = await begin(bad, identity);
    bad.send({ type: "IDENTITY_REGISTER_COMPLETE", protocol: 2, challengeId: challenge.challengeId,
      signature: sign(attacker.pair, challenge.transcript) });
    requireTrue((await bad.receive("ERROR")).error === "invalid_identity_proof");
    bad.send({ type: "CALL_INVITE", protocol: 2, sessionId: crypto.randomUUID(), to: identity.identityId });
    requireTrue((await bad.receive("ERROR")).error === "identity_protocol_required");
    const status = await request("GET", "/status/live");
    requireTrue(status.status === 200 && status.body.registeredIds === 0
      && registry().identities.length === 0 && registry().aliases.length === 0);
    bad.socket.close(1000);
    await bad.closed(1000);
    checkpoint("bad_signature_unbound");

    const good = await connect();
    await register(good, identity);
    const saved = registry();
    requireTrue(saved.schema === 1 && saved.identities.length === 1 && saved.aliases.length === 0
      && saved.identities[0].identityId === identity.identityId
      && saved.identities[0].publicKey === identity.publicKeyBase64Url
      && saved.identities[0].status === "active");
    checkpoint("canonical_registration");
    good.socket.close(1000);
    await good.closed(1000);
    await stopChild();
    active();
    requireTrue(childExits.length === 1 && childExits[0].code === 0 && childExits[0].signal === null);
    plannedRestarts = 1;
    const adminKey = crypto.randomBytes(32).toString("hex");
    await startChild(adminKey);
    checkpoint("controlled_restart");
    requireTrue(JSON.stringify(registry()) === JSON.stringify(saved));
    const resumed = await connect();
    await register(resumed, identity);
    requireTrue(registry().identities.length === 1 && registry().aliases.length === 0
      && registry().identities[0].createdAt === saved.identities[0].createdAt
      && registry().identities[0].publicKey === saved.identities[0].publicKey);
    checkpoint("identity_continuity");
    await closedRoutes(adminKey);
    checkpoint("closed_routes_after_restart");
    await finish(true);
  } catch { await finish(false, stage === "bootstrap" ? "bootstrap_failed" : "qualification_failed"); }
}

if (require.main === module) {
  const [mode, input, output] = process.argv.slice(2);
  if (mode === "runtime" && input === undefined) {
    runtime().catch(() => { process.exitCode = 1; });
  } else {
    try {
      if (mode === "source" && input && !output) sourceReceipt(input);
      else if (mode === "collect" && input && output) collectReceipt(input, output);
      else throw new Error("mode");
    } catch {
      process.stderr.write("SC-180 static/receipt guard failed\n");
      process.exitCode = 1;
    }
  }
}
