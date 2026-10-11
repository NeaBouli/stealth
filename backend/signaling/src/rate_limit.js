// BACKEND-17 – Rate Limit & Flood Control

// Einstellungen
const WINDOW_MS = 10000;          // 10 Sekunden Fenster
const MAX_EVENTS = 40;            // max. 40 signaling messages in 10 Sekunden
const MAX_BINARY_EVENTS = 1000;   // max. 1000 binary frames in 10 Sekunden (~100/s)

// STX-17: bounded eviction. Buckets are cleared on disconnect (clear()), but an
// abnormal close must not leak the entry forever: buckets idle for longer than
// IDLE_EVICT_MS are pruned, and the map is hard-capped (least-recently-active
// bucket evicted first). Limits themselves are unchanged.
const MAX_BUCKETS = 10000;
const IDLE_EVICT_MS = WINDOW_MS * 6; // 60s ohne Event => verwaister Bucket

// Map: connId => { text: number[], binary: number[], lastSeen: number }
const buckets = new Map();

function pruneIdle(at) {
  for (const [connId, bucket] of buckets) {
    if (at - bucket.lastSeen > IDLE_EVICT_MS) buckets.delete(connId);
  }
}

function evictLeastRecent() {
  let oldestId;
  let oldestSeen = Infinity;
  for (const [connId, bucket] of buckets) {
    if (bucket.lastSeen < oldestSeen) {
      oldestSeen = bucket.lastSeen;
      oldestId = connId;
    }
  }
  if (oldestId !== undefined) buckets.delete(oldestId);
}

function ensureBucket(connId) {
  if (!buckets.has(connId)) {
    if (buckets.size >= MAX_BUCKETS) {
      pruneIdle(Date.now());
      if (buckets.size >= MAX_BUCKETS) evictLeastRecent();
    }
    buckets.set(connId, { text: [], binary: [], lastSeen: 0 });
  }
  return buckets.get(connId);
}

function registerEvent(connId) {
  const now = Date.now();
  const bucket = ensureBucket(connId);
  bucket.lastSeen = now;
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
  bucket.lastSeen = now;
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

// Narrow introspection for tests/debugging: aggregate count only, no ids.
function bucketCount() {
  return buckets.size;
}

module.exports = {
  registerEvent,
  registerBinaryEvent,
  clear,
  bucketCount,
  _pruneIdle: pruneIdle
};
