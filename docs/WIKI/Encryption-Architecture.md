> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-CRYPTO** | **DIVISION: StealthX // SecureCall**

---

# CRYPTOGRAPHIC OPERATIONS MANUAL

---
#### ████ CRYPTOGRAPHIC ARSENAL ████
---

SecureCall uses proven, peer-reviewed cryptographic algorithms:

| Component | Algorithm | Purpose |
|-----------|-----------|---------|
| **Symmetric Encryption** | XChaCha20-Poly1305 | 256-bit AEAD cipher with 192-bit extended nonce |
| **Key Exchange** | X25519 | Elliptic Curve Diffie-Hellman on Curve25519 |
| **Identity Authentication** | P-256 / SHA-256 | Android Keystore identity signs registration and call transcripts |
| **Session Key Lifecycle** | X25519 + HKDF-SHA256 | Separate key material for each call; discarded at call end |
| **Key Derivation** | HKDF-SHA256 | HMAC-based key derivation function |
| **Transport** | DTLS-SRTP | Encrypted peer-to-peer media transport |
| **Audio Codec** | Opus | 48kHz, adaptive bitrate 6-510 kbps |

---
#### ████ WHY THESE ALGORITHMS ████
---

### XChaCha20-Poly1305 (not AES-GCM)
- **Nonce safety:** 192-bit nonce virtually eliminates collision risk
- **No hardware dependency:** Performs well without AES-NI hardware acceleration
- **Proven:** Used by WireGuard, Cloudflare, and many modern systems
- **AEAD:** Authenticated Encryption with Associated Data prevents tampering

### X25519 (not RSA)
- **Speed:** ~100x faster than RSA-2048 key exchange
- **Key size:** 32 bytes vs 256+ bytes for RSA
- **Security margin:** 128-bit equivalent security
- **No padding oracle attacks:** Unlike RSA-OAEP

### Rust (not Java/OpenSSL)
- **Memory safety:** Buffer overflows, use-after-free eliminated at compile time
- **No garbage collector:** Deterministic key zeroization
- **Performance:** Native speed via JNI
- **Thread safety:** Data race protection by the compiler

---
#### ████ ENCRYPTED CHANNEL ESTABLISHMENT ████
---

```
  Alice (Caller)                    Server                    Bob (Callee)
       │                              │                            │
   1.  │──── CALL_INITIATE ──────────►│                            │
       │    (encrypted signaling)      │──── INCOMING_CALL ───────►│
       │                              │                            │
   2.  │                              │◄──── CALL_ACCEPT ─────────│
       │◄──── CALL_ACCEPTED ─────────│                            │
       │                              │                            │
   3.  │◄════ Signed, transcript-bound X25519 exchange ═════════►│
       │  Alice: a (private), A (public)                          │
       │  Bob:   b (private), B (public)                          │
       │  Shared: S = X25519(a, B) = X25519(b, A)                │
       │                              │                            │
   4.  │          HKDF-SHA256(S) → session_key                    │
       │                              │                            │
   5.  │      HMAC key confirmation + 6-digit security code       │
       │                              │                            │
   6.  │◄═══════ WebRTC P2P connection (DTLS-SRTP) ══════════════►│
       │                              │                            │
   7.  │  Voice Frame → Opus Encode → XChaCha20 Encrypt → Send    │
       │                                    Recv → Decrypt → Decode│
       │                              │                            │
```

### Step-by-Step

1. **Call Initiation:** Alice signs the invite transcript with her P-256 Android Keystore identity
2. **Call Accept:** Bob verifies the invite and signs his identity and ephemeral X25519 key
3. **Key Exchange:** X25519 Diffie-Hellman produces a shared secret
4. **Key Derivation:** HKDF-SHA256 binds the key to both signed transcripts
5. **Session Start:** Both clients verify HMAC confirmation before media starts
6. **P2P Connection:** Direct WebRTC connection established (bypasses server)
7. **Encrypted Audio:** Each voice frame uses the per-call key with a unique nonce

---
#### ████ PER-CALL KEY LIFECYCLE ████
---

SecureCall derives separate key material for each call using X25519 and HKDF-SHA256, then discards
that material when the call ends. XChaCha20-Poly1305 protects the application media frames during
the call. P-256 identity signatures authenticate the exchange for canonical
key-derived SecureIDs. The implementation does **not** include a Double Ratchet,
per-frame key ratcheting or post-compromise security. Human-readable aliases and
phone/custom-ID lookups remain server-resolved and require canonical-ID or
security-code verification when target authenticity matters.

---
#### ████ WHAT THE SERVER CANNOT SEE ████
---

The signaling server **only** handles:

| Data | Visible to Server? | Stored? |
|------|-------------------|---------|
| Voice content | No (E2E encrypted) | No |
| Encryption keys | No (generated on device) | No |
| Call metadata | Temporary connection IDs only | No |
| Contact list | No (device-only) | No |
| IP address | During signaling only | Not logged |

---
#### ████ VOICE FRAME ENCRYPTION PIPELINE ████
---

Each audio frame is processed as follows:

```
  Microphone
      │
      ▼
  Opus Encode (48kHz)
      │
      ▼
  Per-call key + unique frame nonce
      │
      ▼
  XChaCha20-Poly1305 Encrypt(frame, frame_key, nonce)
      │
      ▼
  WebRTC/DTLS-SRTP Send
      │
      ▼ (network)
      │
  WebRTC/DTLS-SRTP Receive
      │
      ▼
  XChaCha20-Poly1305 Decrypt(ciphertext, frame_key, nonce)
      │
      ▼
  Opus Decode
      │
      ▼
  Speaker
```

---
#### ████ SOURCE CODE ████
---

The cryptographic implementation can be verified in the source code:

- **Rust Crypto Engine:** [`core_crypto/`](https://github.com/NeaBouli/stealth/tree/main/core_crypto)
- **Android Integration:** [`client_android/app/src/main/java/com/securecall/app/`](https://github.com/NeaBouli/stealth/tree/main/client_android/app/src/main/java/com/securecall/app)
- **Security Monitor:** [`client_android/app/src/main/java/com/securecall/app/security/`](https://github.com/NeaBouli/stealth/tree/main/client_android/app/src/main/java/com/securecall/app/security)

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
