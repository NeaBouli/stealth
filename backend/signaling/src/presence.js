// Presence Cache (BACKEND-15 mit Timeout Cleanup)

const presence = new Map();
let lastSnapshot = new Set();

const TIMEOUT_MS = 15000; // 15 Sekunden Inaktivität

function setOnline(connId) {
  const now = Date.now();
  presence.set(connId, {
    firstSeen: now,
    lastSeen: now
  });
}

function updateActivity(connId) {
  if (presence.has(connId)) {
    presence.get(connId).lastSeen = Date.now();
  }
}

function setOffline(connId) {
  presence.delete(connId);
}

function getOnlineList() {
  return Array.from(presence.keys());
}

// Delta tracking
function getDelta() {
  const current = new Set(presence.keys());
  const added = [];
  const removed = [];

  for (const id of current) {
    if (!lastSnapshot.has(id)) added.push(id);
  }
  for (const id of lastSnapshot) {
    if (!current.has(id)) removed.push(id);
  }

  lastSnapshot = current;
  return { added, removed };
}

// NEW: Timeout-basierte Bereinigung von toten Sessions
function cleanupTimeouts() {
  const now = Date.now();
  const removed = [];

  for (const [connId, info] of presence.entries()) {
    const diff = now - info.lastSeen;
    if (diff > TIMEOUT_MS) {
      presence.delete(connId);
      removed.push(connId);
    }
  }

  return removed; // Liste der entfernten IDs
}

module.exports = {
  setOnline,
  updateActivity,
  setOffline,
  getOnlineList,
  getDelta,
  cleanupTimeouts
};
