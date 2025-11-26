# ANDROID-08 – GhostNet Frame System (MVP) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Module:**  
- `GhostFrame` (neu)  
- `AudioFrame` (neu)  
- `ControlFrame` (neu)  
- `GhostNetTransport` (erweitert)  
- `AudioPipeline` (erweitert)  
- `CallActivity` (erweitert)

---

## 1. Ziel
Grundstruktur für das interne Frame-System des GhostNet-Transports schaffen.

Diese Stufe definiert:
- Container-Frames (`GhostFrame`)
- Audio-Datenframes (`AudioFrame`)
- Kontrollnachrichten (`ControlFrame`)
- Logging-basierte Verarbeitung in `GhostNetTransport`
- Dummy-Frame-Send via `AudioPipeline`

Kein Routing, keine Verschlüsselung, kein Backend — reines **Struktur-MVP**.

---

## 2. Implementierte Komponenten

### 2.1 GhostFrame
- Obertyp für alle Frames
- Typen: AUDIO / CONTROL
- Payload: Byte-Array

### 2.2 AudioFrame
- Kapselt Audiobytes
- Konvertierbar in `GhostFrame`

### 2.3 ControlFrame
- Typen: HELLO / SESSION_INFO / HEARTBEAT
- String-Nachricht → Byte-Payload
- Konvertierbar in `GhostFrame`

### 2.4 GhostNetTransport (erweitert)
- sendAudioFrame(): Logging
- sendControlFrame(): Logging
- onIncomingFrame(): Logging
- keine Transportlogik

### 2.5 AudioPipeline (erweitert)
- neue attachTransport()
- sendet Dummy-AudioFrame beim Start

### 2.6 CallActivity (erweitert)
- verbindet Pipeline ↔ Transport
- Logging für ausgehende Frames

---

## 3. Bekannte Einschränkungen
Dieser Patch enthält **keine** produktive Logik.

Nicht enthalten:
- kein Netzwerk
- kein QUIC
- kein WebRTC DataChannel
- keine Paketnummern
- keine Latenzsteuerung
- keine Kompression
- keine Verschlüsselung
- kein Signaling
- kein Backend-Mapping
- kein Frame-Queueing
- kein Heartbeat

Dies ist ein reines MVP-Gerüst.

---

## 4. Nächste Schritte

### ANDROID-09 – Transport Messaging MVP
- FrameQueue (Outgoing)
- FrameQueue (Incoming)
- Standardisierung der Frame-Header
- Zentrale Dispatch-Logik

### ANDROID-10 – QUIC/WebRTC Binding
- Integration eines echten Transports
- ICE-Handling
- verbindungsorientierter Datenkanal

### BACKEND-02 – Signaling
- Session-Mapping
- Peer-Routing
- INVITE/ACCEPT/PEER messages

### BACKEND-03 – Relay / TURN
- Netzwerkfokus
- Heartbeats
- Verbindungsliveness

---

## 5. Dateien in diesem Patch
- `GhostFrame.java`
- `AudioFrame.java`
- `ControlFrame.java`
- `GhostNetTransport.java` (erweitert)
- `AudioPipeline.java` (erweitert)
- `CallActivity.java` (erweitert)
- Dokumentation: `docs/android/ANDROID-08.md`

