"use strict";
const assert = require("assert/strict");
const crypto = require("crypto");
const { issueTesterEntitlement: issue, verifyTesterEntitlement: verify, TTL_SECONDS } = require("../payments/tester_entitlement_tokens");
const pair = crypto.generateKeyPairSync("ed25519");
const privateKey = pair.privateKey.export({ type: "pkcs8", format: "pem" });
const publicKey = pair.publicKey.export({ type: "spki", format: "pem" });
const now = 1700000000;
const device = "a".repeat(64), grant = "b".repeat(64);
const request = { subject: "synthetic-device-A", grantHash: grant, deviceKeyHash: device, privateKey, nowSeconds: now };
const expected = { subject: request.subject, deviceKeyHash: device, publicKey, nowSeconds: now };
const token = issue(request);
assert.equal(verify(token, expected).grant, grant);
assert.equal(verify(token, expected).tier, "PREMIUM");
assert.throws(() => verify(token, { ...expected, subject: "synthetic-device-B" }));
assert.throws(() => verify(token, { ...expected, deviceKeyHash: "c".repeat(64) }));
assert.throws(() => verify(token, { ...expected, nowSeconds: now + TTL_SECONDS }));
assert.equal(verify(token, { ...expected, nowSeconds: now + TTL_SECONDS, expiryGraceSeconds: 1 }).grant, grant);
assert.throws(() => verify(token, { ...expected, expiryGraceSeconds: 7 * 86400 + 1 }));
assert.throws(() => verify(token, { ...expected, nowSeconds: now - 301 }));
assert.throws(() => verify(token, { ...expected, nowSeconds: NaN }));
assert.throws(() => verify(`${token}=`, expected));
assert.throws(() => verify(token.replace("sct1.", "sct2."), expected));
assert.throws(() => verify("x".repeat(4097), expected));
const other = crypto.generateKeyPairSync("ed25519");
assert.throws(() => verify(token, { ...expected, publicKey: other.publicKey.export({ type: "spki", format: "pem" }) }));
function altered(transform) {
  const payload = Buffer.from(transform(Buffer.from(token.split(".")[1], "base64url").toString("ascii"))).toString("base64url");
  const signed = `sct1.${payload}`;
  return `${signed}.${crypto.sign(null, Buffer.from(signed), pair.privateKey).toString("base64url")}`;
}
for (const transform of [
  s => s.replace("com.securecall.app.premium", "com.securecall.app.free"),
  s => s.replace("securecall-tester", "securecall"),
  s => s.replace("tier=PREMIUM", "tier=PRO"),
  s => s + "\nv=1",
  s => s + "\nunknown=1",
  s => s.replace(`iat=${now}`, "iat=1e9"),
  s => s.replace(`exp=${now + TTL_SECONDS}`, `exp=${now + TTL_SECONDS + 1}`),
]) assert.throws(() => verify(altered(transform), expected));
assert.throws(() => issue({ ...request, subject: "bad\nclaim" }));
assert.throws(() => issue({ ...request, deviceKeyHash: "unknown" }));
const refreshed = issue({ ...request, nowSeconds: now + TTL_SECONDS });
assert.equal(verify(refreshed, { ...expected, nowSeconds: now + TTL_SECONDS }).grant, grant);
let response;
const handlers = require("../ws/handlers/subscription")({
  getClientId: () => request.subject,
  verifyEntitlementToken: () => { throw new Error("Tester proof reached commercial verifier"); },
});
const requestId = crypto.randomUUID();
handlers.REFRESH_ENTITLEMENT({ send: value => { response = JSON.parse(value); } }, "synthetic", {
  requestId, entitlementToken: token,
});
assert.equal(response.requestId, requestId);
assert.equal(response.error, "tester_enrollment_unavailable");
assert.equal(response.success, false);
assert.equal(response.entitlementToken, undefined);
console.log("tester_entitlement_tokens: all synthetic protocol assertions PASS");
