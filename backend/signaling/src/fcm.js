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

function isInitialized() {
  return initialized;
}

module.exports = { initFcm, sendCallInvitePush, isInitialized };
