"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { alignStoreFiles, resolveDataDir, STORE_FILES } = require("../data_dir");

const root = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-data-dir-"));
const quiet = () => {};
try {
  const writable = path.join(root, "writable");
  assert.equal(resolveDataDir({ preferred: writable, env: { NODE_ENV: "production" }, warn: quiet }), writable);
  assert.deepEqual(fs.readdirSync(writable), [], "write probe must be removed");

  // A regular file where the directory should be is unwritable for every UID, root included.
  const blocked = path.join(root, "blocked");
  fs.writeFileSync(blocked, "not a directory");
  const fallback = path.join(root, "ephemeral");

  assert.throws(
    () => resolveDataDir({ preferred: blocked, env: { NODE_ENV: "production" }, fallback, warn: quiet }),
    (error) => error.code === "data_dir_not_writable"
  );
  assert.equal(fs.existsSync(fallback), false, "production must not create the ephemeral fallback");

  const warnings = [];
  assert.equal(resolveDataDir({ preferred: blocked, env: { NODE_ENV: "production", DATA_DIR_EPHEMERAL_OK: "1" },
    fallback, warn: (line) => warnings.push(line) }), fallback);
  assert.equal(warnings.length, 1);

  assert.throws(
    () => resolveDataDir({ preferred: blocked, env: { NODE_ENV: "production", DATA_DIR_EPHEMERAL_OK: "true" }, fallback, warn: quiet }),
    (error) => error.code === "data_dir_not_writable",
    "only the exact opt-in value is accepted"
  );

  const devFallback = path.join(root, "dev-ephemeral");
  assert.equal(resolveDataDir({ preferred: blocked, env: { NODE_ENV: "development" }, fallback: devFallback, warn: quiet }), devFallback);
} finally {
  fs.rmSync(root, { recursive: true, force: true });
}

// Every file-backed store is pinned into DATA_DIR.
const aligned = {};
alignStoreFiles("/srv/securecall-data", aligned);
assert.deepEqual(Object.keys(aligned).sort(), Object.keys(STORE_FILES).sort());
for (const [key, value] of Object.entries(aligned)) {
  assert.equal(path.dirname(value), "/srv/securecall-data", `${key} must live in DATA_DIR`);
}
const explicitOverride = { VLABS_FULFILLMENT_ORDERS_FILE: "/elsewhere/orders.json" };
alignStoreFiles("/srv/securecall-data", explicitOverride);
assert.equal(explicitOverride.VLABS_FULFILLMENT_ORDERS_FILE, "/srv/securecall-data/vlabs_fulfillment_orders.json");

// Regression guard: any module that reads a *_FILE variable must be listed in
// STORE_FILES, otherwise its module-relative default escapes DATA_DIR.
// The tester signer and registry are operator-provisioned private files, not
// DATA_DIR stores: they have no default path, must be absolute, and are
// validated as 0700 directory / 0600 file without symlinks or a Git checkout
// (services/tester_license_runtime.js, tester_license_registry.js). Pinning them
// via alignStoreFiles would silently relocate an existing registry.
// IDENTITY_MIGRATION_ROUTES_FILE is the same class: an operator-provisioned,
// permission-checked immutable authority (services/identity_migration_routes.js);
// its DATA_DIR default only covers the absent-file empty-routes case and an
// explicit path must survive alignment.
const NON_STORE_FILE_VARIABLES = new Set([
  "LOG_TO_FILE",
  "TURN_SECRET_FILE",
  "SECURECALL_TESTER_SIGNER_FILE",
  "SECURECALL_TESTER_REGISTRY_FILE",
  "IDENTITY_MIGRATION_ROUTES_FILE",
]);
const sourceRoot = path.join(__dirname, "..");
function sources(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === "__tests__" ? [] : sources(full);
    return entry.name.endsWith(".js") ? [full] : [];
  });
}
// Every static way a module can read a *_FILE variable: env.X_FILE,
// env["X_FILE"] (any quote style) and destructuring from (process.)env,
// including renamed and defaulted bindings. A fully dynamic env[name] cannot
// be resolved statically and is not used for file stores.
function fileVariableReads(source) {
  const names = new Set();
  for (const match of source.matchAll(/\benv\s*(?:\.\s*([A-Z0-9_]+_FILE)\b|\[\s*(["'`])([A-Z0-9_]+_FILE)\2\s*\])/g)) {
    names.add(match[1] || match[3]);
  }
  for (const match of source.matchAll(/\{([^{}]*)\}\s*=\s*(?:process\s*\.\s*)?env\b/g)) {
    for (const binding of match[1].split(",")) {
      const key = binding.trim().replace(/^\.\.\./, "").split(/[:=]/)[0].trim().replace(/^(["'`])(.*)\1$/, "$2");
      if (/^[A-Z0-9_]+_FILE$/.test(key)) names.add(key);
    }
  }
  return [...names];
}

for (const [snippet, expected] of [
  ["const a = process.env.ALPHA_FILE;", ["ALPHA_FILE"]],
  ["const a = env.ALPHA_FILE || x;", ["ALPHA_FILE"]],
  ["const a = process.env[\"BRAVO_FILE\"];", ["BRAVO_FILE"]],
  ["const a = env['CHARLIE_FILE'];", ["CHARLIE_FILE"]],
  ["const a = env[`DELTA_FILE`];", ["DELTA_FILE"]],
  ["const { ECHO_FILE } = process.env;", ["ECHO_FILE"]],
  ["const { FOX_FILE: fox, GOLF_FILE = '/tmp/x', OTHER } = env;", ["FOX_FILE", "GOLF_FILE"]],
  ["let { 'HOTEL_FILE': h } = process.env", ["HOTEL_FILE"]],
  ["const { a, b } = config; const c = env.PORT;", []],
  ["const x = env[name];", []],
  ["const { INDIA_FILE } = settings;", []],
]) {
  assert.deepEqual(fileVariableReads(snippet).sort(), expected.sort(), snippet);
}

const unaligned = [];
for (const file of sources(sourceRoot)) {
  for (const name of fileVariableReads(fs.readFileSync(file, "utf8"))) {
    if (!NON_STORE_FILE_VARIABLES.has(name) && !Object.hasOwn(STORE_FILES, name)) {
      unaligned.push(`${path.relative(sourceRoot, file)}: ${name}`);
    }
  }
}
assert.deepEqual(unaligned, [], `file stores outside DATA_DIR alignment:\n${unaligned.join("\n")}`);

console.log("data_dir.test.js: PASS");
