"use strict";

const { test } = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { spawnSync } = require("node:child_process");
const vm = require("node:vm");
const harness = path.join(__dirname, "sc180-runtime.cjs");
const checks = ["confined_environment", "enforce_startup_health", "closed_http_routes",
  "legacy_register_refused", "bad_signature_unbound", "canonical_registration",
  "controlled_restart", "identity_continuity", "closed_routes_after_restart"];
const valid = () => ({ schema: 1, result: "PASS", nodeVersion: "v22.23.4",
  checkpoints: [...checks], plannedRestarts: 1,
  childExits: [{ code: 0, signal: null }, { code: 0, signal: null }],
  cleanup: { childStopped: true, dataRemoved: true } });

function collect(value, raw = false, setup) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "sc180-synthetic-"));
  try {
    const input = path.join(dir, "input.json");
    const output = path.join(dir, "output.json");
    if (value !== undefined) fs.writeFileSync(input, raw ? value : JSON.stringify(value));
    if (setup) setup({ dir, input, output });
    const child = spawnSync(process.execPath, [harness, "collect", input, output], {
      encoding: "utf8", timeout: 5000, maxBuffer: 16384,
      env: {}, cwd: dir,
    });
    assert.equal(child.error, undefined);
    assert.equal(child.signal, null);
    const text = fs.existsSync(output) ? fs.readFileSync(output, "utf8") : "";
    return { status: child.status, text, receipt: text ? JSON.parse(text) : null,
      stdout: child.stdout, stderr: child.stderr };
  } finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

for (const [name, value, reason] of [
  ["missing", undefined, "read_failed"], ["empty", "", "empty_input"],
  ["malformed", "{SYNTHETIC_PRIVATE_MARKER", "parse_failed"],
  ["oversize", "SYNTHETIC_PRIVATE_MARKER".repeat(500), "oversize_input"],
  ["null", "null", "invalid_shape"], ["array", "[]", "invalid_shape"],
  ["shape", '{"schema":1,"private":"SYNTHETIC_PRIVATE_MARKER"}', "invalid_shape"],
]) test(`collector classifies ${name} without raw input`, () => {
  const result = collect(value, true);
  assert.equal(result.status, 1);
  assert.deepEqual(result.receipt, { schema: 1, result: "FAIL",
    diagnostic: { stage: "collector", reason } });
  assert.doesNotMatch(result.text + result.stdout + result.stderr, /SYNTHETIC_PRIVATE_MARKER/);
});

test("valid PASS and unknown-key suppression", () => {
  const value = valid();
  value.private = "SYNTHETIC_PRIVATE_MARKER";
  value.childExits[0].private = value.private;
  value.cleanup.private = value.private;
  value.diagnostic = { stage: "complete", reason: "none", private: value.private };
  const result = collect(value);
  assert.equal(result.status, 0);
  assert.equal(result.receipt.result, "PASS");
  assert.doesNotMatch(result.text, /private|SYNTHETIC_PRIVATE_MARKER/);
});

test("version mismatch retained but never PASS", () => {
  const value = valid(); value.nodeVersion = "v22.23.3";
  const result = collect(value);
  assert.equal(result.status, 1);
  assert.equal(result.receipt.nodeVersion, "v22.23.3");
  assert.equal(result.receipt.result, "FAIL");
  assert.equal(result.receipt.diagnostic.reason, "version_mismatch");
});

for (const signal of ["SIGSEGV", "SIGABRT", "SYNTHETIC_PRIVATE_MARKER"]) {
  test(`failed child signal is sanitized: ${signal === "SYNTHETIC_PRIVATE_MARKER" ? "unknown" : signal}`, () => {
    const value = valid(); value.childExits[0] = { code: null, signal };
    const result = collect(value);
    assert.equal(result.status, 1);
    assert.equal(result.receipt.result, "FAIL");
    assert.equal(result.receipt.childExits[0].signal,
      signal === "SYNTHETIC_PRIVATE_MARKER" ? "OTHER" : signal);
    assert.doesNotMatch(result.text, /SYNTHETIC_PRIVATE_MARKER/);
  });
}

test("protected pre-import failure receipt contract", async () => {
  const source = fs.readFileSync(harness, "utf8");
  const runtime = source.slice(source.indexOf("async function runtime()"));
  const protectedStart = runtime.indexOf("  try {\n    requireTrue(process.version");
  assert.ok(protectedStart >= 0);
  assert.ok(runtime.indexOf('requireApp("ws")') > protectedStart);
  assert.ok(runtime.indexOf('requireApp("./src/security/identity_protocol.js")') > protectedStart);
  assert.match(runtime, /catch \{ await finish\(false, stage === "bootstrap" \? "bootstrap_failed"/);
  // Execute only the actual finalizer, with synthetic state and no real filesystem/process access.
  const finalizer = runtime.slice(runtime.indexOf("  async function finish("),
    runtime.indexOf("  const deadline ="));
  let emitted;
  let exitCode;
  const removed = [];
  const context = vm.createContext({ finalizing: false, deadline: 0, clearTimeout() {},
    sockets: [], stopChild: async () => {}, unexpectedExit: false, childError: false,
    checkpoints: [], childExits: [], plannedRestarts: 0, stage: "bootstrap",
    CHECKS: checks, root: "/synthetic-owned", data: "/synthetic-owned/data",
    fs: { rmSync: name => removed.push(name), existsSync: () => false },
    process: { version: "v22.23.4", stdout: { write(text, callback) { emitted = text; callback(); } },
      exit: code => { exitCode = code; } },
  });
  await vm.runInContext(`${finalizer}\nfinish(false, "bootstrap_failed")`, context, { timeout: 1000 });
  assert.equal(exitCode, 1);
  assert.deepEqual(removed, ["/synthetic-owned/data", "/synthetic-owned/home"]);
  const value = JSON.parse(emitted);
  assert.deepEqual(value.diagnostic, { stage: "bootstrap", reason: "bootstrap_failed" });
  assert.deepEqual(value.checkpoints, []);
  const result = collect(value);
  assert.equal(result.status, 1);
  assert.deepEqual(result.receipt.diagnostic, value.diagnostic);
});

test("collector accepts exactly 8192 bytes, rejects 8193 bytes", () => {
  const json = JSON.stringify(valid());
  assert.equal(collect(json.padEnd(8192, " "), true).status, 0);
  const result = collect(json.padEnd(8193, " "), true);
  assert.equal(result.status, 1);
  assert.equal(result.receipt.diagnostic.reason, "oversize_input");
});

for (const kind of ["directory", "symlink"]) test(`collector rejects ${kind} input`, () => {
  const result = collect(undefined, false, ({ dir, input }) => {
    if (kind === "directory") fs.mkdirSync(input);
    else {
      const target = path.join(dir, "synthetic-target");
      fs.writeFileSync(target, JSON.stringify(valid()));
      fs.symlinkSync(target, input);
    }
  });
  assert.equal(result.status, 1);
  assert.equal(result.receipt.diagnostic.reason, "read_failed");
});

test("collector does not overwrite an existing output", () => {
  const result = collect(valid(), false, ({ output }) => {
    fs.writeFileSync(output, '{"synthetic":"preserved"}\n');
  });
  assert.equal(result.status, 1);
  assert.equal(result.text, '{"synthetic":"preserved"}\n');
});

for (const [name, change] of [
  ["incomplete", v => v.checkpoints.pop()],
  ["wrong order", v => v.checkpoints.reverse()],
  ["restart missing", v => { v.plannedRestarts = 0; }],
  ["exit missing", v => v.childExits.pop()],
  ["nonzero exit", v => { v.childExits[0].code = 1; }],
  ["child cleanup", v => { v.cleanup.childStopped = false; }],
  ["data cleanup", v => { v.cleanup.dataRemoved = false; }],
  ["explicit FAIL", v => { v.result = "FAIL"; }],
  ["unbounded version", v => { v.nodeVersion = "v123456789.2.3"; }],
  ["unsafe exit", v => { v.childExits[0].code = Number.MAX_SAFE_INTEGER; }],
  ["unknown diagnostic", v => { v.diagnostic = { stage: "SYNTHETIC_PRIVATE_MARKER",
    reason: "SYNTHETIC_PRIVATE_MARKER" }; }],
  ["failure diagnostic despite PASS", v => { v.diagnostic = { stage: "bootstrap", reason: "bootstrap_failed" }; }],
]) test(`strict rejection: ${name}`, () => {
  const value = valid(); change(value);
  const result = collect(value);
  assert.equal(result.status, 1);
  assert.equal(result.receipt.result, "FAIL");
  assert.doesNotMatch(result.text, /SYNTHETIC_PRIVATE_MARKER/);
});
