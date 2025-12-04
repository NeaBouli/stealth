/**
 * BACKEND-11 — Rate-Limiting System (MVP)
 *
 * Dieses Modul limitiert:
 *  - Anzahl REGISTER pro Minute
 *  - Anzahl INVITE pro Minute
 *  - Anzahl ANSWER pro Minute
 * fuer jeden connId (Client-Verbindung).
 *
 * Integration folgt in Patch 86.
 */

class RateLimiter {
  constructor(options = {}) {
    this.windowMs = options.windowMs || 60000; // 1 Minute
    this.maxRegister = options.maxRegister || 5;
    this.maxInvite = options.maxInvite || 15;
    this.maxAnswer = options.maxAnswer || 20;

    this.state = new Map(); // connId -> counters
  }

  ensureEntry(connId) {
    if (!this.state.has(connId)) {
      this.state.set(connId, {
        register: [],
        invite: [],
        answer: []
      });
    }
    return this.state.get(connId);
  }

  // Entfernt alte Eintraege ausserhalb des Zeitfensters
  cleanArray(arr) {
    const threshold = Date.now() - this.windowMs;
    while (arr.length > 0 && arr[0] < threshold) {
      arr.shift();
    }
  }

  check(connId, type) {
    const entry = this.ensureEntry(connId);
    const now = Date.now();

    if (type === "REGISTER") {
      this.cleanArray(entry.register);
      entry.register.push(now);
      return entry.register.length <= this.maxRegister;
    }

    if (type === "INVITE") {
      this.cleanArray(entry.invite);
      entry.invite.push(now);
      return entry.invite.length <= this.maxInvite;
    }

    if (type === "ANSWER") {
      this.cleanArray(entry.answer);
      entry.answer.push(now);
      return entry.answer.length <= this.maxAnswer;
    }

    return true; // END wird nicht begrenzt
  }

  remove(connId) {
    this.state.delete(connId);
  }
}

module.exports = {
  RateLimiter
};
