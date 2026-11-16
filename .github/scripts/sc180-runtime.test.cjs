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

const repo = path.resolve(__dirname, "../..");
const workflow = fs.readFileSync(path.join(repo, ".github/workflows/sc180-isolated-runtime.yml"), "utf8");
const mode = name => fs.lstatSync(name).mode & 0o7777;
const archiveFiles = ["Dockerfile", ".dockerignore", "package.json", "package-lock.json",
  "src/server.js", "src/security/identity_protocol.js"];
const archiveDirs = ["src", "src/security"];

function command(name, args, options = {}) {
  const result = spawnSync(name, args, { timeout: 10000, maxBuffer: 8 * 1024 * 1024, ...options });
  assert.equal(result.error, undefined);
  assert.equal(result.signal, null);
  assert.equal(result.status, 0, `${name}: ${result.stderr}`);
  return result.stdout;
}

function archiveFixture(run) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), "sc180-archive-synthetic-"));
  fs.chmodSync(dir, 0o700);
  try { run(dir); }
  finally { fs.rmSync(dir, { recursive: true, force: true }); }
}

function extract(archive, dest, preserve) {
  fs.mkdirSync(dest, { mode: 0o700 });
  command("bash", ["-c", `umask 077; tar -x ${preserve ? "--same-permissions --no-same-owner " : ""}-C "$1"`,
    "sc180-synthetic", dest], { input: archive });
}

for (const preserve of [false, true]) test(`actual tar fixture: ${preserve ? "preserved modes" : "old extraction rejects public modes"}`, () => {
  archiveFixture(dir => {
    const source = path.join(dir, "source");
    fs.mkdirSync(source, { mode: 0o700 });
    fs.mkdirSync(path.join(source, "src"), { mode: 0o755 });
    fs.chmodSync(path.join(source, "src"), 0o755);
    fs.writeFileSync(path.join(source, "src/file"), "synthetic\n", { mode: 0o644 });
    fs.chmodSync(path.join(source, "src/file"), 0o644);
    const archive = command("tar", ["-c", "-f", "-", "-C", source, "src"]);
    const dest = path.join(dir, "context");
    extract(archive, dest, preserve);
    assert.equal(mode(path.join(dest, "src")), preserve ? 0o755 : 0o700);
    assert.equal(mode(path.join(dest, "src/file")), preserve ? 0o644 : 0o600);
    assert.equal(mode(dir), 0o700);
    assert.equal(mode(dest), 0o700);
  });
});

test("actual Git fixture: command-scoped tar.umask and extraction both matter", () => {
  archiveFixture(dir => {
    const source = path.join(dir, "repo");
    fs.mkdirSync(source, { mode: 0o700 });
    command("git", ["init", "--quiet", source]);
    fs.mkdirSync(path.join(source, "src"), { mode: 0o755 });
    fs.writeFileSync(path.join(source, "src/file"), "synthetic\n");
    command("git", ["add", "src"], { cwd: source });
    const tree = command("git", ["write-tree"], { cwd: source }).toString().trim();
    for (const mask of ["0002", "0022"]) {
      const archive = command("git", ["-c", `tar.umask=${mask}`, "archive", tree, "src"], { cwd: source });
      for (const preserve of [false, true]) {
        const dest = path.join(dir, `${mask}-${preserve}`);
        extract(archive, dest, preserve);
        assert.equal(mode(path.join(dest, "src")), preserve ? (mask === "0022" ? 0o755 : 0o775) : 0o700);
        assert.equal(mode(path.join(dest, "src/file")), preserve ? (mask === "0022" ? 0o644 : 0o664) : 0o600);
        assert.equal(mode(dest), 0o700);
      }
    }
    assert.equal(mode(dir), 0o700);
  });
});

function sourcePreparation() {
  const start = workflow.indexOf('          git -c tar.umask=0022 archive HEAD:backend/signaling');
  const end = workflow.indexOf('          build_attempted=true', start);
  assert.ok(start >= 0 && end > start, "workflow must use reviewed command-scoped archive preparation");
  return workflow.slice(start, end).replace(/^ {10}/gm, "");
}

test("real workflow archive command and fail-closed permission guard", () => {
  const preparation = sourcePreparation();
  assert.match(preparation, /^git -c tar\.umask=0022 archive HEAD:backend\/signaling Dockerfile \.dockerignore package\.json package-lock\.json src \\\n  \| tar -x --same-permissions --no-same-owner -C "\$work\/context"\n/);
  const guard = preparation.slice(preparation.indexOf('node - "$work/context"'));
  assert.ok(guard.startsWith('node - "$work/context"'), "actual workflow guard required");
  archiveFixture(dir => {
    const work = path.join(dir, "work");
    fs.mkdirSync(work, { mode: 0o700 });
    const context = path.join(work, "context");
    fs.mkdirSync(context, { mode: 0o700 });
    const run = script => spawnSync("bash", ["-euo", "pipefail", "-c", `umask 077\n${script}`], {
      cwd: repo, env: { PATH: process.env.PATH, work }, encoding: "utf8", timeout: 10000,
    });
    let result = run(preparation);
    assert.equal(result.error, undefined);
    assert.equal(result.status, 0, result.stderr);
    for (const file of archiveFiles) assert.equal(mode(path.join(context, file)), 0o644);
    for (const subdir of archiveDirs) assert.equal(mode(path.join(context, subdir)), 0o755);
    for (const [name, incorrect, expected] of [
      ...archiveFiles.map(file => [file, 0o600, 0o644]),
      ...archiveDirs.map(subdir => [subdir, 0o700, 0o755]),
      ["package.json", 0o664, 0o644], ["src", 0o775, 0o755],
    ]) {
      fs.chmodSync(path.join(context, name), incorrect);
      assert.equal(run(guard).status, 1, `guard must reject ${name} mode ${incorrect.toString(8)}`);
      assert.equal(mode(path.join(context, name)), incorrect, "guard must not normalize modes");
      fs.chmodSync(path.join(context, name), expected);
    }
    const file = path.join(context, "package.json");
    fs.renameSync(file, `${file}.synthetic`);
    fs.symlinkSync("package.json.synthetic", file);
    assert.equal(run(guard).status, 1, "guard must reject symlinks even with matching target mode");
    fs.unlinkSync(file);
    fs.renameSync(`${file}.synthetic`, file);
    fs.rmSync(context, { recursive: true });
    fs.mkdirSync(context, { mode: 0o700 });
    result = run(preparation.replace("git -c tar.umask=0022 archive", "git archive")
      .replace("tar -x --same-permissions --no-same-owner", "tar -x"));
    assert.equal(result.status, 1, "old actual Git extraction must fail the real guard");
    assert.equal(mode(path.join(context, "package.json")), 0o600);
    assert.equal(mode(path.join(context, "src")), 0o700);
    assert.equal(mode(work), 0o700);
    assert.equal(mode(dir), 0o700);
  });
});

test("workflow line lengths stay within the configured YAML limit", () => {
  workflow.split("\n").forEach((line, index) => {
    assert.ok(line.length <= 200, `workflow line ${index + 1}: ${line.length} > 200`);
  });
});

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
