// BACKEND-18 + BACKEND-19 – Session Registry mit Cleanup

const sessions = new Map();
const CLEANUP_MS = 60000; // 60 Sekunden Aufbewahrungszeit nach ENDED/FAILED

// Session States:
// INVITE_SENT
// RINGING
// ACTIVE
// ENDED
// FAILED

function createSession(sessionId, caller, callee) {
  const now = Date.now();

  const entry = {
    sessionId,
    caller,
    callee,
    state: "INVITE_SENT",
    createdAt: now,
    updatedAt: now,
    endedAt: null
  };

  sessions.set(sessionId, entry);
  return entry;
}

function updateSession(sessionId, newState) {
  if (!sessions.has(sessionId)) return null;

  const entry = sessions.get(sessionId);
  entry.state = newState;
  entry.updatedAt = Date.now();

  if (newState === "ENDED" || newState === "FAILED") {
    entry.endedAt = Date.now();
  }

  return entry;
}

function getSession(sessionId) {
  return sessions.get(sessionId) || null;
}

function listSessions() {
  return Array.from(sessions.values());
}

function endSession(sessionId) {
  return updateSession(sessionId, "ENDED");
}

function deleteSession(sessionId) {
  sessions.delete(sessionId);
}

// NEW: Cleanup-Funktion für alte Sessions
function cleanupSessions() {
  const now = Date.now();
  const removed = [];

  for (const [id, entry] of sessions.entries()) {
    if (entry.state === "ENDED" || entry.state === "FAILED") {
      if (entry.endedAt && now - entry.endedAt > CLEANUP_MS) {
        sessions.delete(id);
        removed.push(id);
      }
    }
  }

  return removed; // Liste der geloeschten sessionIds
}

module.exports = {
  createSession,
  updateSession,
  getSession,
  listSessions,
  endSession,
  deleteSession,
  cleanupSessions
};
