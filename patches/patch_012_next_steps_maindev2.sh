#!/bin/bash
set -e

echo "== patch_012: add NEXT_STEPS_MAINDEV2.md =="

mkdir -p docs/dev

cat <<'DOC' > docs/dev/NEXT_STEPS_MAINDEV2.md
# Next Steps for Main Dev 2 (Post patch_011)

This document provides a **clear, minimal, actionable roadmap**  
for the next development phase after patches 001–010.

It tells Main Dev 2 where the previous developer stopped,  
and what the logical and safest continuation is.

---

## 1. Android Client – GhostNet Inbound (PRIORITY 1)

The predecessor completed:

- FrameV1 **Outbound** (CRYPTO-38/39/40)
- Outbound Queue
- TransportThreadOutbound
- MediaEncryptor
- Debug CALL-INVITE sender

What is missing now:

### 1.1 FrameV1 Inbound Path
Implement:

- `TransportThreadInbound.kt`
- `GhostNetworkReceiver` (mirror to GhostNetworkSender)
- FrameHeaderV1 parser integration (already scaffolded)
- BodyParser:
  - AudioBodyParser
  - ControlBodyParser
  - KeepAliveBodyParser

### 1.2 Media Decryption Path
Wire:

- `MediaDecryptor`
- `SessionCipherBinding.decryptFrameV1`
- Hand off to MediaPlayback or ControlRouter

### 1.3 GhostMediaRouter inbound routing
Implement logic:

- If flags = AUDIO → decode to PCM → feed playback thread
- If flags = CONTROL → call router callbacks
- If flags = KEEPALIVE → ignore / update timers

---

## 2. Audio Pipeline – Opus Integration (PRIORITY 2)

Scaffolding exists in:

- `PATCH_213_OPUS_DECODER_SKELETON.md`
- `PATCH_214_CODEC_INTEGRATION.md`
- `PATCH_215_JNI_HOOKS.md`
- `PATCH_219_OPUS_BINDINGS.md`
- `native/README.md`

Next steps:

- Implement real Opus decode (Rust or C/NDK)
- Implement simple AudioTrack-based playback thread
- Connect decoded PCM to playback function

---

## 3. core_crypto – Real Crypto (PRIORITY 3)

Current state:

- `core_crypto` is scaffold-only.
- No real encryption or decryption yet.

Next steps:

1. Introduce XChaCha20-Poly1305 or AES-GCM via Rust crate.
2. Implement:
   - Key schedule
   - Nonce management
   - Session key rotation
3. Bind via JNI/NDK to Kotlin layer.

---

## 4. Backend – Signaling Hardening (PRIORITY 4)

Backend exists but is “research-ready”:

- Sessions
- Heartbeat
- Broadcast
- Presence
- Routing
- Rate Limiters
- Metrics

Next steps:

1. Add WS reconnect logic.
2. Add proper session-expiry cleanup jobs.
3. Start adding backend tests (tools/test_*.sh or Node-based unit tests).
4. Document end-to-end flows in `docs/tech/`.

---

## 5. Documentation – Expand Tech Specs (PRIORITY 5)

Add:

- `GHOSTNET_WIRE_PROTOCOL.md`
- `GHOSTNET_SECURITY_MODEL.md`
- `AUDIO_PIPELINE_OVERVIEW.md`
- `CRYPTO_DESIGN_V1.md`

These will anchor the design for future developers.

---

## Summary

Main Dev 2 should:

1. Finish Android-GhostNet inbound path.
2. Implement Opus decode + AudioPlayback.
3. Integrate real crypto in `core_crypto`.
4. Harden the signaling backend.
5. Add missing protocol/security design docs.

This roadmap ensures consistent continuation  
of the predecessor’s architecture and coding style.

DOC

echo "[OK] Created docs/dev/NEXT_STEPS_MAINDEV2.md"
echo "== patch_012 done =="
