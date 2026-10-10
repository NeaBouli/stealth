"use strict";

// #144 independent review F1/F5/F6 regressions. Synthetic keys, temp directories only.
const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const { parseIdentityPublicKey } = require("../security/identity_protocol");
const { networkKey } = require("../ws/handlers/register");

function mockWs() {
  return {
    messages: [],
    send(value) { this.messages.push(JSON.parse(value)); },
    close() {},
    once() {},
  };
}
function last(ws) { return ws.messages.at(-1); }
function keyMaterial() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const encoded = pair.publicKey.export({ type: "spki", format: "der" }).toString("base64url");
  return { pair, ...parseIdentityPublicKey(encoded) };
}
function sign(pair, challenge) {
  return crypto.sign("sha256", Buffer.from(challenge), pair.privateKey).toString("base64url");
}
function resetState() {
  const state = require("../state");
  for (const value of Object.values(state)) if (value instanceof Map) value.clear();
  require("../services/fcm_store").fcmTokens.clear();
}
function makeContext(registry, fcm, nowSeconds, routes) {
  return buildContext({
    identityRegistry: registry, identityProtocolMode: "enforce", nowSeconds, fcm,
    identityMigrationRoutes: routes,
    pkd: {}, subscriptions: {}, customIds: { resolve: () => null }, licenses: {},
    getIceServers: () => [],
    ADMIN_API_KEY: "synthetic-admin", ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[A-Za-z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear: () => {} },
    hb: { updateClient: () => {} }, giftCodes: new Map(), saveGiftCodes: () => {},
    saveFcmTokens: () => true,
  });
}
function tempFile(prefix) {
  return path.join(fs.mkdtempSync(path.join(os.tmpdir(), prefix)), "identity_registry.json");
}

// --- networkKey: IPv6 is aggregated to /64, mapped IPv4 collapses to IPv4 ---
assert.strictEqual(networkKey("203.0.113.7"), "203.0.113.7");
assert.strictEqual(networkKey("::ffff:203.0.113.7"), "203.0.113.7");
assert.strictEqual(networkKey("2001:db8:0:1:aaaa::1"), networkKey("2001:0db8:0000:0001:ffff:ffff:ffff:ffff"));
assert.notStrictEqual(networkKey("2001:db8:0:1::1"), networkKey("2001:db8:0:2::1"));
assert.strictEqual(networkKey(undefined), null);

// --- F1: lookups are served from memory and never re-read the file ---
{
  const file = tempFile("securecall-hardening-f1-");
  const seed = createIdentityRegistry({ file, now: () => 1 });
  const ids = [];
  for (let i = 0; i < 50; i++) {
    const key = keyMaterial();
    seed.bind({ requestedClientId: key.identityId, identityId: key.identityId, publicKey: key.publicKeyBase64Url });
    ids.push(key);
  }
  const registry = createIdentityRegistry({ file, now: () => 2 });
  assert.strictEqual(registry.size(), 50);
  const originalRead = fs.readFileSync;
  let reads = 0;
  fs.readFileSync = (...args) => { if (String(args[0]) === file) reads++; return originalRead(...args); };
  try {
    for (const key of ids) {
      assert.strictEqual(registry.resolve(key.identityId), key.identityId);
      assert.ok(registry.getIdentity(key.identityId));
    }
    assert.strictEqual(reads, 0, "lookups must not touch the registry file");

    // An unchanged reconnect does not rewrite the file.
    const before = fs.statSync(file).mtimeMs;
    const writes = [];
    const originalRename = fs.renameSync;
    fs.renameSync = (...args) => { writes.push(args); return originalRename(...args); };
    try {
      const result = registry.bind({ requestedClientId: ids[0].identityId, identityId: ids[0].identityId,
        publicKey: ids[0].publicKeyBase64Url });
      assert.strictEqual(result.createdIdentity, false);
    } finally { fs.renameSync = originalRename; }
    assert.strictEqual(writes.length, 0, "unchanged reconnect must not persist");
    assert.strictEqual(fs.statSync(file).mtimeMs, before);
  } finally { fs.readFileSync = originalRead; }

  // Creation cap applies to new identities only; existing ones still reconnect.
  const capped = createIdentityRegistry({ file, now: () => 3, maxIdentities: 50 });
  const extra = keyMaterial();
  assert.throws(() => capped.bind({ requestedClientId: extra.identityId, identityId: extra.identityId,
    publicKey: extra.publicKeyBase64Url }), error => error.code === "identity_registry_full");
  assert.doesNotThrow(() => capped.bind({ requestedClientId: ids[1].identityId, identityId: ids[1].identityId,
    publicKey: ids[1].publicKeyBase64Url }));

  // A failed persist leaves the served state unchanged.
  const failing = createIdentityRegistry({ file, now: () => 4 });
  const originalRename = fs.renameSync;
  fs.renameSync = () => { throw Object.assign(new Error("synthetic"), { code: "EIO" }); };
  try {
    assert.throws(() => failing.bind({ requestedClientId: extra.identityId, identityId: extra.identityId,
      publicKey: extra.publicKeyBase64Url }));
  } finally { fs.renameSync = originalRename; }
  assert.strictEqual(failing.getIdentity(extra.identityId), null);
  assert.strictEqual(failing.size(), 50);
}

// --- F5: a lock left by a dead process does not brick bind(); a live owner's lock is respected ---
{
  const file = tempFile("securecall-hardening-f5-");
  const registry = createIdentityRegistry({ file, now: () => 1 });
  const dead = require("child_process").spawnSync(process.execPath, ["-e", "process.stdout.write(String(process.pid))"]);
  fs.writeFileSync(`${file}.lock`, dead.stdout.toString());
  const a = keyMaterial();
  registry.bind({ requestedClientId: a.identityId, identityId: a.identityId, publicKey: a.publicKeyBase64Url });
  assert.ok(!fs.existsSync(`${file}.lock`), "stale lock must be cleared");
  assert.strictEqual(createIdentityRegistry({ file }).resolve(a.identityId), a.identityId);

  fs.writeFileSync(`${file}.lock`, String(process.ppid));
  const b = keyMaterial();
  assert.throws(() => registry.bind({ requestedClientId: b.identityId, identityId: b.identityId,
    publicKey: b.publicKeyBase64Url }), error => error.code === "identity_registry_unavailable");
  fs.unlinkSync(`${file}.lock`);
}

// --- F6 + F1 network budget: push cooldown, short TTL, new identities per /64 ---
(async () => {
  resetState();
  let now = 2_000_000_000;
  const file = tempFile("securecall-hardening-f6-");
  const registry = createIdentityRegistry({ file, now: () => now });
  const pushes = [];
  const fcm = {
    isInitialized: () => true,
    sendIdentityMigrationChallenge: async (token, payload) => { pushes.push(payload); return true; },
  };
  const routes = new Map([["android-legacy01", "synthetic-legacy-token"]]);
  const ctx = makeContext(registry, fcm, () => now, routes);
  const flush = () => new Promise(resolve => setImmediate(resolve));

  // Ordinary challenges expire after 60 s.
  const k = keyMaterial();
  const ws = mockWs();
  ctx.clients.set("c-ttl", { ws, clientId: null, ip: "198.51.100.1" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(ws, "c-ttl", { protocol: 2, clientId: k.identityId,
    identityId: k.identityId, publicKey: k.publicKeyBase64Url });
  const challenge = last(ws);
  assert.strictEqual(challenge.type, "IDENTITY_REGISTER_CHALLENGE");
  assert.strictEqual(challenge.expiresAt, now + 60);

  // Repeated migration BEGINs for one (legacy id, identity) push at most once per cooldown.
  const m = keyMaterial();
  for (let i = 0; i < 3; i++) {
    const conn = `c-mig-${i}`;
    const socket = mockWs();
    ctx.clients.set(conn, { ws: socket, clientId: null, ip: `192.0.2.${i + 1}` });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(socket, conn, { protocol: 2, clientId: "android-legacy01",
      identityId: m.identityId, publicKey: m.publicKeyBase64Url });
    await flush();
    if (i > 0) assert.strictEqual(last(socket).error, "identity_challenge_rate_limited");
  }
  assert.strictEqual(pushes.length, 1, "migration push must be rate limited per claim");

  // A different claimant cannot lock the real owner out of migrating.
  const owner = keyMaterial();
  const ownerWs = mockWs();
  ctx.clients.set("c-owner", { ws: ownerWs, clientId: null, ip: "192.0.2.99" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(ownerWs, "c-owner", { protocol: 2, clientId: "android-legacy01",
    identityId: owner.identityId, publicKey: owner.publicKeyBase64Url });
  await flush();
  assert.strictEqual(last(ownerWs).type, "IDENTITY_MIGRATION_PENDING");
  assert.strictEqual(pushes.length, 2);

  // One network can trigger at most 5 migration pushes per window, whatever it claims.
  let spamPushes = 0;
  for (let i = 0; i < 8; i++) {
    const claim = keyMaterial();
    const socket = mockWs();
    ctx.clients.set(`c-spam-${i}`, { ws: socket, clientId: null, ip: `2001:db8:0:9::${i + 1}` });
    const before = pushes.length;
    ctx.handlers.IDENTITY_REGISTER_BEGIN(socket, `c-spam-${i}`, { protocol: 2, clientId: "android-legacy01",
      identityId: claim.identityId, publicKey: claim.publicKeyBase64Url });
    await flush();
    spamPushes += pushes.length - before;
    // Burn the pending proof so the per-IP pending cap does not mask the push budget.
    if (pushes.length > before) {
      ctx.handlers.IDENTITY_REGISTER_COMPLETE(socket, `c-spam-${i}`, {
        challengeId: pushes.at(-1).challengeId, signature: "AA" });
    }
  }
  assert.strictEqual(spamPushes, 5, "per-network migration push budget");

  now += 61;
  const again = mockWs();
  ctx.clients.set("c-mig-later", { ws: again, clientId: null, ip: "192.0.2.50" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(again, "c-mig-later", { protocol: 2, clientId: "android-legacy01",
    identityId: m.identityId, publicKey: m.publicKeyBase64Url });
  await flush();
  assert.strictEqual(last(again).type, "IDENTITY_MIGRATION_PENDING", "push allowed again after the cooldown");

  // New identities are budgeted per /64: rotating addresses inside one /64 does not help.
  let created = 0;
  let limited = false;
  let firstKey = null;
  for (let i = 0; i < 12; i++) {
    const key = keyMaterial();
    const socket = mockWs();
    const conn = `c-new-${i}`;
    ctx.clients.set(conn, { ws: socket, clientId: null, ip: `2001:db8:0:7::${(i + 1).toString(16)}` });
    ctx.handlers.IDENTITY_REGISTER_BEGIN(socket, conn, { protocol: 2, clientId: key.identityId,
      identityId: key.identityId, publicKey: key.publicKeyBase64Url });
    const reply = last(socket);
    if (reply.error === "identity_challenge_rate_limited") { limited = true; break; }
    ctx.handlers.IDENTITY_REGISTER_COMPLETE(socket, conn, { challengeId: reply.challengeId,
      signature: sign(key.pair, reply.challenge) });
    assert.strictEqual(last(socket).type, "REGISTERED", JSON.stringify(last(socket)));
    created++;
    firstKey = firstKey || key;
  }
  assert.ok(limited, "new-identity budget must trigger");
  assert.strictEqual(created, 10);

  // Known identities from the same (exhausted) network still reconnect.
  const known = mockWs();
  ctx.clients.set("c-known", { ws: known, clientId: null, ip: "2001:db8:0:7::ff" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(known, "c-known", { protocol: 2, clientId: firstKey.identityId,
    identityId: firstKey.identityId, publicKey: firstKey.publicKeyBase64Url });
  assert.strictEqual(last(known).type, "IDENTITY_REGISTER_CHALLENGE", JSON.stringify(last(known)));
  assert.strictEqual(registry.size(), 10);

  // Another /64 has its own budget.
  const other = keyMaterial();
  const otherWs = mockWs();
  ctx.clients.set("c-other", { ws: otherWs, clientId: null, ip: "2001:db8:0:8::1" });
  ctx.handlers.IDENTITY_REGISTER_BEGIN(otherWs, "c-other", { protocol: 2, clientId: other.identityId,
    identityId: other.identityId, publicKey: other.publicKeyBase64Url });
  assert.strictEqual(last(otherWs).type, "IDENTITY_REGISTER_CHALLENGE");

  console.log("identity_hardening.test.js: PASS");
})().catch(error => { console.error(error); process.exit(1); });
