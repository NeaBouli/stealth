/**
 * BACKEND-05 — Public Key Directory (PKD)
 *
 * Pseudonymes In-Memory-Verzeichnis fuer oeffentliche Schluessel.
 * Kein Name, keine E-Mail, keine Telefonnummer — nur Key-IDs.
 *
 * Nutzung:
 *   const pkd = require("./pkd");
 *   const entry = pkd.registerKey("base64-public-key...");
 *   const key = pkd.getKey(entry.keyId);
 */

const crypto = require("crypto");

// In-Memory Store: keyId -> { keyId, publicKey, created, updated }
const keys = new Map();

/**
 * Registriert einen Public Key und gibt eine zufaellige 128-Bit Key-ID zurueck.
 */
function registerKey(publicKey) {
  const keyId = crypto.randomBytes(16).toString("hex"); // 128-bit
  const now = Date.now();

  const entry = {
    keyId,
    publicKey,
    created: now,
    updated: now
  };

  keys.set(keyId, entry);
  return entry;
}

/**
 * Gibt den Key-Eintrag fuer eine Key-ID zurueck, oder null.
 */
function getKey(keyId) {
  return keys.get(keyId) || null;
}

/**
 * Rotiert den Public Key fuer eine bestehende Key-ID.
 * Gibt den aktualisierten Eintrag zurueck, oder null falls nicht gefunden.
 */
function rotateKey(keyId, newPublicKey) {
  const entry = keys.get(keyId);
  if (!entry) return null;

  entry.publicKey = newPublicKey;
  entry.updated = Date.now();

  return entry;
}

/**
 * Entfernt einen Key-Eintrag. Gibt true zurueck wenn gefunden, sonst false.
 */
function deleteKey(keyId) {
  return keys.delete(keyId);
}

/**
 * Gibt alle Key-IDs zurueck (ohne die Schluessel selbst — nur fuer Debug).
 */
function listKeys() {
  return Array.from(keys.keys());
}

module.exports = {
  registerKey,
  getKey,
  rotateKey,
  deleteKey,
  listKeys
};
