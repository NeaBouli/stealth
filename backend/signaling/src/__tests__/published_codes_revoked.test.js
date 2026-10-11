"use strict";

// STX-09: activation codes that were published in the public repo are never honoured.
// The codes are read from Git history when available, so they are not repeated here.
const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync } = require("child_process");

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-published-codes-"));
process.env.CODES_FILE = path.join(dir, "activation_codes.json");
process.env.SOLD_CODES_FILE = path.join(dir, "sold_codes.json");

let published = null;
try {
  const raw = execFileSync("git", ["show", "c02146a8:backend/signaling/data/activation_codes.json"],
    { cwd: path.join(__dirname, ".."), encoding: "utf8", stdio: ["ignore", "pipe", "ignore"] });
  published = JSON.parse(raw).codes;
} catch {
  console.log("published_codes_revoked.test.js: SKIP history check (shallow checkout)");
}

const store = require("../services/activation_store");
assert.strictEqual(store.isPublishedCode("SYNTHETIC-CODE-0001"), false);
assert.strictEqual(store.isPublishedCode(undefined), false);

if (published) {
  assert.strictEqual(published.length, 3);
  for (const entry of published) {
    assert.ok(store.isPublishedCode(entry.code), "published code is recognised");
    assert.ok(store.isPublishedCode(` ${entry.code.toLowerCase()} `), "normalised before matching");
  }
  // From the file and from the env seed: both load as tombstones; other codes stay usable.
  fs.writeFileSync(process.env.CODES_FILE, JSON.stringify({ codes: [
    published[0], { code: "SYNTHETIC-CODE-0001", tier: "pro", maxUses: 1, currentUses: 0 },
  ] }));
  process.env.SEED_ACTIVATION_CODES = JSON.stringify([published[1]]);
  store.loadActivationCodes();
  for (const entry of [published[0], published[1]]) {
    const loaded = store.activationCodes.find(item => item.code === entry.code);
    assert.ok(!loaded || (loaded.revoked === true && loaded.tier === undefined), "published code loads revoked");
  }
  assert.ok(store.activationCodes.some(item => item.code === "SYNTHETIC-CODE-0001" && !item.revoked));
  delete process.env.SEED_ACTIVATION_CODES;
}

fs.rmSync(dir, { recursive: true, force: true });
console.log("published_codes_revoked.test.js: PASS");
