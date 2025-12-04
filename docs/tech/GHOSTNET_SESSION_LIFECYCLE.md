# PATCH 232 — GhostNet Session & Call Lifecycle

Dieses Dokument beschreibt den geplanten Lebenszyklus
einer verschlüsselten GhostNet-Session in Verbindung
mit der GhostCall-Statemachine.

## 1. Beteiligte Komponenten

- `GhostCallController`
  - verwaltet den abstrakten Call-Status
  - Zustände (Beispiele):
    - IDLE
    - OUTGOING_SETUP
    - ACTIVE
    - TERMINATING
    - ENDED

- `GhostTransport`
  - kümmert sich um den Transport-Thread
  - sendet/empfängt verschlüsselte Frames
  - Hooks:
    - `start()` → Call in Richtung "establishing"
    - `quietStop()` → ruhiger Stopp bei Call-Ende

- `GhostTransportThread`
  - entnimmt Frames aus der Queue
  - markiert Call als aktiv beim ersten Frame

- `GhostMediaRouter`
  - kümmert sich um Decode/Playback
  - wird über `quietShutdown()` sauber gestoppt

- `GhostNetSession` (geplant/teilweise vorhanden)
  - repräsentiert eine logische Session zwischen zwei Peers
  - verfolgt:
    - Session-ID
    - Peer-Identity
    - Lifetime (created → active → dead)

## 2. Geplanter Ablauf (High-Level)

1. **Call Start Trigger**
   - UI / Debug / später Signaling löst einen Outgoing-Call aus.
   - `GhostTransport.start()` wird aufgerufen.
   - Intern:
     - `onTransportStart()` → `notifyCallEstablishing()`
     - `GhostCallController.startOutgoingCall()` setzt Call-State auf "OUTGOING_SETUP".

2. **Erster Media-Frame**
   - `GhostTransportThread` dequeuet den ersten Frame.
   - Beim ersten Frame:
     - `GhostCallController.markCallActive()`
     - Call-State wechselt nach "ACTIVE".
   - Später:
     - `GhostNetSession` kann diesen Übergang nutzen, um Session-State auf "ACTIVE" zu setzen.

3. **Laufender Call**
   - Frames werden über Transport + MediaRouter verarbeitet.
   - Session befindet sich im aktiven Zustand.

4. **Fehler / Soft-Terminate**
   - Bei Fehlern im Transport oder im MediaDecode:
     - `GhostCallController.terminateCall()` wird getriggert.
   - Intern:
     - `performQuietShutdown()`:
       - `GhostTransport.quietStop()`
       - `GhostMediaRouter.get().quietShutdown()`
       - Call-State → "ENDED"

5. **Session End-of-Life (geplant)**
   - Nach Call-Ende:
     - `GhostNetSession` wird auf "DEAD" gesetzt.
     - Ressourcen werden freigegeben:
       - Schlüssel-Material (sobald Crypto integriert ist)
       - Session-IDs
       - Statistiken (nur lokal / optional)

## 3. Geplante Erweiterungen

- Explizite `GhostNetSessionState`-Statemachine:
  - IDLE
  - NEGOTIATING
  - ACTIVE
  - TERMINATING
  - DEAD

- Hooks in `GhostCallController`:
  - `onCallEstablishing()` → Session auf "NEGOTIATING"
  - `onCallActive()` → Session auf "ACTIVE"
  - `onCallTerminated()` → Session auf "DEAD"

- Debug-/Status-Anzeige in der UI:
  - Session-State neben Call-State sichtbar.
  - Bereits teilweise umgesetzt:
    - `updateSessionStatus(...)` in `MainActivity`.

## 4. Ziel von PATCH 232

- Klarheit für Architekt und Entwickler:
  - Wie Call-State und Session-State zusammenhängen.
  - Wo Hooks bereits existieren (Transport/Media/Call).
  - Wo `GhostNetSession` später andocken soll.

- Basis für folgende Patches:
  - Session-Objekt konkretisieren.
  - Session-State-Enums finalisieren.
  - Call- und Session-Hooks verbinden.
