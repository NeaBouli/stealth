# Encryption Architecture

## Cryptographic Primitives

SecureCall uses proven, peer-reviewed cryptographic algorithms:

| Component | Algorithm | Purpose |
|-----------|-----------|---------|
| **Symmetric Encryption** | XChaCha20-Poly1305 | 256-bit AEAD cipher with 192-bit extended nonce |
| **Key Exchange** | X25519 | Elliptic Curve Diffie-Hellman on Curve25519 |
| **Forward Secrecy** | Double Ratchet | Per-session key derivation with ratcheting |
| **Key Derivation** | HKDF-SHA256 | HMAC-based key derivation function |
| **Transport** | DTLS-SRTP | Encrypted peer-to-peer media transport |
| **Audio Codec** | Opus | 48kHz, adaptive bitrate 6-510 kbps |

## Why These Algorithms?

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

## Call Flow

```
  Alice (Caller)                    Server                    Bob (Callee)
       │                              │                            │
   1.  │──── CALL_INITIATE ──────────►│                            │
       │    (encrypted signaling)      │──── INCOMING_CALL ───────►│
       │                              │                            │
   2.  │                              │◄──── CALL_ACCEPT ─────────│
       │◄──── CALL_ACCEPTED ─────────│                            │
       │                              │                            │
   3.  │◄═══════════════ X25519 Key Exchange ═══════════════════►│
       │  Alice: a (private), A (public)                          │
       │  Bob:   b (private), B (public)                          │
       │  Shared: S = X25519(a, B) = X25519(b, A)                │
       │                              │                            │
   4.  │          HKDF-SHA256(S) → session_key                    │
       │                              │                            │
   5.  │          Double Ratchet initializes                       │
       │                              │                            │
   6.  │◄═══════ WebRTC P2P connection (DTLS-SRTP) ══════════════►│
       │                              │                            │
   7.  │  Voice Frame → Opus Encode → XChaCha20 Encrypt → Send    │
       │                                    Recv → Decrypt → Decode│
       │                              │                            │
```

### Step-by-Step

1. **Call Initiation:** Alice sends an encrypted signaling message through the server
2. **Call Accept:** Bob accepts; both parties now have each other's public key
3. **Key Exchange:** X25519 Diffie-Hellman produces a shared secret
4. **Key Derivation:** HKDF-SHA256 derives the session encryption key from the shared secret
5. **Ratchet Init:** Double Ratchet protocol initializes with the session key
6. **P2P Connection:** Direct WebRTC connection established (bypasses server)
7. **Encrypted Audio:** Each voice frame is encrypted with a unique key from the ratchet

## Perfect Forward Secrecy

The **Double Ratchet** protocol ensures that:

- Each call session uses **unique encryption keys**
- Compromising one key does **not** expose past calls
- Compromising one key does **not** expose future calls
- Each voice frame uses a **fresh key** derived from the ratchet state

```
Session 1: Key_1 ─── cannot derive ──→ Key_2
Session 2: Key_2 ─── cannot derive ──→ Key_3
Session 3: Key_3 ─── cannot derive ──→ Key_1
```

## What the Server Cannot See

The signaling server **only** handles:

| Data | Visible to Server? | Stored? |
|------|-------------------|---------|
| Voice content | No (E2E encrypted) | No |
| Encryption keys | No (generated on device) | No |
| Call metadata | Temporary connection IDs only | No |
| Contact list | No (device-only) | No |
| IP address | During signaling only | Not logged |

## Voice Frame Encryption

Each audio frame is processed as follows:

```
  Microphone
      │
      ▼
  Opus Encode (48kHz)
      │
      ▼
  Ratchet → derive frame_key
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

## Source Code

The cryptographic implementation can be verified in the source code:

- **Rust Crypto Engine:** [`core_crypto/`](https://github.com/NeaBouli/stealth/tree/main/core_crypto)
- **Android Integration:** [`client_android/app/src/main/java/com/securecall/app/`](https://github.com/NeaBouli/stealth/tree/main/client_android/app/src/main/java/com/securecall/app)
- **Security Monitor:** [`client_android/app/src/main/java/com/securecall/app/security/`](https://github.com/NeaBouli/stealth/tree/main/client_android/app/src/main/java/com/securecall/app/security)

---

[← Back to Home](Home.md)
