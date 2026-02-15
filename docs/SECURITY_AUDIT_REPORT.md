# StealthX Platform -- Security Audit Report

**Date:** 2026-02-15
**Auditor:** Internal Security Review
**Scope:** Android Client, Rust Crypto Library, Backend Signaling Server
**Classification:** Confidential

---

## 1. Executive Summary

This report documents the findings of a comprehensive security audit of the StealthX platform. The audit covered three primary components: the Android client application, the Rust cryptographic library, and the Node.js backend signaling server.

**Overall Risk Assessment: CRITICAL**

The audit identified **48 findings** across all components:

| Severity | Android Client | Rust Crypto | Backend Server | Total |
|----------|---------------|-------------|----------------|-------|
| Critical | 4             | 1           | 2              | **7** |
| High     | 6             | 3           | 9              | **18**|
| Medium   | 8             | 5           | 10             | **23**|
| **Total**| **18**        | **9**       | **21**         | **48**|

The most severe class of findings involves the Android client's cryptographic layer, where multiple encryption components are either stubbed out or silently degrade to plaintext. This means that, despite the presence of an encryption API surface, **no actual end-to-end encryption is enforced in production builds**. The Rust FFI layer contains a buffer overflow in its core encrypt/decrypt path. The backend server exposes unauthenticated debug endpoints and hardcoded TURN credentials.

All Critical and High findings are being remediated in this commit. Medium findings have been filed as GitHub issues for tracking.

---

## 2. Android Client Findings

### 2.1 Critical

| ID   | Severity | File / Line | Description | Status |
|------|----------|-------------|-------------|--------|
| C-01 | Critical | `WireCryptoStub.kt`, `SessionCryptoContext.kt`, `MediaEncryptor.kt` | All encryption stubs are no-op implementations. `WireCryptoStub`, `SessionCryptoContext`, and `MediaEncryptor` return plaintext unmodified. Any data passed through these components is transmitted without encryption, completely defeating the stated E2E encryption guarantee. | Fixed |
| C-02 | Critical | `SessionCipherEngine.kt:25-28,31-33` | `SessionCipherEngine` silently falls back to plaintext when the native crypto library is unavailable. No error is raised, no log is emitted at WARN or above, and the caller receives no indication that encryption was skipped. This means a missing or failed native load results in full plaintext transmission without any user or developer awareness. | Fixed |
| C-03 | Critical | `FakeX25519.kt`, `SessionKeyDerivation.kt` | Production code paths reference `FakeX25519` for key agreement and `SessionKeyDerivation` uses a non-cryptographic derivation function. The fake X25519 implementation does not perform an actual Diffie-Hellman exchange; it returns deterministic, predictable output. HKDF derivation in `SessionKeyDerivation.kt` uses trivial parameters, producing keys with no real entropy. | Fixed |
| C-04 | Critical | `MainActivity.java:46` | WebSocket endpoint is hardcoded to `ws://127.0.0.1:8080/signal`. This uses unencrypted WebSocket (not WSS), connects only to localhost (non-functional on real devices), and embeds infrastructure configuration directly in source code rather than using build-time configuration or a discovery mechanism. | Fixed |

### 2.2 High

| ID   | Severity | File / Line | Description | Status |
|------|----------|-------------|-------------|--------|
| H-01 | High | `SessionCipherContext.kt`, `SessionCryptoContext.kt`, `HkdfSha256.kt` | Cryptographic key material is never zeroed from memory after use. `SessionCipherContext` retains the session key in a field that persists for the object lifetime. `SessionCryptoContext` stores derived keys in plain byte arrays with no cleanup. `HkdfSha256` leaves intermediate PRK and OKM bytes on the heap. An attacker with memory access (debug, dump, or exploit) can recover key material long after session completion. | Fixed |
| H-02 | High | `EphemeralKeyProvider.kt` | `EphemeralKeyProvider` returns private key material as an immutable `String`. Java `String` objects are interned, cannot be reliably zeroed, and may persist in memory indefinitely across garbage collection cycles. Key material must be returned as `ByteArray` to allow explicit clearing. | Fixed |
| H-03 | High | `ReplayDetector.kt` | `ReplayDetector.check()` logs duplicate message IDs but returns `void`. There is no mechanism for callers to determine whether a message is a replay. The detector is purely informational and provides zero enforcement against replay attacks. | Fixed |
| H-04 | High | `NonceManager.kt` | When the internal nonce counter overflows its maximum value, it wraps to `1` instead of throwing or regenerating the key context. This causes nonce reuse with the same key, which is catastrophic for AEAD ciphers (AES-GCM, ChaCha20-Poly1305) as it allows plaintext recovery and forgery. | Fixed |
| H-05 | High | `GhostTransport.kt` | `GhostTransport` uses `java.util.Random` for generating transport-layer randomness (message IDs, padding, jitter). `java.util.Random` is a linear congruential generator with a 48-bit seed; its output is fully predictable after observing a small number of values. Must use `java.security.SecureRandom`. | Fixed |
| H-06 | High | `HkdfSha256.kt` | HKDF-SHA256 implementation uses a hardcoded all-zero byte array as the salt parameter. Per RFC 5869, while a zero-length salt is permissible (the extract step uses a default), a hardcoded all-zero salt of fixed length reduces the entropy extraction and is inconsistent with the protocol specification. A proper random or context-derived salt must be used. | Fixed |

### 2.3 Medium

| ID   | Severity | File / Line | Description | Status |
|------|----------|-------------|-------------|--------|
| M-01 | Medium | `GhostTransport.kt`, `MainActivity.java` | No TLS is configured on the WebSocket connection. No certificate pinning is implemented. Connections are vulnerable to passive eavesdropping and active MITM attacks at the transport layer. | GitHub Issue #101 |
| M-02 | Medium | Various crypto classes | Sensitive data including key material, plaintext fragments, and session identifiers are logged via `Log.d()` throughout the crypto layer. These logs are accessible to any app with `READ_LOGS` permission on older Android versions and persist on-device. | GitHub Issue #102 |
| M-03 | Medium | `OutgoingFrameQueue.kt` | `OutgoingFrameQueue` uses `java.util.LinkedList` without synchronization. Concurrent enqueue/dequeue from transport and application threads causes lost frames, corruption, or `ConcurrentModificationException`. | GitHub Issue #103 |
| M-04 | Medium | `TransportFrameQueue.kt` | `TransportFrameQueue` is a singleton accessed from multiple threads without any synchronization primitives. Race conditions can cause frame duplication, loss, or out-of-order delivery. | GitHub Issue #104 |
| M-05 | Medium | `GhostNetSession.java` | Static listener lists in `GhostNetSession.java` hold strong references to Activity-scoped listeners. Listeners are never removed, causing memory leaks proportional to the number of session lifecycle events. | GitHub Issue #105 |
| M-06 | Medium | `NonceManager.kt` | `System.currentTimeMillis()` is used as a nonce seed or component. Millisecond-resolution wall-clock time is predictable and may collide across devices or when the clock is adjusted. | GitHub Issue #106 |
| M-07 | Medium | `GhostNetSession.java` | Exceptions thrown by registered listeners are caught with an empty catch block. Errors in listener callbacks (including security-relevant callbacks) are silently discarded, preventing error propagation and diagnostics. | GitHub Issue #107 |
| M-08 | Medium | `GhostControlRouter.kt` | `GhostControlRouter.isControlFrame()` unconditionally returns `true` regardless of frame type. This causes all frames -- including data frames -- to be routed through the control path, bypassing data-path processing and potentially skipping encryption or validation steps. | GitHub Issue #108 |

---

## 3. Rust Crypto Library Findings

### 3.1 Critical

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| RC-01 | Critical | `ffi/mod.rs:48,79` | `core_crypto_encrypt` and `core_crypto_decrypt` perform `ptr::copy_nonoverlapping` into the caller-provided output buffer without validating that the buffer has sufficient capacity. If the caller provides a buffer smaller than the ciphertext (plaintext + tag for encrypt, or ciphertext - tag for decrypt), the write overflows the allocation, corrupting adjacent heap memory. This is exploitable for arbitrary code execution. | Fixed |

### 3.2 High

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| RH-01 | High | `session/mod.rs:19` | `SessionState` derives `Zeroize` but annotates the `aead_key` field with `#[zeroize(skip)]`. The most security-sensitive field in the struct -- the symmetric AEAD key -- is explicitly excluded from zeroization. When a `SessionState` is dropped, the key remains in memory. | Fixed |
| RH-02 | High | `ffi/mod.rs` (multiple) | Intermediate byte arrays allocated inside FFI bridge functions (`key_bytes`, `key_arr`, `priv_arr`, `combined`) are stack- or heap-allocated, populated with key material, and then dropped without zeroization. The Rust compiler may also optimize away naive zeroing. All sensitive intermediates must use `Zeroizing<>` wrappers or explicit `zeroize()` calls before drop. | Fixed |
| RH-03 | High | `identity/mod.rs:11` | `IdentityKeyPair` holds the long-term identity private key but does not derive `ZeroizeOnDrop`. When an `IdentityKeyPair` goes out of scope, the private key bytes remain on the heap until the allocator reuses the page. Long-term identity keys have the highest sensitivity and longest exposure window. | Fixed |

### 3.3 Medium

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| RM-01 | Medium | `aead/mod.rs` | The AEAD encryption/decryption module does not track or reject previously seen nonces. Without replay detection at this layer, a network attacker can re-send captured ciphertexts and they will be accepted as valid. | GitHub Issue #201 |
| RM-02 | Medium | `ffi/mod.rs` (`generateKeyPair`) | The `generateKeyPair` JNI function generates a full X25519 keypair but only returns the public key to the caller. The private key is silently discarded. This wastes entropy and suggests an incomplete implementation. | GitHub Issue #202 |
| RM-03 | Medium | `hkdf/mod.rs` | The default HKDF invocation passes `None` for the salt parameter. While RFC 5869 allows this, it weakens the extract step when input keying material has non-uniform entropy. A random or protocol-defined salt should be used. | GitHub Issue #203 |
| RM-04 | Medium | `session/mod.rs` (`SessionState::close`) | `SessionState::close()` sets an internal flag and releases resources but does not zeroize the AEAD key or other sensitive fields. Combined with RH-01, the key survives both `close()` and `drop()`. | GitHub Issue #204 |
| RM-05 | Medium | `session/mod.rs` | `SessionKey.bytes` is declared `pub`, allowing any code in the crate (or downstream via re-export) to copy the raw key bytes into an untracked variable. This bypasses any zeroization guarantees on the original `SessionKey`. The field should be `pub(crate)` or accessed only through a method that returns a `Zeroizing` wrapper. | GitHub Issue #205 |

---

## 4. Backend Server Findings

### 4.1 Critical

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| BC-01 | Critical | `server.js:10-17` | TURN server credentials (`username` and `credential`) are hardcoded as fallback defaults in the server source code. If environment variables are not set, the server uses these static credentials for all TURN allocations. An attacker who reads the source (or any published build artifact) can use the TURN server as an open relay, consuming bandwidth and potentially relaying malicious traffic attributed to the platform. | Fixed |
| BC-02 | Critical | `server.js:102-124` | Debug endpoints `GET /routing/list` and `GET /clients/list` are exposed without any authentication or authorization. They return the full internal routing table (client IDs mapped to connection metadata) and the complete connected-client list. An attacker can enumerate all active users, their session state, and internal identifiers, enabling targeted attacks and full user surveillance. | Fixed |

### 4.2 High

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| BH-01 | High | `server.js` (PKD routes) | Public Key Directory (PKD) endpoints for registering, overwriting, and deleting identity keys require no authentication. Any client -- or an external attacker -- can register a key for any user ID, overwrite a legitimate user's key with an attacker-controlled key (enabling MITM), or delete a user's key (denial of service). | Fixed |
| BH-02 | High | `server.js` (WebSocket handler) | WebSocket connections are accepted without any authentication handshake. There is no token, session cookie, or challenge-response mechanism. Any TCP client that completes the WebSocket upgrade is treated as a legitimate peer. | Fixed |
| BH-03 | High | `server.js` (client registration) | Client ID assignment is entirely client-controlled with no verification. A malicious client can claim any arbitrary ID, impersonating another user and receiving their messages. There is no uniqueness enforcement beyond in-memory map overwrite. | Fixed |
| BH-04 | High | `server.js` (WebSocket handler) | No `Origin` header validation is performed on WebSocket upgrade requests. A malicious web page can open a cross-origin WebSocket to the signaling server and interact with it on behalf of a victim whose browser visits the page (WebSocket cross-site hijacking). | Fixed |
| BH-05 | High | `server.js` (WebSocket config) | No `maxPayload` option is set on the WebSocket server. The `ws` library defaults to approximately 100 MB. A single malicious client can send a 100 MB frame, exhausting server memory and causing denial of service. | Fixed |
| BH-06 | High | `server.js` | Rate limiting middleware modules exist in the codebase but are not integrated into the Express app or WebSocket handler. There is no protection against brute-force, enumeration, or flood attacks. | Fixed |
| BH-07 | High | `server.js` (CALL_ACCEPT handler) | The `CALL_ACCEPT` signaling message is forwarded to the caller without verifying that the accepting client is the intended callee. Any client that knows a call ID can accept the call, potentially intercepting the media session. | Fixed |
| BH-08 | High | `server.js` (CALL_END handler) | The `CALL_END` signaling message is processed without verifying that the sender is a participant in the call. Any client can terminate any active call by sending a `CALL_END` with a known call ID. | Fixed |
| BH-09 | High | `server.js` | There is no per-IP connection limit. Combined with unbounded in-memory data structures for routing tables and client state, an attacker can open thousands of connections from a single IP, exhausting server memory and file descriptors. | Fixed |

### 4.3 Medium

| ID    | Severity | File / Line | Description | Status |
|-------|----------|-------------|-------------|--------|
| BM-01 | Medium | `server.js` (client registration) | No length or character validation is performed on `clientId`. An attacker can register with an excessively long ID (megabytes), IDs containing control characters, or IDs that collide with internal map keys (e.g., `__proto__`, `constructor`). | GitHub Issue #301 |
| BM-02 | Medium | `server.js` (PKD routes) | No size limit is enforced on the `publicKey` field in PKD registration. An attacker can store arbitrarily large payloads in the key directory, consuming server memory. | GitHub Issue #302 |
| BM-03 | Medium | `server.js` (error handlers) | User-supplied input (client IDs, message types) is reflected in error response messages without sanitization. While the primary risk is information disclosure, this can facilitate XSS if error responses are rendered in a web context. | GitHub Issue #303 |
| BM-04 | Medium | `server.js` (SDP/ICE handling) | SDP offer/answer and ICE candidate fields are forwarded without validation. Malformed SDP can crash peer WebRTC stacks. Injected candidates can redirect media to attacker-controlled endpoints. | GitHub Issue #304 |
| BM-05 | Medium | `server.js` (binary handler) | Binary WebSocket frames that do not match any known protocol are echoed back to the sender. This creates an amplification vector and may leak information about server processing. | GitHub Issue #305 |
| BM-06 | Medium | `server.js` | The server listens on plain HTTP/WS with no TLS termination configured. All signaling traffic, including SDP (which contains IP addresses) and ICE candidates, is transmitted in cleartext. | GitHub Issue #306 |
| BM-07 | Medium | `server.js` | Incoming JSON messages are parsed with `JSON.parse()` and their properties are accessed directly. If an attacker sends a message with `__proto__` or `constructor` properties, prototype pollution may allow property injection on `Object.prototype`, affecting all subsequent object operations in the server process. | GitHub Issue #307 |
| BM-08 | Medium | `server.js` (`forwardBinaryToPeer`) | `forwardBinaryToPeer` performs a linear scan (O(n)) over the entire client connection table to locate the target peer. With a large number of connected clients, this becomes a performance bottleneck and can be exploited for algorithmic-complexity denial of service. | GitHub Issue #308 |
| BM-09 | Medium | `server.js` (GHOST_PREPARE handler) | The `GHOST_PREPARE` message type is handled without any authentication or session validation. An unauthenticated client can initiate ghost protocol preparation for any target, potentially consuming server resources or disrupting legitimate sessions. | GitHub Issue #309 |
| BM-10 | Medium | `server.js` | No CORS configuration is set on the Express HTTP server. API endpoints (including PKD and debug routes) are accessible from any origin. Combined with the lack of authentication, a malicious web page can perform full PKD manipulation on behalf of a visiting user. | GitHub Issue #310 |

---

## 5. Remediation Summary

### 5.1 Completed Remediations (This Commit)

All **Critical** and **High** findings (25 total) are addressed in this commit:

| Component | Critical Fixed | High Fixed | Total Fixed |
|-----------|---------------|------------|-------------|
| Android Client | 4 | 6 | 10 |
| Rust Crypto | 1 | 3 | 4 |
| Backend Server | 2 | 9 | 11 |
| **Total** | **7** | **18** | **25** |

**Key remediations applied:**

- **Android Client:** Replaced all crypto stubs with real implementations backed by the Rust native library. Removed plaintext fallback paths. Replaced `FakeX25519` with actual X25519 via the FFI bridge. Made WebSocket endpoint configurable via build config with WSS enforcement. Switched to `SecureRandom`, added key zeroization, enforced replay detection with boolean return, and fixed nonce overflow to throw rather than wrap.
- **Rust Crypto:** Added output buffer capacity validation with explicit length checks before all `ptr::copy_nonoverlapping` calls. Removed `#[zeroize(skip)]` from `aead_key`. Wrapped all FFI intermediates in `Zeroizing<>`. Added `ZeroizeOnDrop` derive to `IdentityKeyPair`.
- **Backend Server:** Removed hardcoded TURN credentials (server now fails to start if env vars are missing). Removed debug endpoints entirely. Added authentication middleware to PKD and WebSocket paths. Added `Origin` validation, `maxPayload` limit (64 KB), rate limiting integration, call participant verification, and per-IP connection limits.

### 5.2 Open Items (GitHub Issues)

All **Medium** findings (23 total) have been filed as GitHub issues for tracking:

| Component | Issue Range | Count |
|-----------|-------------|-------|
| Android Client | #101 -- #108 | 8 |
| Rust Crypto | #201 -- #205 | 5 |
| Backend Server | #301 -- #310 | 10 |
| **Total** | | **23** |

These should be prioritized in the next development cycle. Findings M-01/BM-06 (TLS) and M-02 (debug logging) carry the highest residual risk among the medium-severity items.

### 5.3 Recommendations

1. **Establish a CI gate** that fails the build if any crypto stub or fake implementation is present in a release build configuration.
2. **Add integration tests** that verify ciphertext differs from plaintext for every encrypt API.
3. **Deploy TLS termination** (via reverse proxy or direct) before any production exposure.
4. **Conduct a follow-up audit** after all medium findings are resolved, with particular focus on the threading model and memory safety of the Android transport layer.
5. **Implement automated dependency scanning** for both the Rust and Node.js dependency trees.

---

*End of report.*
