"use strict";

const crypto = require("crypto");

const CLIENT_ID = /^[A-Za-z0-9_-]{1,64}$/;
const IDENTITY_ID = /^sc-[A-Za-z0-9_-]{43}$/;
const BASE64URL = /^[A-Za-z0-9_-]+$/;
const UUID = /^[a-f0-9]{8}-[a-f0-9]{4}-[1-5][a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/;
const SIGNATURE = /^[A-Za-z0-9_-]{64,144}$/;
const X25519_PUBLIC_KEY = /^[A-Za-z0-9_-]{43}$/;
const NONCE = /^[A-Za-z0-9_-]{22,64}$/;
const MAX_TRANSITION_WINDOW_SECONDS = 14 * 24 * 60 * 60;

function fail(code = "invalid_identity_protocol") {
  const error = new Error(code);
  error.code = code;
  throw error;
}

function readIdentityProtocolConfig(env = process.env, nowSeconds = Math.floor(Date.now() / 1000)) {
  const mode = String(env.IDENTITY_PROTOCOL_MODE || "enforce").toLowerCase();
  if (!new Set(["transition", "enforce"]).has(mode)) fail("invalid_identity_protocol_mode");
  if (mode === "enforce") return { mode, transitionDeadline: null };

  const rawDeadline = env.IDENTITY_TRANSITION_DEADLINE_EPOCH_SECONDS;
  if (typeof rawDeadline !== "string" || !/^[0-9]+$/.test(rawDeadline)) {
    fail("invalid_identity_transition_deadline");
  }
  const transitionDeadline = Number(rawDeadline);
  if (!Number.isSafeInteger(nowSeconds) || !Number.isSafeInteger(transitionDeadline)
      || transitionDeadline <= nowSeconds
      || transitionDeadline > nowSeconds + MAX_TRANSITION_WINDOW_SECONDS) {
    fail("invalid_identity_transition_deadline");
  }
  return { mode, transitionDeadline };
}

function isLegacyTransitionActive(mode, transitionDeadline, nowSeconds) {
  return mode === "transition"
    && Number.isSafeInteger(transitionDeadline)
    && Number.isSafeInteger(nowSeconds)
    && nowSeconds <= transitionDeadline;
}

function canonicalValue(value, maxLength = 512) {
  if (typeof value !== "string" || value.length < 1 || value.length > maxLength
      || /[\r\n\0]/.test(value)) fail();
  return value;
}

function canonicalTranscript(value) {
  if (typeof value !== "string" || value.length < 1 || value.length > 4096
      || value.includes("\0") || !value.startsWith("securecall-")) fail();
  return value;
}

function decodeBase64Url(value, minLength, maxLength) {
  if (typeof value !== "string" || !BASE64URL.test(value)) fail();
  let decoded;
  try { decoded = Buffer.from(value, "base64url"); } catch { fail(); }
  if (decoded.length < minLength || decoded.length > maxLength
      || decoded.toString("base64url") !== value) fail();
  return decoded;
}

function parseIdentityPublicKey(value) {
  const der = decodeBase64Url(value, 80, 160);
  let publicKey;
  try {
    publicKey = crypto.createPublicKey({ key: der, type: "spki", format: "der" });
  } catch { fail("invalid_identity_public_key"); }
  const canonical = publicKey.export({ type: "spki", format: "der" });
  if (publicKey.asymmetricKeyType !== "ec"
      || publicKey.asymmetricKeyDetails?.namedCurve !== "prime256v1"
      || !Buffer.isBuffer(canonical) || !canonical.equals(der)) {
    fail("invalid_identity_public_key");
  }
  const keyHash = crypto.createHash("sha256").update(canonical).digest("base64url");
  return {
    publicKey,
    publicKeyPem: publicKey.export({ type: "spki", format: "pem" }),
    publicKeyBase64Url: canonical.toString("base64url"),
    keyHash,
    identityId: `sc-${keyHash}`,
  };
}

function registrationTranscript({ challengeId, requestedClientId, identityId, keyHash, expiresAt, nonce }) {
  if (!UUID.test(challengeId) || !CLIENT_ID.test(requestedClientId)
      || !IDENTITY_ID.test(identityId) || !BASE64URL.test(keyHash) || keyHash.length !== 43
      || !Number.isSafeInteger(expiresAt) || expiresAt <= 0 || !NONCE.test(nonce)) fail();
  return [
    "securecall-registration-v2",
    challengeId,
    requestedClientId,
    identityId,
    keyHash,
    String(expiresAt),
    nonce,
  ].join("\n");
}

function callInviteTranscript({ sessionId, fromClientId, fromIdentityId, to, ephemeralPublicKey,
  issuedAt, nonce }) {
  if (!UUID.test(sessionId) || !CLIENT_ID.test(fromClientId) || !IDENTITY_ID.test(fromIdentityId)
      || !CLIENT_ID.test(to) || !X25519_PUBLIC_KEY.test(ephemeralPublicKey)
      || !Number.isSafeInteger(issuedAt) || issuedAt <= 0 || !NONCE.test(nonce)) fail();
  return [
    "securecall-call-invite-v2",
    sessionId,
    fromClientId,
    fromIdentityId,
    to,
    ephemeralPublicKey,
    String(issuedAt),
    nonce,
  ].join("\n");
}

function callAcceptTranscript({ sessionId, inviteDigest, fromClientId, fromIdentityId,
  toIdentityId, ephemeralPublicKey, issuedAt, nonce }) {
  if (!UUID.test(sessionId) || !BASE64URL.test(inviteDigest) || inviteDigest.length !== 43
      || !CLIENT_ID.test(fromClientId) || !IDENTITY_ID.test(fromIdentityId)
      || !IDENTITY_ID.test(toIdentityId) || !X25519_PUBLIC_KEY.test(ephemeralPublicKey)
      || !Number.isSafeInteger(issuedAt) || issuedAt <= 0 || !NONCE.test(nonce)) fail();
  return [
    "securecall-call-accept-v2",
    sessionId,
    inviteDigest,
    fromClientId,
    fromIdentityId,
    toIdentityId,
    ephemeralPublicKey,
    String(issuedAt),
    nonce,
  ].join("\n");
}

function transcriptDigest(transcript) {
  return crypto.createHash("sha256").update(canonicalTranscript(transcript), "utf8").digest("base64url");
}

function verifySignature(publicKey, transcript, signature) {
  if (!publicKey || !SIGNATURE.test(signature)) return false;
  let bytes;
  try { bytes = Buffer.from(signature, "base64url"); } catch { return false; }
  if (bytes.toString("base64url") !== signature) return false;
  try {
    return crypto.verify("sha256", Buffer.from(canonicalTranscript(transcript), "utf8"), publicKey, bytes);
  } catch { return false; }
}

function validateIssuedAt(issuedAt, nowSeconds, maxSkewSeconds = 120) {
  return Number.isSafeInteger(issuedAt) && Number.isSafeInteger(nowSeconds)
    && Math.abs(nowSeconds - issuedAt) <= maxSkewSeconds;
}

module.exports = {
  CLIENT_ID,
  IDENTITY_ID,
  NONCE,
  UUID,
  X25519_PUBLIC_KEY,
  MAX_TRANSITION_WINDOW_SECONDS,
  readIdentityProtocolConfig,
  isLegacyTransitionActive,
  parseIdentityPublicKey,
  registrationTranscript,
  callInviteTranscript,
  callAcceptTranscript,
  transcriptDigest,
  verifySignature,
  validateIssuedAt,
};
