/**
 * BACKEND-10 — Session Metrics & Timeline Tracker (MVP)
 *
 * Dieses Modul sammelt Metriken fuer jeden Call:
 *  - Startzeit
 *  - Endzeit
 *  - Call-Dauer
 *  - Timeline-Ereignisse (REGISTER, INVITE, ANSWER, END, TIMEOUT)
 *
 * Integration folgt in Patch 83.
 */

class SessionMetrics {
  constructor() {
    this.sessions = new Map(); // sessionId -> session data
  }

  startSession(sessionId, initialEvent) {
    const now = Date.now();

    this.sessions.set(sessionId, {
      sessionId,
      startedAt: now,
      endedAt: null,
      timeline: [
        { ts: now, event: initialEvent }
      ]
    });
  }

  addEvent(sessionId, event) {
    const s = this.sessions.get(sessionId);
    if (!s) return;

    s.timeline.push({
      ts: Date.now(),
      event
    });
  }

  endSession(sessionId, finalEvent) {
    const s = this.sessions.get(sessionId);
    if (!s) return null;

    const now = Date.now();

    s.timeline.push({
      ts: now,
      event: finalEvent
    });

    s.endedAt = now;

    const result = {
      sessionId: s.sessionId,
      startedAt: s.startedAt,
      endedAt: s.endedAt,
      durationMs: s.endedAt - s.startedAt,
      timeline: s.timeline
    };

    this.sessions.delete(sessionId);
    return result;
  }

  // Sanitaet: Session manuell loeschen
  remove(sessionId) {
    this.sessions.delete(sessionId);
  }
}

module.exports = {
  SessionMetrics
};
