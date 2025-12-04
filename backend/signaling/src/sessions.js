/**
 * BACKEND-04 - Session State Engine (MVP)
 *
 * Ziel:
 *  - Jede CALL_INVITE erzeugt eine Session
 *  - Session besitzt sessionId + Status
 *  - Statusänderungen via einfache State Machine
 *  - Keine Persistenz, alles in Memory
 */

const { v4: uuidv4 } = require("uuid");

const SESSION_STATES = {
  NEW: "NEW",
  RINGING: "RINGING",
  CONNECTED: "CONNECTED",
  FAILED: "FAILED",
  CLOSED: "CLOSED",
};

const sessions = new Map(); // sessionId -> sessionObject

function createSession(from, to) {
  const sessionId = uuidv4();

  const session = {
    sessionId,
    from,
    to,
    state: SESSION_STATES.NEW,
    created: Date.now(),
    updated: Date.now(),
  };

  sessions.set(sessionId, session);

  console.log("[SESSION] created:", sessionId, "from:", from, "to:", to);
  return session;
}

function updateSession(sessionId, newState) {
  const session = sessions.get(sessionId);
  if (!session) return null;

  session.state = newState;
  session.updated = Date.now();

  console.log("[SESSION] update:", sessionId, "→", newState);
  return session;
}

function getSession(sessionId) {
  return sessions.get(sessionId) || null;
}

function closeSession(sessionId) {
  const session = sessions.get(sessionId);
  if (!session) return;

  session.state = SESSION_STATES.CLOSED;
  session.updated = Date.now();

  console.log("[SESSION] closed:", sessionId);
}

module.exports = {
  SESSION_STATES,
  createSession,
  updateSession,
  getSession,
  closeSession,
  sessions
};
