"use strict";

const assert = require("assert");
const crypto = require("crypto");
const protocol = require("../security/identity_protocol");

const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const publicKey = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
const parsed = protocol.parseIdentityPublicKey(publicKey);
const configNow = 2_000_000_000;

assert.deepStrictEqual(protocol.readIdentityProtocolConfig({}, configNow), {
  mode: "enforce", transitionDeadline: null,
});
assert.deepStrictEqual(protocol.readIdentityProtocolConfig({
  IDENTITY_PROTOCOL_MODE: "TRANSITION",
  IDENTITY_TRANSITION_DEADLINE_EPOCH_SECONDS: String(configNow + 3600),
}, configNow), { mode: "transition", transitionDeadline: configNow + 3600 });
assert.throws(() => protocol.readIdentityProtocolConfig({
  IDENTITY_PROTOCOL_MODE: "transition",
}, configNow), error => error.code === "invalid_identity_transition_deadline");
assert.throws(() => protocol.readIdentityProtocolConfig({
  IDENTITY_PROTOCOL_MODE: "transition",
  IDENTITY_TRANSITION_DEADLINE_EPOCH_SECONDS: String(configNow),
}, configNow), error => error.code === "invalid_identity_transition_deadline");
assert.throws(() => protocol.readIdentityProtocolConfig({
  IDENTITY_PROTOCOL_MODE: "transition",
  IDENTITY_TRANSITION_DEADLINE_EPOCH_SECONDS:
    String(configNow + protocol.MAX_TRANSITION_WINDOW_SECONDS + 1),
}, configNow), error => error.code === "invalid_identity_transition_deadline");
assert.throws(() => protocol.readIdentityProtocolConfig({
  IDENTITY_PROTOCOL_MODE: "unknown",
}, configNow), error => error.code === "invalid_identity_protocol_mode");
assert.strictEqual(protocol.isLegacyTransitionActive(
  "transition", configNow + 1, configNow,
), true);
assert.strictEqual(protocol.isLegacyTransitionActive(
  "transition", configNow - 1, configNow,
), false);
assert.strictEqual(protocol.isLegacyTransitionActive("enforce", configNow + 1, configNow), false);

assert.match(parsed.identityId, /^sc-[A-Za-z0-9_-]{43}$/);
assert.strictEqual(parsed.publicKeyBase64Url, publicKey);
assert.strictEqual(parsed.identityId, `sc-${parsed.keyHash}`);

const challenge = {
  challengeId: crypto.randomUUID(),
  requestedClientId: "android-legacy01",
  identityId: parsed.identityId,
  keyHash: parsed.keyHash,
  expiresAt: 2_000_000_000,
  nonce: crypto.randomBytes(32).toString("base64url"),
};
const registration = protocol.registrationTranscript(challenge);
const signature = crypto.sign("sha256", Buffer.from(registration), pair.privateKey).toString("base64url");
assert.strictEqual(protocol.verifySignature(parsed.publicKey, registration, signature), true);
assert.strictEqual(protocol.verifySignature(parsed.publicKey, `${registration}x`, signature), false);

const invite = protocol.callInviteTranscript({
  sessionId: crypto.randomUUID(),
  fromClientId: "android-legacy01",
  fromIdentityId: parsed.identityId,
  to: "android-peer01",
  ephemeralPublicKey: crypto.randomBytes(32).toString("base64url"),
  issuedAt: 2_000_000_000,
  nonce: crypto.randomBytes(24).toString("base64url"),
});
const inviteDigest = protocol.transcriptDigest(invite);
assert.match(inviteDigest, /^[A-Za-z0-9_-]{43}$/);

const peer = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
const peerParsed = protocol.parseIdentityPublicKey(
  peer.publicKey.export({ type: "spki", format: "der" }).toString("base64url")
);
const accept = protocol.callAcceptTranscript({
  sessionId: invite.split("\n")[1],
  inviteDigest,
  fromClientId: "android-peer01",
  fromIdentityId: peerParsed.identityId,
  toIdentityId: parsed.identityId,
  ephemeralPublicKey: crypto.randomBytes(32).toString("base64url"),
  issuedAt: 2_000_000_001,
  nonce: crypto.randomBytes(24).toString("base64url"),
});
assert.notStrictEqual(protocol.transcriptDigest(accept), inviteDigest);
assert.strictEqual(protocol.validateIssuedAt(1000, 1119), true);
assert.strictEqual(protocol.validateIssuedAt(1000, 1121), false);

assert.throws(() => protocol.parseIdentityPublicKey("not-a-key"));
assert.throws(() => protocol.registrationTranscript({ ...challenge, requestedClientId: "bad\nvalue" }));
assert.throws(() => protocol.callInviteTranscript({
  sessionId: crypto.randomUUID(), fromClientId: "a", fromIdentityId: parsed.identityId,
  to: "b", ephemeralPublicKey: "short", issuedAt: 1, nonce: challenge.nonce,
}));

console.log("identity_protocol.test.js: PASS");
