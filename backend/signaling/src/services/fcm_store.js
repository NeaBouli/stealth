"use strict";

const { writeJsonAtomic } = require("../utils/json_store");

const FCM_TOKENS_FILE = process.env.FCM_TOKENS_FILE
  || require("path").join(__dirname, "../../data/fcm_tokens.json");

const fcmTokens = new Map();

function loadFcmTokens() {
  try {
    const fs = require("fs");
    if (fs.existsSync(FCM_TOKENS_FILE)) {
      const data = JSON.parse(fs.readFileSync(FCM_TOKENS_FILE, "utf8"));
      for (const [k, v] of Object.entries(data)) {
        fcmTokens.set(k, v);
      }
      console.log(`[FCM] Loaded ${fcmTokens.size} persisted tokens`);
    }
  } catch (e) {
    console.warn("[FCM] Could not load persisted tokens:", e.message);
  }
}

function saveFcmTokens() {
  try {
    const obj = {};
    for (const [k, v] of fcmTokens) obj[k] = v;
    writeJsonAtomic(FCM_TOKENS_FILE, obj);
  } catch (e) {
    console.error("[FCM] Failed to persist tokens:", e.message);
  }
}

module.exports = { fcmTokens, loadFcmTokens, saveFcmTokens };
