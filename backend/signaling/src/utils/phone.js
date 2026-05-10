"use strict";

const crypto = require("crypto");

function normalizePhone(num) {
  if (typeof num !== "string") return "";
  let normalized = num.replace(/[\s\-().\[\]/]/g, "");
  if (normalized.startsWith("00")) {
    normalized = "+" + normalized.substring(2);
  }
  normalized = normalized.replace(/[^0-9+]/g, "");
  return normalized;
}

function hashPhone(normalizedPhone) {
  return crypto.createHash("sha256").update(normalizedPhone).digest("hex");
}

module.exports = { normalizePhone, hashPhone };
