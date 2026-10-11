"use strict";

// STX-06: Custom-ID transfer password hardening. Synthetic data, temp dir only.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");

const dir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-custom-id-pw-"));
process.env.IDS_FILE = path.join(dir, "ids.json");
process.env.PENDING_FILE = path.join(dir, "pending.json");
delete process.env.ID_HASH_PEPPER; // storage key = normalized id

// Build the record with the module's own hashing so the stored format is realistic.
const salt = crypto.randomBytes(16).toString("hex");
const hash = crypto.pbkdf2Sync("correct horse 1", salt, 100000, 64, "sha512").toString("hex");
fs.writeFileSync(process.env.IDS_FILE, JSON.stringify({
  "owned-id": { deviceId: "device-owner", passwordHash: hash, passwordSalt: salt },
  "other-id": { deviceId: "device-other", passwordHash: hash, passwordSalt: salt },
}));
fs.writeFileSync(process.env.PENDING_FILE, "{}");

const customIds = require("../custom_ids");
const { activate, hashPassword, setupRoutes } = customIds;

assert.strictEqual(hashPassword("correct horse 1", salt).hash, hash, "test record matches module hashing");

// Type and length bounds.
assert.strictEqual(activate("owned-id", "dev-x", ["correct horse 1"]).error, "invalid_password");
assert.strictEqual(activate("owned-id", "dev-x", "x".repeat(257)).error, "invalid_password");
assert.strictEqual(activate("owned-id", "dev-x", "short").error, "invalid_password");

// Correct password transfers; wrong passwords are counted per ID.
assert.deepStrictEqual(activate("owned-id", "dev-new", "correct horse 1"), { success: true, transferred: true });
for (let i = 0; i < 5; i++) {
  assert.strictEqual(activate("owned-id", "dev-attacker", `wrong-guess-${i}`).error, "wrong_password");
}
// After 5 failures even the correct password is refused for this ID (distributed guessing).
assert.strictEqual(activate("owned-id", "dev-attacker", "correct horse 1").error, "rate_limited");
// Other IDs are unaffected.
assert.deepStrictEqual(activate("other-id", "dev-other2", "correct horse 1"), { success: true, transferred: true });

// The locked attempts never moved the ID.
const stored = JSON.parse(fs.readFileSync(process.env.IDS_FILE, "utf8"));
assert.strictEqual(stored["owned-id"].deviceId, "dev-new", "locked attempts did not transfer");

// HTTP route: client address comes from getClientIp (TRUST_PROXY contract), not req.ip.
const routes = {};
const app = { get() {}, post(route, ...handlers) { routes[route] = handlers; } };
setupRoutes(app, (req, res, next) => next());
const [limiter, handler] = routes["/custom-id/activate"];
const call = (ip, body) => {
  const res = { code: 200, body: null, status(c) { this.code = c; return this; }, json(b) { this.body = b; return this; } };
  const req = { body, ip: "10.0.0.1", headers: {}, socket: { remoteAddress: ip }, connection: { remoteAddress: ip } };
  let passed = false;
  limiter(req, res, () => { passed = true; });
  if (passed) handler(req, res);
  return res;
};
for (let i = 0; i < 5; i++) call("198.51.100.7", { id: "nope-id", deviceId: "d", password: "password-123" });
assert.strictEqual(call("198.51.100.7", { id: "nope-id", deviceId: "d", password: "password-123" }).code, 429);
assert.notStrictEqual(call("198.51.100.8", { id: "nope-id", deviceId: "d", password: "password-123" }).code, 429,
  "a different client behind the same proxy hop is not throttled by req.ip");
assert.strictEqual(call("198.51.100.9", { id: "x", deviceId: "d", password: { length: 9 } }).body.error, "invalid_password");

fs.rmSync(dir, { recursive: true, force: true });
console.log("custom_id_password.test.js: PASS");
