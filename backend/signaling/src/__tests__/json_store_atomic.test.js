"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-json-store-"));
const target = path.join(directory, "activation_codes.json");
const originalChmodSync = fs.chmodSync;
let postCommitChmodAttempted = false;

fs.chmodSync = (...args) => {
  postCommitChmodAttempted = true;
  return originalChmodSync(...args);
};

try {
  assert.doesNotThrow(() => writeJsonAtomic(target, { codes: [{ code: "TEST" }] }));
} finally {
  fs.chmodSync = originalChmodSync;
}

assert.strictEqual(postCommitChmodAttempted, false, "atomic writer performs no fallible chmod after commit");
assert.deepStrictEqual(JSON.parse(fs.readFileSync(target, "utf8")), { codes: [{ code: "TEST" }] });
assert.strictEqual(fs.statSync(target).mode & 0o777, 0o600);

fs.rmSync(directory, { recursive: true, force: true });
console.log("json_store_atomic.test.js ok");
