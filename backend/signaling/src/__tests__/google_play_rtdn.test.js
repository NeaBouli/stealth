"use strict";

const assert = require("assert");
const express = require("express");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { installGooglePlayRtdnRoute } = require("../payments/google_play_rtdn");

function envelope(messageId, notification) {
  return {
    message: {
      messageId,
      data: Buffer.from(JSON.stringify(notification), "utf8").toString("base64")
    },
    subscription: "projects/test/subscriptions/securecall"
  };
}

async function run() {
  const testDir = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-rtdn-"));
  process.env.GOOGLE_PLAY_RTDN_FILE = path.join(testDir, "processed.json");
  process.env.GOOGLE_PLAY_RTDN_AUDIENCE = "https://api.example.test/billing/google-play-rtdn";
  process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL = "rtdn@example.test";
  process.env.GOOGLE_PLAY_ALLOWED_PACKAGES = "com.securecall.app.free";

  const calls = { expire: 0, refresh: 0, activationSave: 0, giftSave: 0, identity: 0, verify: 0 };
  const activationCodes = [{ code: "PREM-TEST", purchaseToken: "void-token" }];
  const giftCodes = new Map([["OLD-GIFT", { purchaseToken: "void-token" }]]);
  const app = express();
  app.use(express.json());
  installGooglePlayRtdnRoute(app, {
    subscriptions: {
      expireByPurchaseToken(token) { calls.expire += 1; calls.lastExpired = token; return 1; },
      refreshByPurchaseToken(token, productId, tier, expiresAt) {
        calls.refresh += 1;
        calls.lastRefresh = { token, productId, tier, expiresAt };
        return 1;
      }
    },
    activationCodes,
    saveActivationCodes() { calls.activationSave += 1; },
    giftCodes,
    saveGiftCodes() { calls.giftSave += 1; },
    async verifyPushIdentity(req, audience, email) {
      calls.identity += 1;
      assert.strictEqual(audience, process.env.GOOGLE_PLAY_RTDN_AUDIENCE);
      assert.strictEqual(email, process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL);
      assert.strictEqual(req.headers.authorization, "Bearer signed-test-token");
    },
    async verifyPlaySubscriptionToken(packageName, token) {
      calls.verify += 1;
      assert.strictEqual(packageName, "com.securecall.app.free");
      assert.strictEqual(token, "sub-token");
      return { productId: "securecall_pro_monthly", tier: "pro", expiresAt: Date.now() + 86400000 };
    }
  });

  const server = app.listen(0);
  await new Promise(resolve => server.once("listening", resolve));
  const url = `http://127.0.0.1:${server.address().port}/billing/google-play-rtdn`;
  const post = body => fetch(url, {
    method: "POST",
    headers: { "content-type": "application/json", authorization: "Bearer signed-test-token" },
    body: JSON.stringify(body)
  });

  try {
    const active = envelope("msg-active", {
      version: "1.0",
      packageName: "com.securecall.app.free",
      subscriptionNotification: { version: "1.0", notificationType: 2, purchaseToken: "sub-token" }
    });
    assert.strictEqual((await post(active)).status, 204);
    assert.strictEqual(calls.refresh, 1);
    assert.strictEqual(calls.verify, 1);

    assert.strictEqual((await post(active)).status, 204, "duplicate Pub/Sub message must be acknowledged");
    assert.strictEqual(calls.refresh, 1, "duplicate must not be processed twice");

    const terminal = envelope("msg-expired", {
      version: "1.0",
      packageName: "com.securecall.app.free",
      subscriptionNotification: { version: "1.0", notificationType: 13, purchaseToken: "expired-token" }
    });
    assert.strictEqual((await post(terminal)).status, 204);
    assert.strictEqual(calls.lastExpired, "expired-token");

    const voided = envelope("msg-voided", {
      version: "1.0",
      packageName: "com.securecall.app.free",
      voidedPurchaseNotification: { purchaseToken: "void-token", productType: 2, refundType: 1 }
    });
    assert.strictEqual((await post(voided)).status, 204);
    assert.strictEqual(activationCodes.length, 0);
    assert.strictEqual(giftCodes.size, 0);
    assert.strictEqual(calls.activationSave, 1);
    assert.strictEqual(calls.giftSave, 1);

    const unsupported = envelope("msg-evil", {
      version: "1.0",
      packageName: "evil.package",
      testNotification: { version: "1.0" }
    });
    assert.strictEqual((await post(unsupported)).status, 400);
  } finally {
    await new Promise(resolve => server.close(resolve));
    fs.rmSync(testDir, { recursive: true, force: true });
  }
}

run().then(() => console.log("google_play_rtdn.test.js ok"));
