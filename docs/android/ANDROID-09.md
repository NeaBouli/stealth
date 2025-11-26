# ANDROID-09 – GhostNet Outgoing Queue (MVP) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Module:**  
- `OutgoingFrameQueue` (neu)  
- `GhostNetTransport` (erweitert)  
- `CallActivity` (erweitert)

---

## 1. Ziel
Einführung der *ersten* Transport-internen Queue-Struktur, die später:

- QUIC/WebRTC-Frames puffert  
- Wiederholungen steuert  
- Prioritäten regelt  
- sequenzielles Frame-Processing ermöglicht  

Aktuell: **MVP-Struktur ohne Transportlogik.**

---

## 2. Implementierte Komponenten

### 2.1 OutgoingFrameQueue (neu)
Funktionen:
- `add(frame)`
- `poll()`
- `isEmpty()`
- `size()`

Nur Logging, keine Verarbeitung.

### 2.2 GhostNetTransport (erweitert)
- jedes `sendAudioFrame()` + `sendControlFrame()` legt Frames in Queue ab  
- neue `processQueue()`-Methode:
  - poll()
  - Logging der Frame-Daten  
  - keine Übertragung (MVP)  

### 2.3 CallActivity (erweitert)
- ruft in `onStart()` → `transport.processQueue()`  
- ruft in `onStop()` → `transport.processQueue()` (Flush)  

Damit ist eine vollständige MVP-Integration gewährleistet.

---

## 3. Bekannte Einschränkungen
Der Patch enthält bewusst **keine Netzwerk- oder Routinglogik**:

Nicht enthalten:
- kein QUIC
- kein WebRTC
- keine Frame-Sequenzen
- keine Prioritäten
- keine Heartbeats
- keine Retry-Mechanismen
- keine Buffer-Strategien
- kein Scheduling
- kein Backend-Traffic

Dies folgt in ANDROID-10 / ANDROID-11.

---

## 4. Nächste Schritte

### ANDROID-10 – Transport Scheduler (MVP)
- Scheduler-Thread für Queue-Processing
- periodische Dispatch-Zyklen
- Logging der Scheduling-Entscheidungen

### ANDROID-11 – QUIC/WebRTC Layer
- Integration einer echten Datagramm-Schicht
- Aufbau eines zuverlässigen DataChannel/QUIC Clients

### BACKEND-02 – Signaling
- A→B Peer-Routing
- CallInvite/Accept-Protokoll
- Backend-gestützte Session-Zuordnung

### BACKEND-03 – TURN/Relay
- Weiterleitung über Relays
- NAT Traversal
- Liveness detection

---

## 5. Dateien in diesem Patch
- `OutgoingFrameQueue.java`
- `GhostNetTransport.java` (erweitert)
- `CallActivity.java` (erweitert)
- Dokument: `docs/android/ANDROID-09.md`

