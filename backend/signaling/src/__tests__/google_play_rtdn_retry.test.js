"use strict";

// Synthetic route tests: no HTTP listener, provider client or installed dependencies.
const assert = require("node:assert/strict");
const fs = require("node:fs");
const os = require("node:os");
const path = require("node:path");
const { test } = require("node:test");
const { installGooglePlayRtdnRoute } = require("../payments/google_play_rtdn");

test("RTDN retries revocation after processed-ID persistence fails, then deduplicates across restart", async () => {
  const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-rtdn-retry-"));
  const filePath = path.join(directory, "processed.json");
  const env = {
    GOOGLE_PLAY_RTDN_FILE: filePath,
    GOOGLE_PLAY_RTDN_AUDIENCE: "https://example.test/rtdn",
    GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL: "rtdn@example.test",
    GOOGLE_PLAY_ALLOWED_PACKAGES: "com.securecall.app.free"
  };
  const previous = Object.fromEntries(Object.keys(env).map(key => [key, process.env[key]]));
  Object.assign(process.env, env);
  const activations = [{ code: "synthetic-code", purchaseToken: "synthetic-purchase" }];
  let expirations = 0;
  let saves = 0;
  const deps = {
    subscriptions: { expireByPurchaseToken() { expirations++; }, revokeLifetimeByPurchaseToken() { return 0; } },
    activationCodes: activations,
    saveActivationCodes() { saves++; return true; },
    giftCodes: new Map(),
    saveGiftCodes() { return true; },
    async verifyPushIdentity() {}
  };
  const install = () => {
    let handler;
    installGooglePlayRtdnRoute({ post(route, callback) {
      assert.equal(route, "/billing/google-play-rtdn");
      handler = callback;
    } }, deps);
    return handler;
  };
  const invoke = async handler => {
    const response = {
      statusCode: 200,
      status(code) { this.statusCode = code; return this; },
      json(body) { this.body = body; return this; },
      sendStatus(code) { this.statusCode = code; return this; }
    };
    await handler({ body: { message: {
      messageId: "synthetic-message",
      data: Buffer.from(JSON.stringify({
        packageName: "com.securecall.app.free",
        voidedPurchaseNotification: { purchaseToken: "synthetic-purchase", refundType: 1, productType: 2 }
      })).toString("base64")
    } } }, response);
    return response;
  };

  try {
    const handler = install();
    fs.mkdirSync(filePath); // Force atomic rename to fail, without mocking persistence.
    assert.equal((await invoke(handler)).statusCode, 500);
    assert.equal(activations.length, 0, "revocation was already durably saved");
    assert.equal(saves, 1);
    fs.rmdirSync(filePath);

    assert.equal((await invoke(handler)).statusCode, 204);
    assert.equal(expirations, 2, "failed processed-ID write must not suppress same-process retry");
    assert.deepEqual(JSON.parse(fs.readFileSync(filePath, "utf8")), {
      messageIds: ["synthetic-message"]
    });
    assert.equal(saves, 1, "idempotent retry does not recreate revoked activations");

    assert.equal((await invoke(handler)).statusCode, 204);
    assert.equal(expirations, 2, "successful processing deduplicates in memory");
    assert.equal((await invoke(install())).statusCode, 204);
    assert.equal(expirations, 2, "durable processed ID deduplicates after restart");
  } finally {
    fs.rmSync(directory, { recursive: true, force: true });
    for (const [key, value] of Object.entries(previous)) {
      if (value === undefined) delete process.env[key];
      else process.env[key] = value;
    }
  }
});
