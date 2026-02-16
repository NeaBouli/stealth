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
  const keyPath = process.env.FIREBASE_SERVICE_ACCOUNT_KEY;
  if (!keyPath) {
    console.warn("[FCM] FIREBASE_SERVICE_ACCOUNT_KEY not set — FCM disabled");
    return;
  }

  try {
    const firebaseAdmin = require("firebase-admin");
    const serviceAccount = require(keyPath);

    firebaseAdmin.initializeApp({
      credential: firebaseAdmin.credential.cert(serviceAccount),
    });

    admin = firebaseAdmin;
    initialized = true;
    console.log("[FCM] Firebase Admin SDK initialized");
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
 * @returns {Promise<boolean>} true if sent successfully
 */
async function sendCallInvitePush(fcmToken, sessionId, callerClientId) {
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
