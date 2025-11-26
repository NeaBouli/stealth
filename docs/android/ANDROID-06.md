# ANDROID-06 – GhostNet Transport (MVP Skeleton) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Module:**  
- `GhostNetTransport` (neu)  
- `CallActivity` (erweitert)

---

## 1. Ziel
Grundgerüst des GhostNet-Transport-Layers anlegen.  
Diese Stufe dient ausschließlich dem strukturellen Aufbau, ohne Funktionalität.

GhostNet wird später der verschlüsselte Audio-Transportkanal via:
- QUIC / oder
- WebRTC DataChannel

Diese Version: **Skeleton (Platzhalter)**.

---

## 2. Implementierte Funktionen (MVP)
### GhostNetTransport (Skeleton)
- Konstruktor erzeugt Log-Eintrag
- `start()` → Log
- `stop()` → Log
- `isConnected()` → immer `false` (Platzhalter)

### CallActivity
- neuer Member `transport`
- Initialisierung in `onCreate()`
- Start in `onStart()`
- Stop in `onStop()`
- Logging aller Transport-Aktionen

---

## 3. Bekannte Einschränkungen
Dieser Patch enthält bewusst **keine reale Netzwerklogik**:

Nicht enthalten:
- keine QUIC-Session
- keine WebRTC-Initialisierung
- keine ICE-Kandidaten
- keine Peer-Verbindungen
- kein Routing
- kein Heartbeat
- keine Fehlertoleranz
- keine Policy-Kopplung

Dies folgt in ANDROID-07, ANDROID-08 und BACKEND-02/03.

---

## 4. Abhängigkeiten / Nächste Schritte

### ANDROID-07 (Transport: Session & Peer-Mapping)
- GhostNetTransport: Session-ID
- Peer-Handle Übergabe
- Vorbereitung DataChannel/QUIC-Sockets
- Logging erweitern

### ANDROID-08 (Transport: Real Audio Path)
- erste echte Datenbindung: AudioFrame → Transport
- Paketstruktur definieren
- Fehler- / Retry-Handling

### BACKEND-02
- Signaling: Mapping A → B
- CallInvite/Accept Nachrichten
- Peer-Discovery (ohne Telefonnummer)

### BACKEND-03
- Relay/Turn-Optimierung
- Heartbeat/Ping für Verbindungsqualität

---

## 5. Dateien in diesem Patch
- `GhostNetTransport.java`
- `CallActivity.java` (erweitert)
- Dokumentation: `docs/android/ANDROID-06.md`

