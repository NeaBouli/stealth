# BACKEND-02 – Stable Signaling & STUN/TURN Integration

## Ziel
Verbesserung der MVP-Version zur stabilen Produktionsvorstufe:

1. stabile Session-Verwaltung
2. Heartbeats & Timeouts
3. STUN/TURN Integration (Coturn)
4. Cleanup alter Sessions

---

## Erwartetes Ergebnis

Ein stabiler Signaling-Dienst mit:

- automatischem Session-Cleanup  
- Heartbeat-Überwachung (z. B. alle 10–20 Sekunden)  
- Integration von Coturn:
  - Bereitstellung von STUN/TURN Credentials
  - Weiterleitung an Android/WebRTC
  
- klaren Fehlercodes & Timeouts:
  - INVITE_TIMEOUT
  - ANSWER_TIMEOUT
  - HEARTBEAT_LOST
  - SESSION_EXPIRED

---

## Funktionen

### 1. Coturn Integration
- einfache Konfig:
turn.example.com:3478
stun.example.com:3478

yaml
Code kopieren
- Auth: "long-term credentials"

### 2. Session Lifecycle
- INVITE → pending
- ANSWER → active
- TIMEOUT → closed
- CANCEL → closed

### 3. Heartbeats
- jeder Client sendet alle 10–20s WS-Ping
- bei ausbleiben → Session invalid

---

## Tests

- Call bricht korrekt ab, wenn B nicht antwortet
- Call bricht ab, wenn Heartbeat fehlt
- Server startet und stoppt sauber
- TURN-/STUN-Server erreichbar

---

## Developer FAQ

**Frage:** Müssen wir TLS für TURN sofort nutzen?  
**Antwort:** Für MVP nicht zwingend. Für Produktiv später ja.

**Frage:** Welche Cleanup-Intervalle?  
**Antwort:** 60–120 Sekunden idle.

**Frage:** Muss der Server horizontal skalieren?  
**Antwort:** Nein, noch nicht – das kommt in Phase 3+.

