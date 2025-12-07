#!/bin/bash
set -e

echo "== patch_012: add GhostNet FrameV1 wire-format spec =="

mkdir -p docs/tech

cat <<'DOC' > docs/tech/GHOSTNET_FRAME_V1.md
# GhostNet FrameV1 – Wire Format Specification (Draft v1)

## 1. Goals

FrameV1 is the **first on-wire format** for GhostNet.  
It is designed to be:

- simple to parse on Android, Node.js and Rust,
- future-proof for later versions (FrameV2+),
- suitable for low-latency voice (small, frequent frames),
- explicit about what is encrypted (payload) and what is not (header).

This document is the **single source of truth** for the FrameV1 layout.

---

## 2. High-level structure

A GhostNet FrameV1 on the wire is:

- a **fixed-size header** (12 bytes),
- followed by a **variable-length encrypted payload**.

Conceptually:

- Header: routing + meta (not encrypted)
- Payload: AEAD-encrypted blob (Opus audio, control, keepalive, …)

+----------+----------+----------+----------+
| Header (12 bytes, unencrypted) |
+----------+----------+----------+----------+
| Ciphertext Payload (0..N bytes) |
+------------------------------------------+

perl
Code kopieren

---

## 3. Header layout (12 bytes, unencrypted)

All integers are little-endian, unsigned.

| Offset | Size | Name        | Description                                      |
|--------|------|------------|--------------------------------------------------|
| 0      | 1    | VERSION    | Frame format version. For now: `0x01`.           |
| 1      | 1    | TYPE       | Frame type (AUDIO, CONTROL, KEEPALIVE, ERROR).   |
| 2      | 1    | FLAGS      | Bitfield: per-type modifiers (e.g. marker bits). |
| 3      | 1    | KEY_ID     | Session key slot / index.                        |
| 4      | 4    | SESSION_ID | Logical session (call / peer) identifier.        |
| 8      | 4    | LENGTH     | Length (in bytes) of the ciphertext payload.     |

### 3.1 VERSION

- `0x01` for FrameV1.
- Any other value must be rejected or handled by a future decoder.
- VERSION is **not** negotiated per frame; it encodes the header format only.

### 3.2 TYPE (Frame Type)

Defines the semantic type of the frame:

- `0x01` – `AUDIO_OPUS_FRAME`  
  Encapsulates one Opus frame (or a small bundle) for voice.
- `0x02` – `CONTROL`  
  Signalling-related messages inside GhostNet (hello, bye, error).
- `0x03` – `KEEPALIVE`  
  Lightweight ping/pong-style frames to keep the connection alive.
- `0x7F` – `ERROR`  
  Transport-level or protocol-level error notification.

Values `0x10`–`0x6F` are reserved for future extensions (video, multi-stream, etc.).

### 3.3 FLAGS

Bitfield, meaning depends on TYPE. All bits default to `0`.

For `AUDIO_OPUS_FRAME`:

- Bit `0` (`0x01`): **MARKER** – e.g. start of talk-spurt.
- Bit `1` (`0x02`): **END_OF_STREAM** – sender is closing this audio stream.

For `KEEPALIVE`:

- Bit `0` (`0x01`): request response (PING).
- If `0`: pure one-way heartbeat (no response required).

All other bits in FrameV1 MUST be sent as `0` and MUST be ignored on receive.

### 3.4 KEY_ID

Small integer (0–255) selecting which **session key** is used:

- `0` – default symmetric key for the session (most common case).
- `1`..`N` – future key slots (rekey, substreams, experimental).

The crypto layer must maintain a mapping `(SESSION_ID, KEY_ID) -> KeyMaterial`.

### 3.5 SESSION_ID (4 bytes)

Unsigned 32-bit number identifying the logical session (call, peer, or channel).

- Assigned during session setup (e.g. in signalling / handshake).
- Unique per connection direction or per call (implementation choice).
- In FrameV1, SESSION_ID is **not encrypted** – it is needed for routing.

If there is no established session yet (e.g. pre-hello), implementations may use `0` as a placeholder, but this should be minimised.

### 3.6 LENGTH (4 bytes)

Unsigned 32-bit length of the ciphertext payload in bytes.

- Minimum: `0` (allowed for pure control/keepalive with no payload).
- Maximum practical value: TBD by implementation, but for audio we expect small payloads (a few hundred bytes).

If the received frame has:

- `LENGTH` larger than the available bytes → **invalid frame**, must be dropped.
- `LENGTH` `0` and TYPE = `AUDIO_OPUS_FRAME` → invalid; should be treated as protocol error.

---

## 4. Payload (ciphertext blob)

The payload is an opaque ciphertext produced by the AEAD layer.

General model:

- Inputs:
  - plaintext (e.g. Opus frame),
  - key selected via `(SESSION_ID, KEY_ID)`,
  - nonce (constructed by the crypto layer),
  - associated data (header bytes 0–11).
- Output:
  - ciphertext including authentication tag.

FrameV1 does **not** specify:

- how nonces are constructed,
- which exact AEAD is used.

This is handled by the **GhostNet Crypto Design** document.  
For the purpose of this spec:

> The payload is a `LENGTH`-byte AEAD ciphertext, authenticated over the 12-byte header.

---

## 5. Example: AUDIO_OPUS_FRAME

Example values (hex):

- VERSION: `01`
- TYPE: `01` (`AUDIO_OPUS_FRAME`)
- FLAGS: `01` (MARKER)
- KEY_ID: `00`
- SESSION_ID: `0x11223344`
- LENGTH: `0x00000014` (20 bytes ciphertext)

Header bytes (little-endian):

Offset Field Value (hex)
0 VERSION 01
1 TYPE 01
2 FLAGS 01
3 KEY_ID 00
4–7 SESSION_ID 44 33 22 11
8–11 LENGTH 14 00 00 00

pgsql
Code kopieren

On the wire (header + ciphertext, fictive payload):

01 01 01 00 44 33 22 11 14 00 00 00 | aa bb cc dd ee ff ... (20 bytes)

markdown
Code kopieren

The receiver:

1. Reads first 12 bytes → parses header.
2. Validates:
   - VERSION == 1,
   - TYPE is known,
   - LENGTH matches remaining bytes.
3. Passes:
   - header bytes as associated data,
   - SESSION_ID + KEY_ID to key lookup,
   - payload to AEAD decrypt.
4. If AEAD succeeds:
   - decodes the resulting plaintext as an Opus frame (for AUDIO type).
5. If AEAD fails:
   - drops the frame, may increase an error counter.

---

## 6. Example: KEEPALIVE (PING)

- VERSION: `01`
- TYPE: `03` (`KEEPALIVE`)
- FLAGS: `01` (PING, expects PONG)
- KEY_ID: `00`
- SESSION_ID: `0x00000000` (or a valid session, depending on design)
- LENGTH: `0x00000000`

On the wire:

01 03 01 00 00 00 00 00 00 00 00 00

yaml
Code kopieren

No payload bytes follow.  
The receiver responds (if configured) with a `KEEPALIVE` frame where:

- TYPE = `03`,
- FLAGS = `00` (PONG, no reply expected),
- same SESSION_ID.

---

## 7. Versioning and future compatibility

FrameV1 is intentionally minimal. Future versions may:

- extend the header,
- change field semantics,
- add new TYPEs and FLAG semantics.

Rules for V1 receivers:

- If `VERSION != 0x01` → either drop the frame or hand it to a dedicated upgrader/compat-layer.
- Unknown TYPE:
  - Drop the frame.
  - Optionally log a warning.
- Unknown FLAG bits:
  - Ignore them (for known TYPEs), treat as future extension.

---

## 8. Implementation notes

- The header should be parsed into a small struct/object (e.g. `FrameHeaderV1`) with:
  - `version`, `type`, `flags`, `keyId`, `sessionId`, `length`.
- The exact mapping from TYPE to payload interpretation (Opus, JSON, CBOR, etc.) is **out of scope** of this document and will be defined in separate specs:
  - e.g. `GHOSTNET_AUDIO_V1.md`, `GHOSTNET_CONTROL_V1.md`.

This document only defines **how the bytes are arranged on the wire**, not what the decrypted payload means.

DOC

echo "[OK] Created docs/tech/GHOSTNET_FRAME_V1.md"
echo "== patch_012 done =="
