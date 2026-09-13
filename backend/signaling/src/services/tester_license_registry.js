"use strict";

const fs = require("fs");
const path = require("path");
const crypto = require("crypto");
const { issueTesterEntitlement, verifyTesterEntitlement } = require("../payments/tester_entitlement_tokens");
const HEX = /^[a-f0-9]{64}$/;
const SUBJECT = /^[A-Za-z0-9_-]{1,64}$/;
const PACKAGE = "com.securecall.app.premium";
const matches = (pattern, value) => typeof value === "string" && pattern.test(value);
function fail() { throw new Error("tester_license_unavailable"); }
function digest(value) { return crypto.createHash("sha256").update(value).digest("hex"); }

function privatePath(file, directory = false) {
  if (!path.isAbsolute(file)) fail();
  for (let part = file; ; part = path.dirname(part)) {
    if (fs.lstatSync(part).isSymbolicLink() || fs.existsSync(path.join(part, ".git"))) fail();
    if (path.dirname(part) === part) break;
  }
  const stat = fs.lstatSync(file);
  if (stat.uid !== process.getuid() || (stat.mode & 0o777) !== (directory ? 0o700 : 0o600)
      || (directory ? !stat.isDirectory() : !stat.isFile() || stat.nlink !== 1)) fail();
}

function validate(data) {
  if (data?.schema !== 1 || !Array.isArray(data.grants) || !Array.isArray(data.enrolledKeys)) fail();
  const ids = new Set(), codes = new Set(), keys = new Set();
  for (const grant of data.grants) {
    if (!grant || !matches(HEX, grant.id) || !matches(HEX, grant.codeHash) || ids.has(grant.id) || codes.has(grant.codeHash)
        || !["inactive", "active", "revoked"].includes(grant.status)
        || (grant.binding !== null && (!matches(HEX, grant.binding?.keyHash) || !matches(SUBJECT, grant.binding?.subject)))) fail();
    ids.add(grant.id); codes.add(grant.codeHash);
  }
  // These records must originate from the operator's verified attestation process.
  // There is deliberately no API here accepting enrollment approval from a client.
  for (const key of data.enrolledKeys) {
    if (!key || !matches(HEX, key.hash) || keys.has(key.hash) || key.package !== PACKAGE
        || key.assurance !== "hardware-verified" || !["active", "revoked"].includes(key.status)
        || typeof key.publicKeyPem !== "string" || key.publicKeyPem.length > 2048) fail();
    const publicKey = crypto.createPublicKey(key.publicKeyPem);
    if (publicKey.asymmetricKeyType !== "ec" || publicKey.asymmetricKeyDetails?.namedCurve !== "prime256v1"
        || digest(publicKey.export({ type: "spki", format: "der" })) !== key.hash) fail();
    keys.add(key.hash);
  }
  return data;
}

function createTesterLicenseRegistry({ file, privateKey, now = () => Math.floor(Date.now() / 1000) }) {
  privatePath(path.dirname(file), true);
  privatePath(file);
  const signer = crypto.createPrivateKey(privateKey);
  if (signer.asymmetricKeyType !== "ed25519") fail();
  const verifier = crypto.createPublicKey(signer).export({ type: "spki", format: "pem" });
  const challenges = new Map();
  function read() {
    try {
      privatePath(file);
      if (fs.statSync(file).size > 5 * 1024 * 1024) fail();
      return validate(JSON.parse(fs.readFileSync(file, "utf8")));
    } catch { fail(); }
  }
  function transaction(change) {
    privatePath(path.dirname(file), true);
    const lock = `${file}.lock`;
    let fd;
    try { fd = fs.openSync(lock, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600); }
    catch { fail(); }
    let temporary;
    try {
      const state = read();
      const result = change(state);
      validate(state);
      temporary = `${file}.${crypto.randomUUID()}.tmp`;
      const output = fs.openSync(temporary, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
      try { fs.writeFileSync(output, JSON.stringify(state)); fs.fsyncSync(output); }
      finally { fs.closeSync(output); }
      // All runtime writers use this lock. No stale-lock recovery is automatic.
      const held = fs.fstatSync(fd), current = fs.lstatSync(lock);
      if (held.dev !== current.dev || held.ino !== current.ino) fail();
      fs.renameSync(temporary, file);
      temporary = undefined;
      const dir = fs.openSync(path.dirname(file), fs.constants.O_RDONLY);
      try { fs.fsyncSync(dir); } finally { fs.closeSync(dir); }
      return result;
    } catch { fail(); }
    finally {
      if (temporary) { try { fs.unlinkSync(temporary); } catch { /* A failed write stays fail-closed. */ } }
      try {
        const held = fs.fstatSync(fd), current = fs.lstatSync(lock);
        if (held.dev === current.dev && held.ino === current.ino) fs.unlinkSync(lock);
      } finally { fs.closeSync(fd); }
    }
  }
  function records(state, codeHash, keyHash) {
    const grant = state.grants.find(g => g.codeHash === codeHash && g.status === "active");
    const key = state.enrolledKeys.find(k => k.hash === keyHash && k.status === "active");
    if (!grant || !key) fail();
    return { grant, key };
  }
  function begin({ code, subject, keyHash, packageName }) {
    if (typeof code !== "string" || !/^SC-PREM-[A-F0-9]{48}$/.test(code)
        || !matches(SUBJECT, subject) || !matches(HEX, keyHash) || packageName !== PACKAGE) fail();
    const codeHash = digest(code);
    const { grant } = records(read(), codeHash, keyHash);
    if (grant.binding && (grant.binding.keyHash !== keyHash || grant.binding.subject !== subject)) fail();
    const time = now();
    for (const [id, record] of challenges) {
      if (record.expires <= time || record.subject === subject) challenges.delete(id);
    }
    if (challenges.size >= 256) fail();
    const id = crypto.randomUUID();
    const challenge = `securecall-tester-activation-v1\n${id}\n${grant.id}\n${subject}\n${keyHash}\n${crypto.randomBytes(32).toString("base64url")}`;
    challenges.set(id, { kind: "activate", codeHash, subject, keyHash, challenge, expires: time + 300 });
    return { challengeId: id, challenge };
  }
  function activate({ challengeId, signature, subject }) {
    const pending = challenges.get(challengeId);
    challenges.delete(challengeId);
    if (!pending || pending.kind !== "activate" || pending.expires <= now() || pending.subject !== subject
        || typeof signature !== "string" || !/^[A-Za-z0-9_-]{64,144}$/.test(signature)) fail();
    return transaction(state => {
      const { grant, key } = records(state, pending.codeHash, pending.keyHash);
      const bytes = Buffer.from(signature, "base64url");
      if (bytes.toString("base64url") !== signature
          || !crypto.verify("sha256", Buffer.from(pending.challenge), key.publicKeyPem, bytes)) fail();
      if (grant.binding && (grant.binding.subject !== subject || grant.binding.keyHash !== key.hash)) fail();
      grant.binding = { subject, keyHash: key.hash };
      return issueTesterEntitlement({ subject, grantHash: grant.id, deviceKeyHash: key.hash, privateKey, nowSeconds: now() });
    });
  }
  function beginRefresh({ token, subject, keyHash }) {
    verifyTesterEntitlement(token, { subject, deviceKeyHash: keyHash, publicKey: verifier,
      nowSeconds: now(), expiryGraceSeconds: 7 * 86400 });
    const time = now();
    for (const [id, record] of challenges) {
      if (record.expires <= time || record.subject === subject) challenges.delete(id);
    }
    if (challenges.size >= 256) fail();
    const id = crypto.randomUUID();
    const challenge = `securecall-tester-renewal-v1\n${id}\n${digest(token)}\n${subject}\n${keyHash}\n${crypto.randomBytes(32).toString("base64url")}`;
    challenges.set(id, { kind: "refresh", token, subject, keyHash, challenge, expires: time + 300 });
    return { challengeId: id, challenge };
  }
  function refresh({ challengeId, signature, subject }) {
    const pending = challenges.get(challengeId);
    challenges.delete(challengeId);
    if (!pending || pending.kind !== "refresh" || pending.expires <= now() || pending.subject !== subject
        || typeof signature !== "string" || !/^[A-Za-z0-9_-]{64,144}$/.test(signature)) fail();
    const keyHash = pending.keyHash;
    const claims = verifyTesterEntitlement(pending.token, { subject, deviceKeyHash: keyHash, publicKey: verifier,
      nowSeconds: now(), expiryGraceSeconds: 7 * 86400 });
    // Revalidation on every refresh; possession of an old signed proof cannot
    // resurrect a revoked grant or a revoked enrolled key.
    return transaction(state => {
      const grant = state.grants.find(g => g.id === claims.grant && g.status === "active");
      const key = state.enrolledKeys.find(k => k.hash === keyHash && k.status === "active");
      if (!grant || !key || grant.binding?.subject !== subject || grant.binding?.keyHash !== keyHash) fail();
      const bytes = Buffer.from(signature, "base64url");
      if (bytes.toString("base64url") !== signature
          || !crypto.verify("sha256", Buffer.from(pending.challenge), key.publicKeyPem, bytes)) fail();
      return issueTesterEntitlement({ subject, grantHash: grant.id, deviceKeyHash: keyHash, privateKey, nowSeconds: now() });
    });
  }
  return { begin, activate, beginRefresh, refresh };
}

module.exports = { createTesterLicenseRegistry };
