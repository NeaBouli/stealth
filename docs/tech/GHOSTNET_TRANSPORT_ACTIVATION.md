# PATCH 206 — GhostTransport Activation Hooks

## Ziel
Transport soll nur während eines aktiven Calls laufen.

## Lifecycle

Call ACTIVE  →
  GhostNetSession.onCallActive() →
    GhostTransport.start()

Call ENDED →
  GhostNetSession.onCallEnded() →
    GhostTransport.stop()

## Status
- Nur Skelett
- Keine echten Audio/Media-Sender
- Keine Netzwerklogik
- Keine SRTP/XChaCha-Integration

Einsatz im Debug:
- "Accept Call" → START
- "End Call" → STOP
