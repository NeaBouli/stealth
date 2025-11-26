# ANDROID-11 – GhostNet Channel MVP

**Status:** abgeschlossen  
**Art:** Skeleton / Struktur  
**Ziel:** Vorbereitung des Transport-Layers (QUIC/WebRTC)  
**Abhängigkeiten:** ANDROID-08, ANDROID-09, ANDROID-10  

---

## 1. Überblick

ANDROID-11 führt die ersten Bausteine des späteren Netzwerktransports ein:

- `GhostNetChannel` (MVP)
- Integration in `GhostNetTransport`
- Logging-basierte Schnittstellen
- Keine Netzwerklogik, keine Crypto, keine ICE- oder Peer-Verbindungen

Dies ist ein *reines Grundgerüst* für alle folgenden Transport-Schritte.

---

## 2. Implementierte Dateien

### 2.1 `GhostNetChannel`
Pfad:  
`client_android/app/src/main/java/com/securecall/app/ghostnet/channel/GhostNetChannel.java`

Enthält:

- State-Enum: DISCONNECTED / CONNECTING / CONNECTED  
- Methoden:
  - `connect(peerId)`
  - `disconnect()`
  - `sendBytes()`
- Logging-Placeholders
- Noch keine echte Verbindung (MVP)

---

### 2.2 Integration in `GhostNetTransport`

Transport erhält:

- Channel-Instanz
- Logging zur Sichtbarkeit
- `connect()` via Channel
- Getter für Session-ID, Remote-Peer, Channel-Status

Keine Netzlogik, reine API-Struktur.

---

### 2.3 Erweiterung von `CallActivity`

Beim Öffnen des Call-Screens:

- Transport wird gestartet
- Logging zeigt:
  - Session ID
  - Remote Peer
  - Channel-Status
- Transport wird beim Verlassen gestoppt

---

## 3. Nächste Schritte (ANDROID-12+)

Geplante Aufgaben:

- QUIC- oder WebRTC-Core integrieren
- `GhostNetChannel` → echte PeerConnection
- ICE-Kandidaten
- Signaling-Integration mit Backend (BACKEND-02)
- ByteFrame → AudioFrame Pipeline
- zuverlässiger Reconnect / Heartbeats
- Security Layer (Crypto-02+)

---

## 4. Fazit

ANDROID-11 definiert eine klar strukturierte Grundlage für den späteren Transport-Stack.  
Es existiert jetzt:

- Transport-Thread  
- Queue  
- Channel-Skeleton  
- Lifecycle in der App  
- Logging zur Diagnose  

Damit ist das Fundament fertig, um ab **ANDROID-12** echte Netzwerkpakete zu übertragen.
