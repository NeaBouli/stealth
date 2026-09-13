"use strict";

// Codec only. Enrollment/issuance authorization belongs to the caller; never
// expose this signer directly to a client-supplied key or claimed enrollment.
const crypto = require("crypto");
const TTL_SECONDS = 30 * 24 * 60 * 60;
const PREFIX = "sct1";
const KEYS = ["v", "iss", "aud", "sub", "pkg", "tier", "grant", "device", "iat", "exp"];
const safeId = value => typeof value === "string" && /^[A-Za-z0-9_.:-]{1,160}$/.test(value);
const hash = value => typeof value === "string" && /^[a-f0-9]{64}$/.test(value);

function invalid() { return new Error("Invalid tester entitlement"); }
function validate(c, now, grace = 0) {
  if (!Number.isSafeInteger(now) || now <= 0 || now > Number.MAX_SAFE_INTEGER - TTL_SECONDS
      || !Number.isSafeInteger(grace) || grace < 0 || grace > 7 * 86400) throw invalid();
  if (Object.keys(c).join(",") !== KEYS.join(",") || c.v !== "1" || c.iss !== "stealthx"
      || c.aud !== "securecall-tester" || c.pkg !== "com.securecall.app.premium"
      || c.tier !== "PREMIUM" || !safeId(c.sub) || !hash(c.grant) || !hash(c.device)
      || !/^[1-9][0-9]*$/.test(c.iat) || !/^[1-9][0-9]*$/.test(c.exp)) throw invalid();
  const iat = Number(c.iat), exp = Number(c.exp);
  if (!Number.isSafeInteger(iat) || !Number.isSafeInteger(exp)
      || exp > Number.MAX_SAFE_INTEGER - 7 * 86400 || iat > now + 300
      || exp <= iat || exp - iat > TTL_SECONDS || exp + grace <= now) throw invalid();
}

function decode(value) {
  if (!/^[A-Za-z0-9_-]+$/.test(value)) throw invalid();
  const bytes = Buffer.from(value, "base64url");
  if (bytes.toString("base64url") !== value) throw invalid();
  return bytes;
}

function issueTesterEntitlement({ subject, grantHash, deviceKeyHash, privateKey,
  nowSeconds = Math.floor(Date.now() / 1000) }) {
  const key = crypto.createPrivateKey(privateKey);
  if (key.asymmetricKeyType !== "ed25519") throw invalid();
  const c = { v: "1", iss: "stealthx", aud: "securecall-tester", sub: subject,
    pkg: "com.securecall.app.premium", tier: "PREMIUM", grant: grantHash,
    device: deviceKeyHash, iat: String(nowSeconds), exp: String(nowSeconds + TTL_SECONDS) };
  validate(c, nowSeconds);
  const payload = Buffer.from(KEYS.map(k => `${k}=${c[k]}`).join("\n")).toString("base64url");
  const signed = `${PREFIX}.${payload}`;
  return `${signed}.${crypto.sign(null, Buffer.from(signed, "ascii"), key).toString("base64url")}`;
}

function verifyTesterEntitlement(token, { subject, deviceKeyHash, publicKey,
  nowSeconds = Math.floor(Date.now() / 1000), expiryGraceSeconds = 0 }) {
  if (typeof token !== "string" || token.length > 4096 || !safeId(subject) || !hash(deviceKeyHash)) throw invalid();
  const parts = token.split(".");
  if (parts.length !== 3 || parts[0] !== PREFIX) throw invalid();
  const payload = decode(parts[1]), signature = decode(parts[2]);
  const key = crypto.createPublicKey(publicKey);
  if (key.asymmetricKeyType !== "ed25519" || signature.length !== 64
      || !crypto.verify(null, Buffer.from(`${PREFIX}.${parts[1]}`, "ascii"), key, signature)) throw invalid();
  if (payload.some(byte => byte > 127)) throw invalid();
  const c = {};
  for (const line of payload.toString("ascii").split("\n")) {
    const i = line.indexOf("=");
    if (i <= 0 || Object.hasOwn(c, line.slice(0, i))) throw invalid();
    Object.defineProperty(c, line.slice(0, i), { value: line.slice(i + 1), enumerable: true });
  }
  validate(c, nowSeconds, expiryGraceSeconds);
  if (c.sub !== subject || c.device !== deviceKeyHash) throw invalid();
  return Object.freeze(c);
}

module.exports = { issueTesterEntitlement, verifyTesterEntitlement, TTL_SECONDS };
