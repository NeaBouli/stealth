// BACKEND-17 – Rate Limit & Flood Control

// Einstellungen
const WINDOW_MS = 10000;          // 10 Sekunden Fenster
const MAX_EVENTS = 40;            // max. 40 signaling messages in 10 Sekunden
const MAX_BINARY_EVENTS = 1000;   // max. 1000 binary frames in 10 Sekunden (~100/s)

// Map: connId => { text: number[], binary: number[] }
const buckets = new Map();

function ensureBucket(connId) {
  if (!buckets.has(connId)) {
    buckets.set(connId, { text: [], binary: [] });
  }
  return buckets.get(connId);
}

function registerEvent(connId) {
  const now = Date.now();
  const bucket = ensureBucket(connId);
  bucket.text.push(now);

  // alte Einträge entfernen
  while (bucket.text.length && now - bucket.text[0] > WINDOW_MS) {
    bucket.text.shift();
  }

  // Limit prüfen
  if (bucket.text.length > MAX_EVENTS) {
    return false; // Rate Limit verletzt
  }

  return true; // OK
}

function registerBinaryEvent(connId) {
  const now = Date.now();
  const bucket = ensureBucket(connId);
  bucket.binary.push(now);

  while (bucket.binary.length && now - bucket.binary[0] > WINDOW_MS) {
    bucket.binary.shift();
  }

  if (bucket.binary.length > MAX_BINARY_EVENTS) {
    return false;
  }

  return true;
}

function clear(connId) {
  buckets.delete(connId);
}

module.exports = {
  registerEvent,
  registerBinaryEvent,
  clear
};
