#!/bin/bash
set -e

echo "== patch_014: add GhostNet inbound flow doc stub =="

mkdir -p docs/tech

cat <<'DOC' > docs/tech/GHOSTNET_INBOUND_FLOW.md
# GhostNet – Inbound Flow (Stub)

Status: v0.1 (skeleton)  
Scope: Android client inbound path for GhostNet frames.

## 1. High-level inbound pipeline

Planned flow for incoming GhostNet data:

1. Network layer receives raw bytes from the GhostNet backend.
2. Inbound transport thread reads raw frames from the network:
   - `TransportThreadInbound` (stub exists in:
     `client_android/.../ghostnet/transport/thread/TransportThreadInbound.kt`)
3. Raw frames are passed into the frame parser:
   - header parsing (version, type, flags, length),
   - body parsing (audio, control, keepalive).
4. Parsed frames are dispatched to the media router:
   - audio frames -> playback pipeline,
   - control frames -> call/session state machine,
   - keepalive frames -> connection health.

## 2. Components involved (planned)

- **Inbound thread**
  - `TransportThreadInbound`:
    - runs in its own thread,
    - will later be wired to the network receiver,
    - currently stubbed (logging + sleep loop).

- **Frame parsing**
  - Package: `ghostnet.frame.body.*` (Audio/Control/KeepAlive parsers),
  - uses header helpers in `media/crypto/FrameHeaderUtils.kt`,
  - validates version + frame type before dispatch.

- **Media router**
  - `GhostMediaRouter`:
    - central point for audio/control dispatch,
    - will receive parsed inbound frames.

## 3. Next implementation steps

Inbound path needs:

1. A receive hook from the network layer into `TransportThreadInbound`.
2. A small API on the parser side, e.g.:
   - `FrameBodyParser.parse(raw: ByteArray): ParsedFrame`.
3. Dispatch logic in the inbound thread:
   - parse frame,
   - hand off to `GhostMediaRouter`.

This document is a stub and should be updated when:

- the network receive API is final,
- the frame parsers are fully implemented,
- media playback is integrated end-to-end.
DOC

echo "[OK] Created docs/tech/GHOSTNET_INBOUND_FLOW.md"
echo "== patch_014 done =="
