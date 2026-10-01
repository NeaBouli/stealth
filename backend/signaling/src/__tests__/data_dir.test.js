"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { resolveDataDir } = require("../data_dir");

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

console.log("data_dir.test.js: PASS");
