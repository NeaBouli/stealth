"use strict";

// Same-package Play lifetime entitlement: real SUBSCRIPTION_VERIFY handler, real
// subscription store (temp file), real RTDN route and status mapping. Synthetic data
// only: no HTTP listener, Play client, credentials or provider fallback.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const crypto = require("node:crypto");
const { execFileSync } = require("node:child_process");
const { after, test } = require("node:test");

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-play-lifetime-"));
const SUBS_FILE = path.join(directory, "subscriptions.json");
process.env.SUBS_FILE = SUBS_FILE;
process.env.GOOGLE_PLAY_ALLOWED_PACKAGES = "com.securecall.app.free";
process.env.GOOGLE_PLAY_RTDN_FILE = path.join(directory, "rtdn.json");
process.env.GOOGLE_PLAY_RTDN_AUDIENCE = "https://example.test/rtdn";
process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL = "rtdn@example.test";

const subscriptions = require("../subscriptions");
const billing = require("../payments/google_play_billing");
const { installGooglePlayRtdnRoute } = require("../payments/google_play_rtdn");
const { buildContext } = require("../context");
const { createIdentityRegistry } = require("../services/identity_registry");
const { parseIdentityPublicKey } = require("../security/identity_protocol");

const PKG = "com.securecall.app.free";
const CATALOG = billing.PLAY_CATALOG_VERSION;
const PRO = "securecall_pro_lifetime";
const PREMIUM = "securecall_premium_lifetime";
const REQUEST_ID = "123e4567-e89b-12d3-a456-426614174000";
// Synthetic policy shape only; real values are owner-supplied and absent from source.
const POLICY = Object.freeze({
  catalogVersion: CATALOG,
  offerVersion: "synthetic-offer",
  products: { [PRO]: { packageName: PKG }, [PREMIUM]: { packageName: PKG } },
  proofTtlMs: 60000,
  rebindAuthority: "none",
});

after(() => fs.rmSync(directory, { recursive: true, force: true }));

function flush() {
  return new Promise(resolve => setImmediate(resolve)).then(() => new Promise(r => setImmediate(r)));
}

function harness({ policy = POLICY, aliases = {}, identityRegistry, verify, acknowledge, activationCodes = [], enabled = true } = {}) {
  const calls = { verify: 0, acknowledge: 0, subscriptionVerify: 0, subscriptionAcknowledge: 0, ackSawDurableRecord: [] };
  const ctx = buildContext({
    pkd: { registerKey: () => ({}), getKey: () => null },
    subscriptions,
    fcm: { isInitialized: () => false, sendCallInvitePush() {}, sendDataMessage() {} },
    customIds: { resolve: () => null },
    licenses: { getStatus: () => ({}), getCurrentPrice: () => null },
    getIceServers: () => [],
    ADMIN_API_KEY: "synthetic-admin",
    ALLOWED_ORIGINS: [],
    CLIENT_ID_REGEX: /^[a-zA-Z0-9_-]{1,64}$/,
    rateLimit: { registerEvent: () => true, registerBinaryEvent: () => true, clear() {} },
    hb: { start() {}, updateClient() {}, stop() {} },
    giftCodes: new Map(),
    saveGiftCodes: () => true,
    saveActivationCodes: () => true,
    issueEntitlementToken: () => null,
    verifyEntitlementToken: () => { throw new Error("unused"); },
    entitlementOrderHash: () => "unused",
    verifyPlaySubscription: (packageName, productId) => {
      calls.subscriptionVerify += 1;
      if (!billing.SUBSCRIPTION_PRODUCTS[productId]) throw new Error("unsupported_package_or_product");
      return { tier: "pro", expiresAt: Date.now() + 86400000, catalogVersion: CATALOG, needsAcknowledgement: false };
    },
    acknowledgePlaySubscription: () => { calls.subscriptionAcknowledge += 1; return true; },
    playBillingEnabled: enabled,
    verifyPlayOneTimePurchase: async (packageName, productId, purchaseToken) => {
      calls.verify += 1;
      if (verify) return verify(packageName, productId, purchaseToken);
      const product = billing.resolveOneTimeProduct(packageName, productId);
      if (!product) throw new Error("unsupported_package_or_product");
      return { ...product, packageName, needsAcknowledgement: true };
    },
    acknowledgePlayOneTimePurchase: async (packageName, productId, purchaseToken) => {
      calls.acknowledge += 1;
      calls.ackSawDurableRecord.push(
        JSON.parse(fs.readFileSync(SUBS_FILE, "utf8"))
          && Object.values(JSON.parse(fs.readFileSync(SUBS_FILE, "utf8")))
            .some(entry => entry.kind === "play_lifetime" && entry.purchaseToken === purchaseToken)
      );
      if (acknowledge) return acknowledge(packageName, productId, purchaseToken);
    },
    playLifetimePolicy: policy,
    identityRegistry: identityRegistry || { aliasesFor: clientId => aliases[clientId] || [] },
  });
  ctx.activationCodes.splice(0, ctx.activationCodes.length, ...activationCodes);
  const sockets = new Map();
  const connect = clientId => {
    const ws = { readyState: 1, messages: [], send(data) { this.messages.push(JSON.parse(data)); }, close() {} };
    const connId = `conn-${clientId}`;
    ctx.clients.set(connId, { ws, lastSeen: Date.now(), clientId, ip: "192.0.2.1" });
    ctx.clientIds.set(clientId, connId);
    sockets.set(clientId, { ws, connId });
  };
  const start = (clientId, fields) => {
    if (!sockets.has(clientId)) connect(clientId);
    const { ws, connId } = sockets.get(clientId);
    ctx.handlers.SUBSCRIPTION_VERIFY(ws, connId, {
      requestId: REQUEST_ID, packageName: PKG, catalogVersion: CATALOG, ...fields,
    });
    return ws;
  };
  const send = async (clientId, fields) => {
    const ws = start(clientId, fields);
    await flush();
    return ws.messages[ws.messages.length - 1];
  };
  return { ctx, calls, send, start };
}

function status(clientId, purchaseToken, policy = POLICY) {
  return billing.lifetimeStatusResponse(
    subscriptions.getLifetimeEntitlement(clientId, purchaseToken, PKG, CATALOG), policy
  );
}

function deferred() {
  let resolve;
  let reject;
  const promise = new Promise((res, rej) => { resolve = res; reject = rej; });
  return { promise, resolve, reject };
}

function rtdn({ activationCodes = [] } = {}) {
  let handler;
  installGooglePlayRtdnRoute({ post(_route, callback) { handler = callback; } }, {
    subscriptions,
    activationCodes,
    saveActivationCodes: () => true,
    giftCodes: new Map(),
    saveGiftCodes: () => true,
    async verifyPushIdentity() {},
    async verifyPlaySubscriptionToken() { throw new Error("subscription_not_active"); },
  });
  return async (messageId, notification) => {
    const response = {
      statusCode: 200,
      status(code) { this.statusCode = code; return this; },
      json(body) { this.body = body; return this; },
      sendStatus(code) { this.statusCode = code; return this; },
    };
    await handler({ headers: {}, body: { message: {
      messageId,
      data: Buffer.from(JSON.stringify({ packageName: PKG, ...notification })).toString("base64"),
    } } }, response);
    return response.statusCode;
  };
}

test("missing or incomplete policy keeps lifetime unavailable without provider calls", async () => {
  const { calls, send } = harness({ policy: null });
  const ack = await send("alice", { purchaseToken: "tok-nopolicy", productId: PRO });
  assert.equal(ack.success, false);
  assert.equal(ack.error, "lifetime_entitlement_unavailable");
  assert.equal(ack.tier, "FREE");
  assert.equal(calls.verify, 0);
  assert.equal(subscriptions.hasLifetimePurchase("tok-nopolicy"), false);

  for (const broken of [
    { ...POLICY, catalogVersion: "other-catalog" },
    { ...POLICY, offerVersion: "" },
    { ...POLICY, proofTtlMs: 0 },
    { ...POLICY, proofTtlMs: Infinity },
    { ...POLICY, proofTtlMs: undefined },
    { ...POLICY, rebindAuthority: undefined },
    { ...POLICY, rebindAuthority: "auto_transfer" },
    { ...POLICY, products: {} },
    { ...POLICY, products: { securecall_premium_activation_code: { packageName: PKG } } },
    { ...POLICY, products: { securecall_pro_monthly: { packageName: PKG } } },
    { ...POLICY, products: { [PRO]: { packageName: "com.securecall.app.premium" } } },
  ]) {
    assert.equal(billing.normalizeLifetimePolicy(broken), null, JSON.stringify(broken));
  }
});

test("grant persists an identity-bound record before acknowledgement, then restores idempotently", async () => {
  const { calls, send } = harness();
  const before = Date.now();
  const ack = await send("alice", { purchaseToken: "tok-grant", productId: PREMIUM });
  assert.equal(ack.success, true, JSON.stringify(ack));
  assert.equal(ack.tier, "premium");
  assert.equal(ack.requestId, REQUEST_ID);
  assert.equal(ack.productId, PREMIUM);
  assert.ok(ack.expiresAt >= before + POLICY.proofTtlMs && ack.expiresAt <= Date.now() + POLICY.proofTtlMs);
  assert.deepEqual(calls.ackSawDurableRecord, [true], "durable record exists before Play acknowledgement");
  assert.equal(status("alice", "tok-grant").valid, true);
  assert.equal(subscriptions.getTier("alice"), "FREE", "monthly tier store is unaffected");
  assert.equal(subscriptions.getSubscription("alice"), null);

  const restored = harness({ verify: (p, id) => ({ ...billing.resolveOneTimeProduct(p, id), packageName: p, needsAcknowledgement: false }) });
  const again = await restored.send("alice", { purchaseToken: "tok-grant", productId: PREMIUM });
  assert.equal(again.success, true);
  assert.equal(again.tier, "premium");
  assert.equal(restored.calls.acknowledge, 0, "restore does not re-acknowledge");
});

test("success log is a generic aggregate event without client, device or token identities", async t => {
  const logs = [];
  t.mock.method(console, "log", (...args) => logs.push(args.join(" ")));
  const h = harness();
  assert.equal((await h.send("private-client-fixture", {
    purchaseToken: "private-token-fixture", productId: PRO,
  })).success, true);
  assert.deepEqual(logs, ["[SUBSCRIPTION] Lifetime verification completed"]);
});

test("closed commerce gate rejects lifetime before provider or persistence work", async () => {
  const h = harness({ enabled: false });
  const ack = await h.send("alice", { purchaseToken: "tok-closed", productId: PRO });
  assert.equal(ack.success, false);
  assert.equal(ack.error, "play_billing_disabled");
  assert.equal(h.calls.verify, 0);
  assert.equal(h.calls.acknowledge, 0);
  assert.equal(subscriptions.hasLifetimePurchase("tok-closed"), false);
  const malformed = await h.send("alice", { purchaseToken: {}, productId: { toString: null, valueOf: null } });
  assert.equal(malformed.error, "play_billing_disabled", "disabled commerce keeps its existing error precedence");
  assert.equal(h.calls.verify + h.calls.subscriptionVerify, 0);
});

test("receipt fields reject malformed JSON values before lookup or provider work", async t => {
  const fields = { requestId: 36, purchaseToken: 8192, productId: 256, packageName: 255, catalogVersion: 128 };
  for (const [field, limit] of Object.entries(fields)) {
    const cases = {
      coercionObject: { toString: null, valueOf: null },
      object: {},
      emptyArray: [],
      array: [PRO],
      booleanTrue: true,
      booleanFalse: false,
      number: 1,
      null: null,
      empty: "",
      blank: " ",
      overlong: field === "requestId" ? REQUEST_ID + "\n" : "x".repeat(limit + 1),
    };
    for (const [name, value] of Object.entries(cases)) {
      await t.test(`${field}: ${name}`, async () => {
        const h = harness();
        const before = fs.existsSync(SUBS_FILE) ? fs.readFileSync(SUBS_FILE, "utf8") : null;
        const input = JSON.parse(JSON.stringify({ purchaseToken: "tok-malformed", productId: PRO, [field]: value }));
        let ws;
        assert.doesNotThrow(() => { ws = h.start("malformed-client", input); });
        await flush();
        assert.deepEqual(ws.messages, [{
          type: "SUBSCRIPTION_VERIFY_ACK", success: false,
          requestId: field === "requestId" ? "" : REQUEST_ID,
          tier: "FREE", expiresAt: 0, productId: "", packageName: "", catalogVersion: "",
          error: "invalid_subscription_verification_request",
        }]);
        assert.equal(h.calls.verify + h.calls.subscriptionVerify + h.calls.acknowledge + h.calls.subscriptionAcknowledge, 0);
        const after = fs.existsSync(SUBS_FILE) ? fs.readFileSync(SUBS_FILE, "utf8") : null;
        assert.equal(after, before, "malformed input must not change the durable entitlement store");
      });
    }
  }
});

function registryWithAlias(name, alias) {
  const keys = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const identity = parseIdentityPublicKey(keys.publicKey.export({ type: "spki", format: "der" }).toString("base64url"));
  const registry = createIdentityRegistry({ file: path.join(directory, name + ".json") });
  registry.bind({ requestedClientId: alias, identityId: identity.identityId,
    publicKey: identity.publicKeyBase64Url, migrationAuthorized: true });
  return { registry, identityId: identity.identityId };
}

test("alias revoked during provider verification cannot authorize a lifetime transfer", async () => {
  const owner = "legacy-before-verify";
  const token = "tok-alias-before-verify";
  const policy = { ...POLICY, rebindAuthority: "identity_registry_alias" };
  const { registry, identityId } = registryWithAlias("alias-before-verify", owner);
  assert.equal((await harness().send(owner, { purchaseToken: token, productId: PRO })).success, true);
  assert.deepEqual(registry.aliasesFor(identityId), [owner]);
  const gate = deferred();
  const racing = harness({ policy, identityRegistry: registry, verify: () => gate.promise });
  const ws = racing.start(identityId, { purchaseToken: token, productId: PRO });
  await flush();
  assert.equal(racing.calls.verify, 1);
  assert.equal(ws.messages.length, 0, "verification is still awaiting the provider");
  registry.revokeAlias(owner);
  assert.deepEqual(registry.aliasesFor(identityId), []);
  gate.resolve(restoredAnswer(PKG, PRO));
  await flush();
  assert.equal(ws.messages.at(-1).success, false, "revoked authority cannot transfer the purchase");
  assert.equal(racing.calls.acknowledge, 0);
  assert.equal(status(identityId, token), null);
  assert.equal(status(owner, token).valid, true, "the original owner keeps the right");
  assert.equal((await harness({ policy, identityRegistry: registry, verify: restoredAnswer })
    .send(owner, { purchaseToken: token, productId: PRO })).success, true, "same-identity restore still works");
});

test("completed lifetime transfer and same-identity restore survive a later alias revocation", async () => {
  const owner = "legacy-after-transfer";
  const token = "tok-alias-after-transfer";
  const policy = { ...POLICY, rebindAuthority: "identity_registry_alias" };
  const { registry, identityId } = registryWithAlias("alias-after-transfer", owner);
  assert.equal((await harness().send(owner, { purchaseToken: token, productId: PRO })).success, true);
  const current = harness({ policy, identityRegistry: registry, verify: restoredAnswer });
  assert.equal((await current.send(identityId, { purchaseToken: token, productId: PRO })).success, true);
  assert.equal(status(owner, token), null);
  registry.revokeAlias(owner);
  assert.equal(status(identityId, token).valid, true, "later revocation is not retroactive");
  assert.equal((await current.send(identityId, { purchaseToken: token, productId: PRO })).success, true);
});

test("forged, replayed, cross-identity, cross-package and cross-product claims fail closed", async () => {
  const base = harness();
  assert.equal((await base.send("alice", { purchaseToken: "tok-bound", productId: PRO })).success, true);

  const stolen = await base.send("mallory", { purchaseToken: "tok-bound", productId: PRO });
  assert.equal(stolen.success, false);
  assert.equal(status("mallory", "tok-bound"), null);
  assert.equal(status("alice", "tok-bound").valid, true, "owner keeps the right");

  const productSwap = await base.send("alice", { purchaseToken: "tok-bound", productId: PREMIUM });
  assert.equal(productSwap.success, false, "same token cannot be re-labelled as another product");

  const packageSwap = await base.send("alice", {
    purchaseToken: "tok-package", productId: PRO, packageName: "com.securecall.app.premium",
  });
  assert.equal(packageSwap.error, "lifetime_entitlement_unavailable");

  const catalogSwap = await base.send("alice", { purchaseToken: "tok-catalog", productId: PRO, catalogVersion: "other" });
  assert.equal(catalogSwap.error, "lifetime_entitlement_unavailable");

  const forged = harness({ verify: () => { throw new Error("purchase_not_completed"); } });
  assert.equal((await forged.send("alice", { purchaseToken: "tok-forged", productId: PRO })).success, false);
  assert.equal(subscriptions.hasLifetimePurchase("tok-forged"), false);

  const tierLie = harness({ verify: p => ({ productId: PRO, tier: "premium", catalogVersion: CATALOG, packageName: p, needsAcknowledgement: true }) });
  assert.equal((await tierLie.send("alice", { purchaseToken: "tok-tier", productId: PRO })).success, false);
  assert.equal(subscriptions.hasLifetimePurchase("tok-tier"), false);

  const codeToken = harness({ activationCodes: [{ code: "PREM-SYNTH", purchaseToken: "tok-code" }] });
  assert.equal((await codeToken.send("alice", { purchaseToken: "tok-code", productId: PREMIUM })).success, false);
  assert.equal(codeToken.calls.verify, 0, "a code-backed token never doubles as an in-package right");

  const gift = await base.send("alice", { purchaseToken: "tok-gift", productId: "securecall_premium_activation_code" });
  assert.equal(gift.success, false, "activation-code product never takes the lifetime path");
  assert.equal(base.calls.subscriptionVerify, 1);
});

test("rebind only through the policy-approved identity alias authority", async () => {
  const none = harness();
  assert.equal((await none.send("old-id", { purchaseToken: "tok-rebind", productId: PRO })).success, true);
  const unapproved = harness({ aliases: { "new-id": ["old-id"] } });
  assert.equal((await unapproved.send("new-id", { purchaseToken: "tok-rebind", productId: PRO })).success, false,
    "rebindAuthority none rejects even a registered alias");

  const approved = harness({ policy: { ...POLICY, rebindAuthority: "identity_registry_alias" }, aliases: { "new-id": ["old-id"] } });
  const moved = await approved.send("new-id", { purchaseToken: "tok-rebind", productId: PRO });
  assert.equal(moved.success, true);
  assert.equal(status("old-id", "tok-rebind"), null, "no duplicate right on the old identity");
  const stranger = harness({ policy: { ...POLICY, rebindAuthority: "identity_registry_alias" } });
  assert.equal((await stranger.send("other-id", { purchaseToken: "tok-rebind", productId: PRO })).success, false);
});

test("persistence and acknowledgement failures grant nothing and stay retryable", async () => {
  fs.rmSync(SUBS_FILE, { force: true });
  fs.mkdirSync(SUBS_FILE); // real atomic-write failure, no persistence mock
  const broken = harness();
  const failed = await broken.send("alice", { purchaseToken: "tok-durable", productId: PRO });
  assert.equal(failed.success, false);
  assert.equal(failed.error, "entitlement_temporarily_unavailable");
  assert.equal(broken.calls.acknowledge, 0, "never acknowledge without a durable record");
  assert.equal(subscriptions.hasLifetimePurchase("tok-durable"), false);
  fs.rmSync(SUBS_FILE, { recursive: true, force: true });
  assert.equal((await broken.send("alice", { purchaseToken: "tok-durable", productId: PRO })).success, true, "retry succeeds");

  let ackFails = true;
  const ackHarness = harness({ acknowledge: () => { if (ackFails) throw new Error("acknowledgement_failed"); } });
  assert.equal((await ackHarness.send("alice", { purchaseToken: "tok-ack", productId: PRO })).success, false);
  assert.equal(status("alice", "tok-ack").reason, "pending_acknowledgement");
  assert.equal((await ackHarness.send("mallory", { purchaseToken: "tok-ack", productId: PRO })).success, false,
    "pending record keeps its binding");
  ackFails = false;
  assert.equal((await ackHarness.send("alice", { purchaseToken: "tok-ack", productId: PRO })).success, true);
  assert.equal(status("alice", "tok-ack").valid, true);
});

test("status proof is finite and policy-bound", () => {
  const now = 1_000_000;
  const record = subscriptions.getLifetimeEntitlement("alice", "tok-grant", PKG, CATALOG);
  assert.equal(billing.lifetimeStatusResponse(record, POLICY, now).expiresAt, now + POLICY.proofTtlMs);
  assert.equal(billing.lifetimeStatusResponse(record, null, now).reason, "lifetime_entitlement_unavailable");
  assert.equal(billing.lifetimeStatusResponse(record, { ...POLICY, offerVersion: "next-offer" }, now).valid, false);
  assert.equal(subscriptions.getLifetimeEntitlement("alice", "tok-grant", "com.securecall.app.premium", CATALOG), null);
});

test("refund/revoke via RTDN uses the same record, deduplicates, and never resurrects", async () => {
  const post = rtdn();
  subscriptions.recordVerifiedSubscription("alice", "tok-monthly", "securecall_pro_monthly", "pro",
    Date.now() + 86400000, PKG, CATALOG);

  assert.equal(await post("sub-on-lifetime", { subscriptionNotification: { purchaseToken: "tok-grant", notificationType: 13 } }), 204);
  assert.equal(status("alice", "tok-grant").valid, true, "subscription events cannot touch lifetime records");

  const voided = { voidedPurchaseNotification: { purchaseToken: "tok-grant", productType: 2, refundType: 1 } };
  assert.equal(await post("void-1", voided), 204);
  assert.equal(status("alice", "tok-grant").reason, "revoked");
  assert.equal(subscriptions.getTier("alice"), "pro", "refund of lifetime keeps the separate monthly right");
  assert.equal(await post("void-1", voided), 204, "duplicate delivery");
  assert.equal(await post("void-2", voided), 204, "redelivery under a new ID is idempotent");

  const replay = harness({ verify: (p, id) => ({ ...billing.resolveOneTimeProduct(p, id), packageName: p, needsAcknowledgement: false }) });
  assert.equal((await replay.send("alice", { purchaseToken: "tok-grant", productId: PREMIUM })).success, false);
  assert.equal(status("alice", "tok-grant").reason, "revoked", "verification replay cannot resurrect");

  assert.equal(await post("otp-cancel", { oneTimeProductNotification: { purchaseToken: "tok-ack", notificationType: 2, sku: PRO } }), 204);
  assert.equal(status("alice", "tok-ack").reason, "revoked");
  assert.equal(await post("otp-purchased", { oneTimeProductNotification: { purchaseToken: "tok-durable", notificationType: 1, sku: PRO } }), 204);
  assert.equal(status("alice", "tok-durable").valid, true, "purchase notifications grant/revoke nothing");

  assert.equal(await post("void-monthly", { voidedPurchaseNotification: { purchaseToken: "tok-monthly", productType: 1, refundType: 1 } }), 204);
  assert.equal(subscriptions.getTier("alice"), "FREE");
  assert.equal(status("alice", "tok-durable").valid, true, "monthly refund keeps lifetime rights");

  const persisted = JSON.parse(fs.readFileSync(SUBS_FILE, "utf8"));
  assert.ok(Object.keys(persisted).every(key => !key.startsWith("inapp:") || !key.includes("tok-")),
    "raw purchase tokens are not used as store keys");
});

test("monthly SUBSCRIPTION_VERIFY behavior is unchanged", async () => {
  const { calls, send } = harness();
  const ack = await send("bob", { purchaseToken: "tok-bob-monthly", productId: "securecall_pro_monthly" });
  assert.equal(ack.success, true);
  assert.equal(calls.verify, 0, "monthly products never reach the one-time verifier");
  assert.equal(subscriptions.getTier("bob"), "pro");
});


const restoredAnswer = (p, id) => ({ ...billing.resolveOneTimeProduct(p, id), packageName: p, needsAcknowledgement: false });

test("unknown-token revoke persists a tombstone first and blocks any later grant, across restart", async () => {
  const post = rtdn({ activationCodes: [{ code: "PREM-CODE", purchaseToken: "tok-code-void" }] });
  assert.equal(subscriptions.hasLifetimePurchase("tok-early"), false);
  assert.equal(await post("void-early", { voidedPurchaseNotification: { purchaseToken: "tok-early", productType: 2, refundType: 1 } }), 204);
  const persisted = Object.values(JSON.parse(fs.readFileSync(SUBS_FILE, "utf8")))
    .find(entry => entry.kind === "play_lifetime" && entry.purchaseToken === "tok-early");
  assert.equal(persisted?.revoked, true, "tombstone is durable before RTDN success");
  assert.equal(persisted.clientId, null, "tombstone is bound to no identity");

  const late = harness();
  const ack = await late.send("alice", { purchaseToken: "tok-early", productId: PRO });
  assert.equal(ack.success, false);
  assert.equal(late.calls.acknowledge, 0, "a tombstoned token is never acknowledged");
  assert.equal(status("alice", "tok-early"), null);

  assert.equal(await post("otp-cancel-early", { oneTimeProductNotification: { purchaseToken: "tok-otp-early", notificationType: 2, sku: PREMIUM } }), 204);
  assert.equal((await late.send("alice", { purchaseToken: "tok-otp-early", productId: PREMIUM })).success, false);

  // Subscription and code-backed voids keep their own semantics (no lifetime tombstone).
  assert.equal(await post("void-sub", { voidedPurchaseNotification: { purchaseToken: "tok-sub-void", productType: 1, refundType: 1 } }), 204);
  assert.equal(subscriptions.hasLifetimePurchase("tok-sub-void"), false);
  assert.equal(await post("void-code", { voidedPurchaseNotification: { purchaseToken: "tok-code-void", productType: 2, refundType: 1 } }), 204);
  assert.equal(subscriptions.hasLifetimePurchase("tok-code-void"), false);

  // Restart: a fresh process loading the same store still refuses the token.
  const child = execFileSync(process.execPath, ["-e", `
    const s = require(${JSON.stringify(path.join(__dirname, "..", "subscriptions"))});
    try {
      s.recordVerifiedLifetime({ clientId: "alice", purchaseToken: "tok-early", productId: ${JSON.stringify(PRO)},
        tier: "pro", packageName: ${JSON.stringify(PKG)}, catalogVersion: ${JSON.stringify(CATALOG)},
        offerVersion: "synthetic-offer", acknowledged: true, allowedSubjects: ["alice"] });
      process.stdout.write("granted");
    } catch (error) { process.stdout.write(error.message); }
  `], { env: { PATH: process.env.PATH, SUBS_FILE }, encoding: "utf8" });
  assert.match(child, /purchase_revoked$/);
});

test("tombstone persistence failure answers RTDN 500 and stays retryable", async () => {
  const post = rtdn();
  const snapshot = fs.readFileSync(SUBS_FILE);
  fs.rmSync(SUBS_FILE, { force: true });
  fs.mkdirSync(SUBS_FILE);
  const voided = { voidedPurchaseNotification: { purchaseToken: "tok-void-retry", productType: 2, refundType: 1 } };
  assert.equal(await post("void-retry", voided), 500);
  assert.equal(subscriptions.hasLifetimePurchase("tok-void-retry"), false, "no in-memory-only tombstone");
  fs.rmSync(SUBS_FILE, { recursive: true, force: true });
  fs.writeFileSync(SUBS_FILE, snapshot);
  assert.equal(await post("void-retry", voided), 204, "same delivery retries successfully");
  assert.equal(subscriptions.hasLifetimePurchase("tok-void-retry"), true);
  assert.equal(await post("void-retry", voided), 204);
});

test("revoke while a stale provider answer is in flight never grants", async () => {
  const pending = deferred();
  const racing = harness({ verify: () => pending.promise });
  const ws = racing.start("alice", { purchaseToken: "tok-stale", productId: PRO });
  await flush();
  assert.equal(racing.calls.verify, 1);
  assert.equal(await rtdn()("void-stale", { voidedPurchaseNotification: { purchaseToken: "tok-stale", productType: 2, refundType: 1 } }), 204);
  pending.resolve(restoredAnswer(PKG, PRO)); // answer fetched before the refund
  await flush();
  const ack = ws.messages[ws.messages.length - 1];
  assert.equal(ack.success, false);
  assert.equal(racing.calls.acknowledge, 0);
  assert.equal(status("alice", "tok-stale"), null);
});

test("revoke during provider acknowledgement sends no success, even for an acknowledged record", async () => {
  assert.equal((await harness().send("alice", { purchaseToken: "tok-ackrace", productId: PRO })).success, true);
  assert.equal(subscriptions.getLifetimeEntitlement("alice", "tok-ackrace", PKG, CATALOG).acknowledged, true);

  const ackGate = deferred();
  const restore = harness({ acknowledge: () => ackGate.promise }); // provider still asks to acknowledge
  const ws = restore.start("alice", { purchaseToken: "tok-ackrace", productId: PRO });
  await flush();
  assert.equal(restore.calls.acknowledge, 1, "handler is parked in the provider acknowledgement");
  assert.equal(await rtdn()("void-ackrace", { voidedPurchaseNotification: { purchaseToken: "tok-ackrace", productType: 2, refundType: 1 } }), 204);
  ackGate.resolve();
  await flush();
  const ack = ws.messages[ws.messages.length - 1];
  assert.equal(ack.success, false, "no success from the stale pre-await record");
  assert.equal(ack.tier, "FREE");
  assert.equal(status("alice", "tok-ackrace").reason, "revoked");

  // Fresh, unacknowledged record revoked during the acknowledgement await.
  const freshGate = deferred();
  const fresh = harness({ acknowledge: () => freshGate.promise });
  const freshWs = fresh.start("alice", { purchaseToken: "tok-ackrace-new", productId: PREMIUM });
  await flush();
  assert.equal(await rtdn()("otp-ackrace", { oneTimeProductNotification: { purchaseToken: "tok-ackrace-new", notificationType: 2, sku: PREMIUM } }), 204);
  freshGate.resolve();
  await flush();
  assert.equal(freshWs.messages[freshWs.messages.length - 1].success, false);
});

test("rebind during acknowledgement leaves no success on the previous identity", async () => {
  const policy = { ...POLICY, rebindAuthority: "identity_registry_alias" };
  assert.equal((await harness({ policy }).send("old-ack", { purchaseToken: "tok-rebind-race", productId: PRO })).success, true);
  const gate = deferred();
  const stale = harness({ policy, acknowledge: () => gate.promise });
  const ws = stale.start("old-ack", { purchaseToken: "tok-rebind-race", productId: PRO });
  await flush();
  const moved = await harness({ policy, aliases: { "new-ack": ["old-ack"] }, verify: restoredAnswer })
    .send("new-ack", { purchaseToken: "tok-rebind-race", productId: PRO });
  assert.equal(moved.success, true);
  gate.resolve();
  await flush();
  assert.equal(ws.messages[ws.messages.length - 1].success, false, "old identity gets no success after rebind");
  assert.equal(status("old-ack", "tok-rebind-race"), null);
  assert.equal(status("new-ack", "tok-rebind-race").valid, true);
});

test("acknowledgement-mark storage failure grants nothing and the retry succeeds", async () => {
  const gate = deferred();
  const flaky = harness({ acknowledge: () => gate.promise });
  const ws = flaky.start("alice", { purchaseToken: "tok-markfail", productId: PRO });
  await flush();
  const snapshot = fs.readFileSync(SUBS_FILE);
  fs.rmSync(SUBS_FILE, { force: true });
  fs.mkdirSync(SUBS_FILE);
  gate.resolve();
  await flush();
  const failed = ws.messages[ws.messages.length - 1];
  assert.equal(failed.success, false);
  assert.equal(failed.error, "entitlement_temporarily_unavailable");
  assert.equal(subscriptions.getLifetimeEntitlement("alice", "tok-markfail", PKG, CATALOG).acknowledged, false,
    "no in-memory acknowledgement without durable storage");
  assert.equal(status("alice", "tok-markfail").reason, "pending_acknowledgement");
  fs.rmSync(SUBS_FILE, { recursive: true, force: true });
  fs.writeFileSync(SUBS_FILE, snapshot);
  assert.equal((await harness().send("alice", { purchaseToken: "tok-markfail", productId: PRO })).success, true);
  assert.equal(status("alice", "tok-markfail").valid, true);
});

test("operator policy file seam: read-only, normalized, absent/invalid/overflow -> unavailable", () => {
  const file = path.join(directory, "lifetime-policy.json");
  const load = (value, env = { GOOGLE_PLAY_LIFETIME_POLICY_FILE: file }) => {
    if (value === undefined) fs.rmSync(file, { force: true });
    else fs.writeFileSync(file, typeof value === "string" ? value : JSON.stringify(value));
    return billing.loadLifetimePolicy(env);
  };
  assert.equal(billing.loadLifetimePolicy({}), null, "no input -> unavailable");
  assert.equal(load(undefined), null, "missing file -> unavailable");
  fs.writeFileSync(file, JSON.stringify(POLICY));
  const before = fs.readFileSync(file, "utf8");
  const loaded = billing.loadLifetimePolicy({ GOOGLE_PLAY_LIFETIME_POLICY_FILE: file });
  assert.deepEqual(JSON.parse(JSON.stringify(loaded)), JSON.parse(JSON.stringify(POLICY)));
  assert.ok(Object.isFrozen(loaded) && Object.isFrozen(loaded.products));
  assert.equal(fs.readFileSync(file, "utf8"), before, "seam never writes the input");
  for (const bad of [
    "{not json",
    "",
    " ".repeat(5000),
    [],
    { ...POLICY, enabled: true },
    { ...POLICY, products: [] },
    { ...POLICY, products: { [PRO]: { packageName: PKG, price: 1 } } },
    { ...POLICY, offerVersion: "has space" },
    { ...POLICY, offerVersion: "x".repeat(129) },
    { ...POLICY, proofTtlMs: 8.64e15 },
    { ...POLICY, proofTtlMs: Number.MAX_SAFE_INTEGER },
    { ...POLICY, proofTtlMs: 1.5 },
    { ...POLICY, proofTtlMs: "60000" },
    { ...POLICY, products: JSON.parse('{"__proto__":{"packageName":"com.securecall.app.free"}}') },
    { ...POLICY, products: { constructor: { packageName: PKG } } },
  ]) {
    assert.equal(load(bad), null, JSON.stringify(bad).slice(0, 80));
  }
  fs.rmSync(file, { force: true });
  fs.mkdirSync(file);
  assert.equal(billing.loadLifetimePolicy({ GOOGLE_PLAY_LIFETIME_POLICY_FILE: file }), null, "directory -> unavailable");
  fs.rmSync(file, { recursive: true, force: true });

  const record = subscriptions.getLifetimeEntitlement("alice", "tok-markfail", PKG, CATALOG);
  assert.equal(billing.lifetimeStatusResponse(record, POLICY, 8.64e15 - 1000).reason, "lifetime_entitlement_unavailable",
    "proof expiry overflow is unavailable, never unbounded");
  for (const now of [NaN, Infinity, -1, Number.MAX_SAFE_INTEGER]) {
    assert.equal(billing.normalizeLifetimePolicy(POLICY, now), null, "invalid proof clock");
  }
  process.env.GOOGLE_PLAY_ALLOWED_PACKAGES = PKG + ",com.securecall.app.premium,com.securecall.app.pro";
  try {
    for (const packageName of ["com.securecall.app.premium", "com.securecall.app.pro"]) {
      assert.equal(billing.normalizeLifetimePolicy({ ...POLICY, products: { [PRO]: { packageName } } }), null,
        "the same-package lifetime seam cannot turn a Direct package into a Play entitlement");
    }
  } finally {
    process.env.GOOGLE_PLAY_ALLOWED_PACKAGES = PKG;
  }
});
