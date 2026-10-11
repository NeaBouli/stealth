/**
 * Firebase Cloud Messaging module for push notifications.
 *
 * Sends CALL_INVITE push notifications to offline peers.
 * Requires FIREBASE_SERVICE_ACCOUNT_KEY env var pointing to the
 * service account JSON file path.
 */

let admin = null;
let initialized = false;

function initFcm() {
  // Support two modes: Base64-encoded JSON (Railway) or file path (local dev)
  const base64Key = process.env.FIREBASE_SERVICE_ACCOUNT_BASE64;
  const keyPath = process.env.FIREBASE_SERVICE_ACCOUNT_KEY;

  if (!base64Key && !keyPath) {
    console.warn("[FCM] No Firebase credentials set — FCM disabled");
    console.warn("[FCM] Set FIREBASE_SERVICE_ACCOUNT_BASE64 (Railway) or FIREBASE_SERVICE_ACCOUNT_KEY (file path)");
    return;
  }

  try {
    const firebaseAdmin = require("firebase-admin");
    let serviceAccount;

    if (base64Key) {
      serviceAccount = JSON.parse(
        Buffer.from(base64Key, "base64").toString("utf8")
      );
      console.log("[FCM] Using Base64-encoded service account");
    } else {
      serviceAccount = require(keyPath);
      console.log("[FCM] Using file-based service account:", keyPath);
    }

    firebaseAdmin.initializeApp({
      credential: firebaseAdmin.credential.cert(serviceAccount),
    });

    admin = firebaseAdmin;
    initialized = true;
    console.log("[FCM] Firebase Admin SDK initialized successfully");
  } catch (err) {
    console.error("[FCM] Failed to initialize:", err.message);
  }
}

/**
 * Send a CALL_INVITE push notification to a device.
 *
 * @param {string} fcmToken - The target device's FCM token
 * @param {string} sessionId - The call session ID
 * @param {string} callerClientId - The caller's client ID
 * @param {string} callerPhone - Optional caller phone for local contact resolution
 * @returns {Promise<boolean>} true if sent successfully
 */
async function sendCallInvitePush(fcmToken, sessionId, callerClientId, callerPhone = "") {
  if (!initialized || !admin) {
    console.warn("[FCM] Not initialized, cannot send push");
    return false;
  }

  const message = {
    token: fcmToken,
    data: {
      type: "CALL_INVITE",
      sessionId: sessionId,
      callerName: callerClientId,
      callerClientId: callerClientId,
      callerPhone: callerPhone,
    },
    android: {
      priority: "high",
      ttl: 30000, // 30 seconds — calls are time-sensitive
    },
  };

  try {
    const response = await admin.messaging().send(message);
    console.log("[FCM] Push sent successfully:", response);
    return true;
  } catch (err) {
    console.error("[FCM] Failed to send push:", err.message);
    return false;
  }
}

/** Deliver a one-time legacy identity migration challenge to the device that
 * already owns the persisted FCM route. The challenge contains no secret and
 * is useful only with the matching non-exportable installation private key. */
async function sendIdentityMigrationChallenge(fcmToken, payload) {
  if (!initialized || !admin) return false;
  const required = ["challengeId", "challenge", "requestedClientId", "identityId", "expiresAt"];
  if (!payload || required.some(key => typeof payload[key] !== "string" || !payload[key])) return false;
  try {
    await admin.messaging().send({
      token: fcmToken,
      data: {
        type: "IDENTITY_MIGRATION_CHALLENGE",
        challengeId: payload.challengeId,
        challenge: payload.challenge,
        requestedClientId: payload.requestedClientId,
        identityId: payload.identityId,
        expiresAt: payload.expiresAt,
      },
      android: { priority: "high", ttl: 300000 },
    });
    console.log("[IDENTITY] Migration challenge push delivered");
    return true;
  } catch (error) {
    console.warn("[IDENTITY] Migration challenge push failed:", error.message);
    return false;
  }
}

async function sendAuthenticatedCallInvitePush(fcmToken, payload) {
  if (!initialized || !admin) return false;
  const fields = ["sessionId", "from", "fromIdentityId", "to", "requestedTo", "ephemeralPublicKey",
    "identityPublicKey", "issuedAt", "nonce", "signature", "inviteDigest"];
  if (!payload || fields.some(key => typeof payload[key] !== "string" || !payload[key])) return false;
  try {
    await admin.messaging().send({
      token: fcmToken,
      data: { type: "CALL_INVITE_V2", ...Object.fromEntries(fields.map(key => [key, payload[key]])),
        callerPhone: typeof payload.callerPhone === "string" ? payload.callerPhone : "" },
      android: { priority: "high", ttl: 30000 },
    });
    return true;
  } catch (error) {
    console.warn("[FCM] Authenticated call push failed:", error.message);
    return false;
  }
}

function isInitialized() {
  return initialized;
}

module.exports = {
  initFcm,
  sendCallInvitePush,
  sendIdentityMigrationChallenge,
  sendAuthenticatedCallInvitePush,
  isInitialized,
};
