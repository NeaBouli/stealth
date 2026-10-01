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
const NON_STORE_FILE_VARIABLES = new Set(["LOG_TO_FILE", "TURN_SECRET_FILE"]);
const sourceRoot = path.join(__dirname, "..");
function sources(directory) {
  return fs.readdirSync(directory, { withFileTypes: true }).flatMap((entry) => {
    const full = path.join(directory, entry.name);
    if (entry.isDirectory()) return entry.name === "__tests__" ? [] : sources(full);
    return entry.name.endsWith(".js") ? [full] : [];
  });
}
const unaligned = [];
for (const file of sources(sourceRoot)) {
  for (const match of fs.readFileSync(file, "utf8").matchAll(/\benv\.([A-Z0-9_]+_FILE)\b/g)) {
    if (!NON_STORE_FILE_VARIABLES.has(match[1]) && !Object.hasOwn(STORE_FILES, match[1])) {
      unaligned.push(`${path.relative(sourceRoot, file)}: ${match[1]}`);
    }
  }
}
assert.deepEqual(unaligned, [], `file stores outside DATA_DIR alignment:\n${unaligned.join("\n")}`);

console.log("data_dir.test.js: PASS");
