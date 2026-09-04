"use strict";

const fs = require("fs");
const path = require("path");
const { writeJsonAtomic } = require("../utils/json_store");
const { verifyPlaySubscriptionToken } = require("./google_play_billing");

const MAX_DATA_BYTES = 64 * 1024;
const TERMINAL_SUBSCRIPTION_TYPES = new Set([5, 10, 12, 13, 20]);

function allowedPackages() {
  return new Set(String(process.env.GOOGLE_PLAY_ALLOWED_PACKAGES || "com.securecall.app.free")
    .split(",").map(value => value.trim()).filter(Boolean));
}

function loadProcessed(filePath) {
  try {
    const value = JSON.parse(fs.readFileSync(filePath, "utf8"));
    return new Set(Array.isArray(value.messageIds) ? value.messageIds : []);
  } catch {
    return new Set();
  }
}

function saveProcessed(filePath, processed) {
  const messageIds = Array.from(processed).slice(-5000);
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  writeJsonAtomic(filePath, { messageIds });
}

function revokeActivationByPurchaseToken(activationCodes, purchaseToken) {
  let revoked = 0;
  for (let index = activationCodes.length - 1; index >= 0; index -= 1) {
    if (activationCodes[index]?.purchaseToken !== purchaseToken) continue;
    activationCodes.splice(index, 1);
    revoked += 1;
  }
  return revoked;
}

function revokeGiftByPurchaseToken(giftCodes, purchaseToken) {
  let revoked = 0;
  for (const [code, record] of giftCodes) {
    if (record?.purchaseToken !== purchaseToken) continue;
    giftCodes.delete(code);
    revoked += 1;
  }
  return revoked;
}

async function verifyPushIdentity(req, audience, expectedEmail) {
  const match = /^Bearer ([^\s]+)$/.exec(String(req.headers.authorization || ""));
  if (!match) throw new Error("missing_bearer_token");
  const { OAuth2Client } = require("google-auth-library");
  const ticket = await new OAuth2Client().verifyIdToken({ idToken: match[1], audience });
  const claims = ticket.getPayload();
  if (!claims || claims.email !== expectedEmail || claims.email_verified !== true) {
    throw new Error("unexpected_push_identity");
  }
}

function decodeNotification(body) {
  const message = body?.message;
  if (!message || typeof message.messageId !== "string" || !/^[A-Za-z0-9_-]{1,160}$/.test(message.messageId)) {
    throw new Error("invalid_pubsub_message");
  }
  if (typeof message.data !== "string" || Buffer.byteLength(message.data, "base64") > MAX_DATA_BYTES) {
    throw new Error("invalid_pubsub_data");
  }
  let notification;
  try {
    notification = JSON.parse(Buffer.from(message.data, "base64").toString("utf8"));
  } catch {
    throw new Error("invalid_developer_notification");
  }
  if (!notification || !allowedPackages().has(notification.packageName)) {
    throw new Error("unsupported_package");
  }
  return { messageId: message.messageId, notification };
}

function installGooglePlayRtdnRoute(app, deps) {
  const filePath = process.env.GOOGLE_PLAY_RTDN_FILE || path.join(process.cwd(), "data", "google_play_rtdn.json");
  const processed = loadProcessed(filePath);

  app.post("/billing/google-play-rtdn", async (req, res) => {
    const audience = String(process.env.GOOGLE_PLAY_RTDN_AUDIENCE || "").trim();
    const expectedEmail = String(process.env.GOOGLE_PLAY_RTDN_SERVICE_ACCOUNT_EMAIL || "").trim();
    if (!audience || !expectedEmail) return res.status(503).json({ error: "google_play_rtdn_not_configured" });

    try {
      await (deps.verifyPushIdentity || verifyPushIdentity)(req, audience, expectedEmail);
    } catch {
      return res.status(401).json({ error: "invalid_push_identity" });
    }

    let decoded;
    try {
      decoded = decodeNotification(req.body);
    } catch (error) {
      return res.status(400).json({ error: error.message });
    }
    if (processed.has(decoded.messageId)) return res.sendStatus(204);

    const value = decoded.notification;
    try {
      if (value.subscriptionNotification) {
        const event = value.subscriptionNotification;
        if (!event.purchaseToken || !Number.isInteger(event.notificationType)) throw new Error("invalid_subscription_notification");
        if (TERMINAL_SUBSCRIPTION_TYPES.has(event.notificationType)) {
          deps.subscriptions.expireByPurchaseToken(event.purchaseToken);
        } else {
          const verified = await (deps.verifyPlaySubscriptionToken || verifyPlaySubscriptionToken)(value.packageName, event.purchaseToken);
          deps.subscriptions.refreshByPurchaseToken(
            event.purchaseToken,
            verified.productId,
            verified.tier,
            verified.expiresAt,
            value.packageName,
            verified.catalogVersion
          );
        }
      } else if (value.voidedPurchaseNotification) {
        const event = value.voidedPurchaseNotification;
        if (!event.purchaseToken || ![1, 2].includes(event.refundType)) throw new Error("invalid_voided_purchase");
        deps.subscriptions.expireByPurchaseToken(event.purchaseToken);

        const activationSnapshot = deps.activationCodes.slice();
        const activationRevoked = revokeActivationByPurchaseToken(deps.activationCodes, event.purchaseToken);
        if (activationRevoked > 0) {
          try {
            if (deps.saveActivationCodes() === false) throw new Error("activation_revocation_persistence_failed");
          } catch (error) {
            deps.activationCodes.splice(0, deps.activationCodes.length, ...activationSnapshot);
            throw error;
          }
        }

        const giftSnapshot = new Map(deps.giftCodes);
        const giftRevoked = revokeGiftByPurchaseToken(deps.giftCodes, event.purchaseToken);
        if (giftRevoked > 0) {
          try {
            if (deps.saveGiftCodes() === false) throw new Error("gift_revocation_persistence_failed");
          } catch (error) {
            deps.giftCodes.clear();
            for (const [code, record] of giftSnapshot) deps.giftCodes.set(code, record);
            throw error;
          }
        }
      } else if (!value.testNotification && !value.oneTimeProductNotification) {
        throw new Error("unsupported_notification");
      }

      processed.add(decoded.messageId);
      saveProcessed(filePath, processed);
      return res.sendStatus(204);
    } catch (error) {
      console.error("[GOOGLE-PLAY-RTDN] Processing failed:", error.message);
      return res.status(500).json({ error: "google_play_rtdn_processing_failed" });
    }
  });
}

module.exports = {
  decodeNotification,
  installGooglePlayRtdnRoute,
  revokeActivationByPurchaseToken,
  revokeGiftByPurchaseToken
};
