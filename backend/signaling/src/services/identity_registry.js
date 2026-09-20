"use strict";

const crypto = require("crypto");
const fs = require("fs");
const path = require("path");
const {
  CLIENT_ID,
  IDENTITY_ID,
  parseIdentityPublicKey,
} = require("../security/identity_protocol");

class IdentityRegistryError extends Error {
  constructor(code = "identity_registry_unavailable") {
    super(code);
    this.code = code;
  }
}

function fail(code) { throw new IdentityRegistryError(code); }

function initialState() { return { schema: 1, identities: [], aliases: [] }; }

function validateState(value) {
  if (!value || value.schema !== 1 || !Array.isArray(value.identities) || !Array.isArray(value.aliases)) fail();
  const identities = new Set();
  for (const entry of value.identities) {
    if (!entry || !IDENTITY_ID.test(entry.identityId) || identities.has(entry.identityId)
        || !["active", "revoked"].includes(entry.status)
        || !Number.isSafeInteger(entry.createdAt) || !Number.isSafeInteger(entry.updatedAt)) fail();
    const parsed = parseIdentityPublicKey(entry.publicKey);
    if (parsed.identityId !== entry.identityId || parsed.keyHash !== entry.keyHash) fail();
    identities.add(entry.identityId);
  }
  const aliases = new Set();
  for (const entry of value.aliases) {
    if (!entry || !CLIENT_ID.test(entry.alias) || IDENTITY_ID.test(entry.alias)
        || aliases.has(entry.alias) || !identities.has(entry.identityId)
        || !["active", "revoked"].includes(entry.status)
        || !Number.isSafeInteger(entry.createdAt) || !Number.isSafeInteger(entry.updatedAt)) fail();
    aliases.add(entry.alias);
  }
  return value;
}

function createIdentityRegistry({ file, now = () => Math.floor(Date.now() / 1000) }) {
  if (!path.isAbsolute(file)) fail();
  const directory = path.dirname(file);
  fs.mkdirSync(directory, { recursive: true });

  function createIfMissing() {
    if (fs.existsSync(file)) return;
    let descriptor;
    try {
      descriptor = fs.openSync(file, fs.constants.O_CREAT | fs.constants.O_EXCL
        | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
      fs.writeFileSync(descriptor, JSON.stringify(initialState()));
      fs.fsyncSync(descriptor);
    } catch (error) {
      if (error.code !== "EEXIST") fail();
    } finally {
      if (descriptor !== undefined) fs.closeSync(descriptor);
    }
  }

  function read() {
    createIfMissing();
    try {
      const stat = fs.lstatSync(file);
      if (!stat.isFile() || stat.isSymbolicLink() || stat.nlink !== 1 || stat.size > 5 * 1024 * 1024) fail();
      return validateState(JSON.parse(fs.readFileSync(file, "utf8")));
    } catch (error) {
      if (error instanceof IdentityRegistryError) throw error;
      fail();
    }
  }

  function transaction(change) {
    createIfMissing();
    const lock = `${file}.lock`;
    let lockFd;
    let temporary;
    try {
      lockFd = fs.openSync(lock, fs.constants.O_CREAT | fs.constants.O_EXCL
        | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
      const state = read();
      const result = change(state);
      validateState(state);
      temporary = `${file}.${crypto.randomUUID()}.tmp`;
      const output = fs.openSync(temporary, fs.constants.O_CREAT | fs.constants.O_EXCL
        | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
      try {
        fs.writeFileSync(output, JSON.stringify(state));
        fs.fsyncSync(output);
      } finally { fs.closeSync(output); }
      fs.renameSync(temporary, file);
      temporary = undefined;
      const dirFd = fs.openSync(directory, fs.constants.O_RDONLY);
      try { fs.fsyncSync(dirFd); } finally { fs.closeSync(dirFd); }
      return result;
    } catch (error) {
      if (error instanceof IdentityRegistryError) throw error;
      fail();
    } finally {
      if (temporary) { try { fs.unlinkSync(temporary); } catch { /* fail closed */ } }
      if (lockFd !== undefined) {
        try { fs.unlinkSync(lock); } catch { /* a failed transaction stays failed */ }
        fs.closeSync(lockFd);
      }
    }
  }

  function getIdentity(identityId) {
    const entry = read().identities.find(item => item.identityId === identityId);
    return entry ? { ...entry } : null;
  }

  function resolve(clientId) {
    const state = read();
    const identity = state.identities.find(item => item.identityId === clientId && item.status === "active");
    if (identity) return identity.identityId;
    const alias = state.aliases.find(item => item.alias === clientId && item.status === "active");
    return alias ? alias.identityId : null;
  }

  function bind({ requestedClientId, identityId, publicKey, migrationAuthorized = false }) {
    if (!CLIENT_ID.test(requestedClientId) || !IDENTITY_ID.test(identityId)) fail("invalid_identity_binding");
    const parsed = parseIdentityPublicKey(publicKey);
    if (parsed.identityId !== identityId) fail("identity_key_mismatch");
    return transaction(state => {
      const time = now();
      let identity = state.identities.find(item => item.identityId === identityId);
      if (identity) {
        if (identity.status !== "active" || identity.publicKey !== parsed.publicKeyBase64Url
            || identity.keyHash !== parsed.keyHash) fail("identity_key_mismatch");
        identity.updatedAt = time;
      } else {
        identity = { identityId, keyHash: parsed.keyHash, publicKey: parsed.publicKeyBase64Url,
          status: "active", createdAt: time, updatedAt: time };
        state.identities.push(identity);
      }
      if (requestedClientId !== identityId) {
        if (IDENTITY_ID.test(requestedClientId)) fail("identity_alias_invalid");
        let alias = state.aliases.find(item => item.alias === requestedClientId);
        if (alias) {
          if (alias.status !== "active" || alias.identityId !== identityId) fail("identity_alias_conflict");
          alias.updatedAt = time;
        } else {
          if (!migrationAuthorized) fail("identity_migration_required");
          alias = { alias: requestedClientId, identityId, status: "active", createdAt: time, updatedAt: time };
          state.aliases.push(alias);
        }
      }
      return { ...identity, requestedClientId, migratedAlias: requestedClientId !== identityId ? requestedClientId : null };
    });
  }

  function aliasesFor(identityId) {
    return read().aliases.filter(item => item.identityId === identityId && item.status === "active")
      .map(item => item.alias);
  }

  createIfMissing();
  read();
  return { getIdentity, resolve, bind, aliasesFor };
}

module.exports = { createIdentityRegistry, IdentityRegistryError };
