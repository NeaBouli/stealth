# ANDROID-07 – GhostNet Session & Peer Mapping (MVP) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Module:**  
- `GhostNetSession` (neu)  
- `GhostNetTransport` (erweitert)  
- `CallActivity` (erweitert)

---

## 1. Ziel
Einführung einer grundlegenden Session- und Peer-Struktur für den GhostNet Transport-Layer.

Diese Stufe definiert:
- lokale Session-ID
- Remote-Peer-ID (noch `null`)
- Aktivierungsstatus
- Logging in Transport und CallActivity

Dies ist die notwendige Basis für zukünftige Transport-Schichten (WebRTC/QUIC).

---

## 2. Implementierte Komponenten

### 2.1 GhostNetSession (neu)
Enthält:
- `sessionId` (UUID)
- `remotePeerId` (zunächst `null`)
- `active` (Boolean)

Methoden:
- Activate/Deactivate
- Getter/Setter

### 2.2 GhostNetTransport (erweitert)
- enthält eine lokale `GhostNetSession`
- Logging der Session-ID
- Session aktivieren/deaktivieren bei `start()/stop()`
- neuer Setter für Remote-Peer
- neuer Getter für Session-ID und Peer-ID

### 2.3 CallActivity (erweitert)
Beim Start:
- Transport wird initialisiert
- Session-ID ausgegeben
- Remote-Peer (null) ausgegeben
- Logs entsprechen MVP-Standard

---

## 3. Bekannte Einschränkungen
Diese Stufe enthält absichtlich **keine echte GhostNet-Funktionalität**:

Nicht enthalten:
- keine WebRTC-Verbindung
- keine QUIC-Verbindung
- kein Signaling/Mapping auf Backend
- keine E2E Routing-Logik
- keine Paketstruktur
- kein Heartbeat
- keine Fehlerbehandlung
- keine Transport-Policies

Dies ist ein **Datengerüst**, kein Kommunikationssystem.

---

## 4. Nächste Schritte

### ANDROID-08: Transport-Protokoll MVP
- Frame-Container definieren (AudioFrame, ControlFrame)
- Basic-Message-Konzept für Transport
- interne Queue für ausgehende Frames
- Logging aller Frames (noch kein Netzwerk)

### BACKEND-02
- Session-Mapping serverseitig
- CallInvite/Accept Messages
- Peer-Discovery ohne Telefonnummern

### ANDROID-09
- Erste echte Peer-Konnektion (WebRTC oder QUIC)
- Remote-Peer-ID setzen via Signaling

---

## 5. Dateien in diesem Patch
- `GhostNetSession.java`
- `GhostNetTransport.java` (erweitert)
- `CallActivity.java` (erweitert)
- Dokumentation: `docs/android/ANDROID-07.md`

