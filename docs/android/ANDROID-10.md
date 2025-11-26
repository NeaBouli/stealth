# ANDROID-10 – GhostNet Scheduler (MVP) – Abschlussbericht

**Status:** ✔ Abgeschlossen  
**Module:**  
- `GhostNetScheduler` (neu)  
- `GhostNetTransport` (erweitert)  
- `CallActivity` (erweitert)

---

## 1. Ziel
Einführung eines regelmäßigen Prozess-Systems ("Scheduler") für den GhostNet-Transport.

Der Scheduler ruft periodisch:
transport.processQueue()

yaml
Code kopieren
auf und verarbeitet somit den Outgoing-Frame-Puffer.

Diese Funktion ist notwendig für:
- zukünftige Netzwerkanbindung (QUIC/WebRTC)
- Taktung von Transport-Frames
- Heartbeats
- Flow-Control

---

## 2. Implementierte Komponenten

### 2.1 GhostNetScheduler (neu)
- eigener Thread
- läuft im 500ms-Intervall (MVP)
- ruft `processQueue()` auf
- Logging für Start/Stop
- `shutdown()` für kontrolliertes Beenden

### 2.2 GhostNetTransport (erweitert)
- Scheduler wird beim Transport-Start automatisch erzeugt und gestartet
- Scheduler wird beim Stop korrekt beendet
- keine Netzwerkverarbeitung (MVP)
- Queue bleibt MVP-only (keine Weiterleitung)

### 2.3 CallActivity (erweitert)
- Log-Ausgabe vor dem Scheduler-Start
- Log-Ausgabe vor dem Scheduler-Stop
- keine Logikänderung notwendig (Transport steuert Scheduler selbst)

---

## 3. Bekannte Einschränkungen
Dieser Patch enthält **noch keinen echten Transport**.

Nicht enthalten:
- kein QUIC
- kein WebRTC DataChannel
- keine Frame-Verarbeitung
- keine Verbindung zu Backend
- keine Heartbeats
- keine Prioritätssteuerung
- keine Retry-Mechanismen
- kein aktiver Timer (nur thread.sleep)

Alles folgt in späteren Android-Stufen.

---

## 4. Nächste Schritte

### ANDROID-11 – QUIC/WebRTC Transport Binding
- GhostNetScheduler nutzt echten Network-Dispatch
- Integration eines QUIC-Clients ODER WebRTC-DataChannels
- Aufbau Peer-to-Peer DataChannel
- Verschlüsselter Frame-Transport

### BACKEND-02
- Signaling: INVITE / ACCEPT / PEER messages
- Session-Zuordnung zwischen Clients

### ANDROID-12 – Heartbeat System
- ControlFrames: HEARTBEAT + SESSION_INFO
- Netzwerk-Liveness-Prüfung

### ANDROID-13 – Policy Integration
- Transport abhängig von Sicherheitsmodus (Basic / Pro / Premium)

---

## 5. Dateien in diesem Patch
- `GhostNetScheduler.java` (neu)
- `GhostNetTransport.java` (erweitert)
- `CallActivity.java` (erweitert)
- Dokument: `docs/android/ANDROID-10.md`

