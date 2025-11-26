# GhostNet Call Signaling MVP (PATCH 202)

Ziel:
- Minimaler CALL_INIT / CALL_BYE-Mechanismus über den bestehenden WebSocket-Kanal.
- Noch KEINE Kopplung an echte Audio/Media-Flows.

## 1. Messages

### CALL_INIT
- type: "call-init"
- callId: UUID
- role: "caller" (später: "callee")

Beispiel:
{
  "type": "call-init",
  "callId": "<uuid>",
  "role": "caller"
}

### CALL_BYE
- type: "call-bye"
- callId: "<uuid>"

## 2. JVM-Seite

- `CallInit` und `CallBye` generieren JSON.
- `CallSignalParser` kann Messages wieder einlesen (nur Typ + callId).

## 3. Debug-Fluss (MainActivity)

1. Button "Send CALL_INIT"
   - erzeugt neue Call-ID
   - sendet JSON über `WebSocketService.sendMessage()`
   - speichert `lastDebugCallId`

2. Button "Send CALL_BYE"
   - nutzt `lastDebugCallId`
   - sendet passende BYE-Message

## 4. Nächste Schritte

- Integration mit `GhostNetSession` (Call-Lifecycle)
- Auswertung eingehender CALL_INIT/CALL_BYE im WebSocketService
- Aufbau einer State Machine:
  - IDLE -> RINGING -> ACTIVE -> TERMINATED

## 2.1 Parsing auf Empfang

In `WebSocketService.onMessage()` werden nun:

- Key-Exchange Nachrichten priorisiert geprüft  
- Falls nicht, Call-Signaling Nachrichten (`call-init`, `call-bye`) geparst  
- Entsprechende Controller (`CallController`) angesteuert  

