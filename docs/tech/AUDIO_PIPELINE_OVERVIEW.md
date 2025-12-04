# SecureCall / Stealth – Audio Pipeline Overview (Stub)

Version: v0.1

This is a short high-level map of how audio flows through the system,
so new devs can see where microphone samples enter and how they leave
the speakers again.

---

## 1. Outbound path (device → network)

Rough flow:

- Android microphone (AudioRecord)
- encoder (planned: Opus via native/securecall_opus/)
- crypto + framing:
  - core_crypto/ (Rust, keys + AEAD)
  - MediaEncryptor (Android wrapper)
  - FrameHeaderV1 + body
- transport:
  - GhostNetworkSender
  - TransportThreadOutbound
  - real network layer (WebSocket/QUIC/etc., TBD)

Result: PCM → encoded audio → encrypted GhostNet frame → network.

---

## 2. Inbound path (network → device)

Rough flow:

- network implementation receives encrypted GhostNet frames
- GhostNetworkReceiver enqueues raw ByteArray
- TransportThreadInbound:
  - polls GhostNetworkReceiver
  - parses header/body (later: FrameHeaderUtils + FrameBodyParser)
- GhostMediaRouter:
  - routes AUDIO → playback pipeline
  - routes CONTROL → call state / UI
  - routes KEEPALIVE → session health

Then:

- decrypt payload (core_crypto via FFI)
- decode audio (Opus decoder in native/securecall_opus/)
- optional jitter buffer
- AudioTrack for low-latency playback.

---

## 3. Where to look in the code

Outbound:

- MainActivity debug hooks
- ghostnet/media/crypto/MediaEncryptor.kt
- ghostnet/transport/net/GhostNetworkSender.kt
- ghostnet/transport/thread/TransportThreadOutbound.kt

Inbound:

- ghostnet/transport/net/GhostNetworkReceiver (stub)
- ghostnet/transport/thread/TransportThreadInbound.kt
- ghostnet/frame/FrameParserStub.kt
- ghostnet/frame/body/* (body parsers)
- ghostnet/media/GhostMediaRouter.kt

Crypto core:

- core_crypto/ (Rust)
- native/ (NDK/FFI glue)

---

## 4. Status

This file is a stub (v0.1) and should be updated when:

- Opus is wired via native/securecall_opus/,
- GhostMediaRouter handles real inbound audio,
- the wire spec in GHOSTNET_WIRE_SPEC_v1.md is stable.

