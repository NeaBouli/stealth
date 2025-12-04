# SecureCall / Stealth – Crypto Design V1 (Stub)

Version: v0.1 (design stub)  
Status: DRAFT – subject to change as implementation evolves.

This document describes the *intended* cryptographic structure for
SecureCall / Stealth. It connects:

- the **wire format** (see `GHOSTNET_WIRE_SPEC_v1.md`),
- the **security model** (see `GHOSTNET_SECURITY_MODEL.md`),
- the **Rust crypto core** in `core_crypto/`,
- and the **NDK/FFI glue** in `native/`.

It is a living document and will be updated when concrete algorithms
and parameters are fully fixed.

---

## 1. Design goals (recap)

Core goals (high level):

- End-to-end encryption for voice calls
- Forward secrecy for sessions
- No server-side decryption capability
- Minimal and well-structured key material on devices
- No long-term central identifier that links all sessions of a user

Non-goals (for v1):

- Perfect deniability guarantees
- Multi-device key synchronisation
- Post-quantum resistance

These non-goals may be revisited in future versions.

---

## 2. High-level architecture

The crypto stack is split into:

- **core_crypto/** (Rust):
  - owns the cryptographic primitives and state machines,
  - defines session contexts and key schedules,
  - is independent from Android/Java specifics.

- **native/** (NDK / JNI):
  - exposes a small FFI surface to Android,
  - handles conversion between Kotlin/Java types and Rust.

- **client_android/**:
  - uses FFI bindings to:
    - perform handshakes,
    - encrypt / decrypt frames,
    - derive per-session keys.

GhostNet frames transport *already encrypted* payloads. The Android side
should never implement its own crypto primitives – everything sensitive
lives in Rust.

---

## 3. Building blocks (candidates)

This section lists **candidate** primitives. Final choices must be
documented once they are fixed in `core_crypto/`.

Candidates (to be confirmed):

- Symmetric encryption:
  - AEAD:
    - XChaCha20-Poly1305  
      **or**
    - AES-256-GCM
- Key derivation:
  - HKDF (HMAC-based Key Derivation Function)
- Authentication / identity:
  - Ed25519 key pairs for long-term identity keys (optional in v1)
- Randomness:
  - OS-provided CSPRNG via Rust (rand_core / getrandom).

Once the actual primitives are chosen and implemented, this section must
be updated with:

- exact algorithms,
- key sizes,
- nonce formats,
- library versions.

---

## 4. Session model (conceptual)

Each call uses a **session context**:

- defined and owned by `core_crypto`,
- identified by a local handle on the Android side,
- includes:
  - root key material,
  - encryption + decryption keys,
  - sequence / counter state (for replay protection),
  - negotiated protocol version and capabilities.

Lifecycle (simplified):

1. Session creation during call setup:
   - handshake between caller and callee (details TBD),
   - derive shared secret,
   - expand into traffic keys.

2. Active session:
   - each outbound frame:
     - gets an incremented counter,
     - is encrypted with the current send key,
     - may update internal state (e.g. for ratcheting).

3. Session teardown:
   - keys are zeroed in memory,
   - references on the Android side are cleared,
   - no persistent storage of session keys.

Any long-term keys (if used) must be handled separately and not be
exposed to regular app logs or debugging facilities.

---

## 5. Frame encryption / decryption mapping

On a conceptual level:

- **Outbound** (Android → Rust → Wire):

  1. Media layer creates a plaintext payload:
     - audio frame (PCM / Opus bytes),
     - control message,
     - keepalive.

  2. Frame builder composes a `FrameHeaderV1` + payload.

  3. `core_crypto`:
     - takes session context,
     - encrypts the payload (and/or parts of the header) with AEAD,
     - returns an `EncryptedFrame`.

  4. Transport sends the raw encrypted bytes over GhostNet.

- **Inbound** (Wire → Rust → Android):

  1. Transport receives raw bytes from the network.

  2. Frame parser (Rust or Android, design TBD):
     - separates header and ciphertext,
     - validates basic structure (length, type, etc.).

  3. `core_crypto`:
     - uses session context and counters,
     - decrypts the payload via AEAD,
     - returns plaintext payload (e.g. Opus frame).

  4. Media layer dispatches to:
     - decoder (Opus),
     - audio playback,
     - control handlers.

The exact division between “header parsing in Rust” vs “in Android”
must be decided and then documented both here and in
`GHOSTNET_WIRE_SPEC_v1.md`.

---

## 6. Error handling & replay protection (TBD)

Crypto-related errors include:

- invalid ciphertext / authentication failure,
- out-of-window sequence numbers (possible replay),
- inconsistent protocol versions,
- unexpected frame types.

Principles:

- distinguish between:
  - **hard errors** (abort session),
  - **soft errors** (skip frame, continue session).

- never leak sensitive details into logs:
  - log only coarse error categories,
  - no dumps of keys, nonces, or raw payloads.

The exact replay protection and windowing model (e.g. per-direction
counters with sliding window) must be specified once `core_crypto`
implements it.

---

## 7. Implementation notes

When wiring Android to the Rust core:

- Do:
  - define a minimal FFI surface,
  - pass opaque handles instead of raw pointers,
  - centralise all JNI calls in a small number of files.

- Do not:
  - implement “temporary” crypto on the Android side,
  - duplicate key derivation logic in Kotlin,
  - bypass `core_crypto` for “debug-only” shortcuts.

Any deviation from the documented crypto flow must be:

- explicitly justified,
- documented in `docs/tech/DECISION_*.md`,
- reviewed for security and legal impact.

---

## 8. TODOs for v1

This document must be extended with:

- final list of primitives (algorithms, parameters),
- detailed handshake description (if applicable),
- key schedule diagrams,
- replay protection scheme,
- mapping to actual Rust types and modules.

Until then, this stub:

- gives new contributors a mental model,
- ensures all crypto-sensitive work flows through `core_crypto`,
- and provides a single place to record crypto design decisions.
