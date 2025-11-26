# PATCH 208 — Media Pipeline ↔ Session ↔ Call Lifecycle

## Ziel
MediaPipeline und Transport sollen automatisch starten/stoppen:

- Wenn ein Call aktiv wird
- Wenn ein Call endet

## Ablauf

### CALL_ACTIVE
1. `CallController.acceptCall()`
2. → `GhostNetSession.onCallActive()`
3. → `enableTransport()`
4. → `enableMediaPipeline()`
5. → SessionState = ACTIVE

### CALL_ENDED
1. `CallController.endCall()`
2. → `GhostNetSession.onCallEnded()`
3. → `disableMediaPipeline()`
4. → `disableTransport()`
5. → SessionState = DEAD

## Status
- Keine echten Media-Threads
- Keine Audio-Decoder
- Keine SRTP/XChaCha
- Reiner Lifecycle-Skeleton

## Nächste Schritte
- PATCH 209: AudioDecoder Skeleton
- PATCH 210: Inbound MediaFrame → Decrypt → Decode
- PATCH 211: Outbound AudioRecorder → Encrypt → Send
