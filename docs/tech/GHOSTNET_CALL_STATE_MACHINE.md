# GhostNet Call-State-Machine (PATCH 203)

## States

| State     | Bedeutung |
|-----------|-----------|
| IDLE      | Kein aktiver Call |
| RINGING   | Eingehender Anruf |
| OUTGOING  | Ausgehender Anruf |
| ACTIVE    | Gespräch läuft |
| ENDED     | Gespräch beendet |

## Events

### Incoming:
- CALL_INIT → incomingCall()
- Nutzer wählt "Accept" → active
- CALL_BYE → endCall()

### Outgoing:
- Nutzer drückt "Call" → outgoingCall()
- Remote sendet "call-answer" (später) → active
- CALL_BYE → endCall()

## TODO (für spätere Patches)
- Integration in Signaling-Service
- Ringtone / Vibrations
- UI (Full Screen Incoming Call)
- Bindung an GhostNetSessionState
