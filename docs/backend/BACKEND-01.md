# BACKEND-01 — Signaling Server MVP

Minimaler Signaling Server für SecureCall.
Phase 1 der Backend-Implementierung.

---

## 1. Ziele

- HTTP-Endpoint `/` für Health-Check
- WebSocket-Endpoint `/signal`
- Echo-Mechanismus zur Validierung der Verbindung
- Projektstruktur anlegen: `backend/signaling/`
- Dependencies installieren und lauffähig machen

---

## 2. Dateien (BACKEND-01)

backend/signaling/package.json
backend/signaling/.gitignore
backend/signaling/package-lock.json
backend/signaling/README.md
backend/signaling/src/server.js

yaml
Code kopieren

---

## 3. Start (lokal)

cd backend/signaling
npm install
npm run dev

makefile
Code kopieren

Output:

[SIGNAL] server listening on port 8080

yaml
Code kopieren

---

## 4. Bekannte Fehler & Fixes

### Fehler: Port bereits belegt
Error: listen EADDRINUSE: address already in use :::8080

makefile
Code kopieren

**Fix:**
Anderen Prozess beenden:
lsof -i :8080
kill -9 <PID>

yaml
Code kopieren

---

### Fehler: Missing modules nach `git clone`
**Fix:**
cd backend/signaling
npm install

yaml
Code kopieren

---

### Fehler: WS-Client kann sich nicht verbinden
Ursache: falscher Pfad oder fehlender Serverstart.

**Fix:**
Pfad prüfen:
ws://localhost:8080/signal

yaml
Code kopieren

---

## 5. Status

BACKEND-01 ist abgeschlossen.

Bereit für:

- BACKEND-02 (Signaling Logic: Invite/Accept/Cancel)
- BACKEND-03 (Peer-Mapping + Sessions)
- BACKEND-04 (Management API für Premium/OS)

---

## 6. Nächster Schritt im Projekt

Gemäß Gesamtarchitektur folgt:

**ANDROID-02 – GhostNet Audio Pipeline MVP**  
oder  
**BACKEND-02 – Signaling Logic (Invite/Accept)**

