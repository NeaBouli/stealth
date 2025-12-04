# GhostNet Wire Protocol – Version 1 (Stub)

Status: draft / work-in-progress  
Scope: describes the high-level structure of GhostNet frames on the wire.

This document is the starting point for a full formal wire specification.  
For exact implementation details, always cross-check with:

- `client_android/app/src/main/java/com/securecall/app/ghostnet/media/crypto/FrameHeaderUtils.kt`
- `client_android/app/src/main/java/com/securecall/app/ghostnet/frame/body/`
- `client_android/app/src/main/java/com/securecall/app/ghostnet/transport/net/GhostNetworkSender.kt`
- `backend/ghostnet/` (relay/server stubs)

---

## 1. Goals

GhostNet is the media transport layer used by SecureCall / Stealth.

Design goals:

- low-latency, unordered/ordered-friendly transport over UDP/QUIC or TCP/WebSocket
- explicit frame header (versioned)
- clear separation of header vs. encrypted payload
- suitable for end-to-end encrypted audio/control traffic
- protocol must survive evolution (V2+) without breaking V1 clients abruptly

---

## 2. Frame types (conceptual)

GhostNet V1 defines three main frame classes:

- **AUDIO**  
  Encapsulates one encoded audio frame (e.g. Opus @ 20 ms).

- **CONTROL**  
  Small control messages (call invite, accept/reject, ping/pong, error codes, etc.).

- **KEEPALIVE**  
  Lightweight keepalive frames to keep connections and NAT bindings alive.

Concrete type identifiers are defined in `FrameHeaderUtils.kt`.  
On the wire, the receiver reads the header, checks version & type, then dispatches to the matching parser in `ghostnet/frame/body/`.

---

## 3. Frame header (high-level)

GhostNet V1 uses a **fixed-size header** (V1) + encrypted body:

- Header V1:
  - contains protocol version,
  - frame type,
  - flags,
  - and fields needed for nonce/sequence handling.

- Body:
  - encrypted with the active session key (placeholder in current MVP),
  - interpreted based on frame type (AUDIO / CONTROL / KEEPALIVE).

Exact layout and bit assignments are defined in `FrameHeaderUtils.kt` and may be refined in future patches.  
This document will be updated once the layout is fully frozen.

---

## 4. Encryption (design intent)

Current MVP code still uses **stub crypto** (no real AEAD yet).  
The target design:

- Handshake:
  - X25519 (Curve25519) key exchange
  - HKDF-SHA256 for key derivation

- Payload protection:
  - XChaCha20-Poly1305 (preferred)
  - or AES-GCM as fallback on hardware-accelerated platforms

- Nonce handling:
  - monotonically increasing counter per direction,
  - portions of the nonce tied to header fields,
  - replay protection on the receiver side.

Until core_crypto + FFI are fully wired, GhostNet packets remain “encrypted” with placeholder logic.  
The wire spec must be kept aligned with the Rust implementation in `core_crypto/` once real crypto lands.

---

## 5. Audio frames (AUDIO)

Conceptual structure for an AUDIO frame (after decryption):

- framing for exactly one audio packet (e.g. 20 ms Opus),
- fields (conceptual, subject to change):
  - codec identifier (e.g. OPUS),
  - codec-specific flags (bitrate, mono/stereo, etc.),
  - raw encoded audio payload.

The exact in-memory and on-the-wire layout is currently described in the `PATCH_21x` media memos and will be consolidated here once the Opus integration stabilises.

---

## 6. Control frames (CONTROL)

CONTROL frames are small messages used for:

- call setup / teardown (INVITE, ACCEPT, REJECT),
- error reporting,
- out-of-band signalling that should not rely on the separate signaling server.

The current MVP uses simple integer codes + optional text payload (see debug button in `MainActivity`).  
Later the CONTROL namespace will move towards a compact, versioned control message format.

---

## 7. Keepalive frames (KEEPALIVE)

KEEPALIVE frames exist to:

- keep NAT bindings alive,
- keep connections warm during silence,
- allow latency and connectivity probing.

They should carry minimal or no payload beyond what is required for crypto/nonces.

---

## 8. Versioning & evolution

The header contains a **protocol version field**.  
Migration strategy:

- V1 is the initial production-ready version.
- Future V2+ may introduce:
  - extended headers,
  - new frame types,
  - optional fields.

Servers and clients SHOULD:

- negotiate capabilities during session setup,
- prefer the highest mutually supported version,
- fall back to V1 when needed.

Breaking changes must be captured here and in dedicated decision memos under `docs/tech/DECISION_*.md`.

---

## 9. Status and next steps

This document is a **stub** and needs to be expanded when:

- frame header layout is fully frozen,
- crypto wiring in `core_crypto/` is complete,
- Opus/codec payload formats are finalised.

Until then, this file:
- gives new developers a mental model,
- provides a single place to track protocol-level decisions,
- and will evolve alongside CRYPTO- and MEDIA-related patches.

