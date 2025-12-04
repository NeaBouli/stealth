// BACKEND-17 – Rate Limit & Flood Control

// Einstellungen
const WINDOW_MS = 10000;      // 10 Sekunden Fenster
const MAX_EVENTS = 40;        // max. 40 Messages in 10 Sekunden

// Map: connId => Array von Timestamps
const buckets = new Map();

function registerEvent(connId) {
  const now = Date.now();

  if (!buckets.has(connId)) {
    buckets.set(connId, []);
  }

  const arr = buckets.get(connId);
  arr.push(now);

  // alte Einträge entfernen
  while (arr.length && now - arr[0] > WINDOW_MS) {
    arr.shift();
  }

  // Limit prüfen
  if (arr.length > MAX_EVENTS) {
    return false; // Rate Limit verletzt
  }

  return true; // OK
}

function clear(connId) {
  buckets.delete(connId);
}

module.exports = {
  registerEvent,
  clear
};
