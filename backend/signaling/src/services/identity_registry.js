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

// The registry is loaded and fully validated once, then served from memory (SCT/STX-01
// review F1): lookups never re-read the file or re-parse keys. Writes go through an
// atomic temp+rename with a pid-stamped lock, and only when something actually changes.
const MAX_FILE_BYTES = 64 * 1024 * 1024;
const DEFAULT_MAX_IDENTITIES = 100000;

function processAlive(pid) {
  if (!Number.isSafeInteger(pid) || pid <= 0) return false;
  try { process.kill(pid, 0); return true; } catch (error) { return error.code === "EPERM"; }
}

function createIdentityRegistry({
  file,
  now = () => Math.floor(Date.now() / 1000),
  maxIdentities = DEFAULT_MAX_IDENTITIES,
}) {
  if (!path.isAbsolute(file)) fail();
  const directory = path.dirname(file);
  const lock = `${file}.lock`;
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

  function load() {
    createIfMissing();
    try {
      const stat = fs.lstatSync(file);
      if (!stat.isFile() || stat.isSymbolicLink() || stat.nlink !== 1 || stat.size > MAX_FILE_BYTES) fail();
      return validateState(JSON.parse(fs.readFileSync(file, "utf8")));
    } catch (error) {
      if (error instanceof IdentityRegistryError) throw error;
      fail();
    }
  }

  // A lock left by a dead process (crash, SIGKILL during deploy) must not brick bind()
  // forever (review F5). The lock records its owner pid; a dead owner's lock is removed.
  function clearStaleLock() {
    let owner;
    try { owner = Number.parseInt(fs.readFileSync(lock, "utf8"), 10); }
    catch (error) { return error.code === "ENOENT"; }
    if (processAlive(owner) && owner !== process.pid) return false;
    try { fs.unlinkSync(lock); } catch (error) { if (error.code !== "ENOENT") return false; }
    return true;
  }

  function acquireLock() {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const fd = fs.openSync(lock, fs.constants.O_CREAT | fs.constants.O_EXCL
          | fs.constants.O_WRONLY | fs.constants.O_NOFOLLOW, 0o600);
        fs.writeFileSync(fd, String(process.pid));
        return fd;
      } catch (error) {
        if (error.code !== "EEXIST" || attempt > 0 || !clearStaleLock()) fail();
      }
    }
    return fail();
  }

  let state = load();
  const identities = new Map(state.identities.map(entry => [entry.identityId, entry]));
  const aliases = new Map(state.aliases.map(entry => [entry.alias, entry]));

  function persist() {
    const lockFd = acquireLock();
    let temporary;
    try {
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
    } catch (error) {
      if (error instanceof IdentityRegistryError) throw error;
      fail();
    } finally {
      if (temporary) { try { fs.unlinkSync(temporary); } catch { /* fail closed */ } }
      try { fs.unlinkSync(lock); } catch { /* best effort; stale-lock recovery handles it */ }
      fs.closeSync(lockFd);
    }
  }

  function getIdentity(identityId) {
    const entry = identities.get(identityId);
    return entry ? { ...entry } : null;
  }

  function resolve(clientId) {
    const identity = identities.get(clientId);
    if (identity && identity.status === "active") return identity.identityId;
    const alias = aliases.get(clientId);
    return alias && alias.status === "active" ? alias.identityId : null;
  }

  function bind({ requestedClientId, identityId, publicKey, migrationAuthorized = false }) {
    if (!CLIENT_ID.test(requestedClientId) || !IDENTITY_ID.test(identityId)) fail("invalid_identity_binding");
    const parsed = parseIdentityPublicKey(publicKey);
    if (parsed.identityId !== identityId) fail("identity_key_mismatch");

    const time = now();
    const existing = identities.get(identityId);
    if (existing && (existing.status !== "active" || existing.publicKey !== parsed.publicKeyBase64Url
        || existing.keyHash !== parsed.keyHash)) fail("identity_key_mismatch");
    if (!existing && identities.size >= maxIdentities) fail("identity_registry_full");

    let aliasEntry = null;
    if (requestedClientId !== identityId) {
      if (IDENTITY_ID.test(requestedClientId)) fail("identity_alias_invalid");
      aliasEntry = aliases.get(requestedClientId) || null;
      if (aliasEntry && (aliasEntry.status !== "active" || aliasEntry.identityId !== identityId)) {
        fail("identity_alias_conflict");
      }
      if (!aliasEntry && !migrationAuthorized) fail("identity_migration_required");
    }

    const createIdentity = !existing;
    const createAlias = requestedClientId !== identityId && !aliasEntry;
    let identity = existing;
    if (createIdentity || createAlias) {
      // Build the next state, persist it, and only then publish it in memory, so a
      // storage failure leaves both the file and the served state unchanged.
      const nextIdentities = state.identities.slice();
      const nextAliases = state.aliases.slice();
      if (createIdentity) {
        identity = { identityId, keyHash: parsed.keyHash, publicKey: parsed.publicKeyBase64Url,
          status: "active", createdAt: time, updatedAt: time };
        nextIdentities.push(identity);
      }
      let newAlias = null;
      if (createAlias) {
        newAlias = { alias: requestedClientId, identityId, status: "active", createdAt: time, updatedAt: time };
        nextAliases.push(newAlias);
      }
      const previous = state;
      state = { schema: 1, identities: nextIdentities, aliases: nextAliases };
      try { persist(); } catch (error) { state = previous; throw error; }
      if (createIdentity) identities.set(identityId, identity);
      if (newAlias) aliases.set(newAlias.alias, newAlias);
    }
    return {
      ...identity,
      requestedClientId,
      createdIdentity: createIdentity,
      migratedAlias: requestedClientId !== identityId ? requestedClientId : null,
    };
  }

  function aliasesFor(identityId) {
    const result = [];
    for (const entry of aliases.values()) {
      if (entry.identityId === identityId && entry.status === "active") result.push(entry.alias);
    }
    return result;
  }

  // Operator reversal of a disputed legacy migration (#144 review F2, owner decision
  // 2026-10-11: keep legacy ids, with audit and revoke). A revoked alias resolves to
  // nothing and cannot be re-bound automatically (bind() reports identity_alias_conflict).
  function revokeAlias(alias) {
    const entry = aliases.get(alias);
    if (!entry) fail("identity_alias_unknown");
    if (entry.status !== "active") return { ...entry, changed: false };
    const revoked = { ...entry, status: "revoked", updatedAt: now() };
    const previous = state;
    state = { schema: 1, identities: state.identities,
      aliases: state.aliases.map(item => (item.alias === alias ? revoked : item)) };
    try { persist(); } catch (error) { state = previous; throw error; }
    aliases.set(alias, revoked);
    return { ...revoked, changed: true };
  }

  function getAlias(alias) {
    const entry = aliases.get(alias);
    return entry ? { ...entry } : null;
  }

  return { getIdentity, resolve, bind, aliasesFor, getAlias, revokeAlias, size: () => identities.size };
}

module.exports = { createIdentityRegistry, IdentityRegistryError };
