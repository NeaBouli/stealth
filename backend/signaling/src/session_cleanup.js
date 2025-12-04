/**
 * BACKEND-05 – Session Cleanup und Parallel-Call-Schutz (MVP)
 *
 * Dieses Modul:
 *  - entfernt alte Sessions automatisch
 *  - verhindert mehrfache parallele Calls desselben Nutzers
 *  - führt einfache TTL-Logik ein
 */

const SESSION_TTL_MS = 30000; // 30 Sekunden bis automatische Entfernung

function startSessionCleanup(sessions, closeSession) {
  setInterval(() => {
    const now = Date.now();

    for (const [sessionId, session] of sessions.entries()) {
      const age = now - session.updated;

      if (age > SESSION_TTL_MS) {
        console.log("[SESSION-CLEANUP] removing old session:", sessionId);

        closeSession(sessionId);
        sessions.delete(sessionId);
      }
    }
  }, 5000); // alle 5 Sekunden prüfen
}


/**
 * Check: Darf der Nutzer einen neuen Call starten?
 *
 * Es wird verhindert:
 *  - mehrere parallele RINGING-Sessions von derselben Quelle
 */
function canStartCall(sessions, fromUser) {
  for (const session of sessions.values()) {
    if (session.from === fromUser) {
      if (session.state === "NEW" || session.state === "RINGING") {
        return false;
      }
    }
  }
  return true;
}

module.exports = {
  startSessionCleanup,
  canStartCall
};
