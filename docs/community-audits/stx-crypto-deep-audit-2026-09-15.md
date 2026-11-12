# StealthX / SecureCall — Cryptography Deep Audit

- **Series:** Collateral Web3 Open Audits
- **Date:** 2026-09-15
- **Target:** NeaBouli/stealth @ `e06d018417bae5be16bf6b89d0a1887586a99d3b`
- **Scope:** `core_crypto/` (Rust, every `.rs` file + tests + Cargo.lock), JNI boundary, Android crypto usage, transport pinning, protocol claims vs implementation
- **Method:** full read of the crate by a deep-recon agent + lead verification of every finding at file:line; no code execution of crypto paths; no secrets read
- **Register:** STX-21 … STX-28 (this report) — **0 Critical / 2 High / 4 Medium / 1 Low / 1 Info**

---

## Executive summary

The primitive layer is solid: XChaCha20-Poly1305 with random 192-bit nonces from `OsRng`, X25519 with a low-order-point check in the JNI path, HKDF-SHA256, zeroize-on-drop on all Rust key types, current patched RustCrypto dependencies, and no `unwrap()` on untrusted FFI input. The problems are at the **protocol layer**: the key exchange is unauthenticated (the signaling server can MITM), HKDF binds neither session nor transcript, and the loudly advertised **Double Ratchet does not exist** — a call uses one static key end-to-end. Transport security is degraded because the declarative certificate pin-set expired on 2026-08-14.

## Severity table

| ID | Severity | Title |
|----|----------|-------|
| STX-21 | High | Unauthenticated key exchange: signaling server can silently MITM calls; no transcript binding |
| STX-22 | High | "Double Ratchet" advertised everywhere — not implemented (one static key per call) |
| STX-23 | Medium | Declarative certificate pin-set expired 2026-08-14 (Android now ignores it) |
| STX-24 | Medium | No replay protection, no AAD, no direction separation on the E2E media path |
| STX-25 | Medium | JNI/Java layers leave key-material copies unzeroized |
| STX-26 | Medium | GhostNet mock crypto layer (plaintext passthrough, fabricated "shared secrets") present in tree |
| STX-27 | Low | FFI hardening gaps: no `catch_unwind`, drop-based identity zeroization, no AAD parameter |
| STX-28 | Info | `core_crypto/README.md` is stale (claims "skeleton / no real encryption"); no KAT/fuzz coverage |

---

## STX-21 — High — Unauthenticated key exchange: server can silently MITM calls

**Evidence:** X25519 ephemeral public keys are relayed as base64 in `CALL_INVITE`/`CALL_ACCEPT` through the WebSocket server with no authentication — no identity keys, no fingerprints, no SAS/QR verification UI anywhere in the client (`client_android/.../net/WebSocketService.kt:1056-1097, 1301-1320`; grep for fingerprint/SAS/verify/identity returns nothing). Key derivation in `core_crypto/src/ffi/mod.rs:265-269` uses `session::derive_key(shared, None, b"SecureCall-AEAD-Key-v1")` — **salt=None**, so the session key is not bound to the sessionId, the transcript, or the peers' identities. Combined with STX-01 (unauthenticated REGISTER lets anyone claim an ID and receive its invites), no server compromise is even needed — ID hijack suffices.

**Impact:** a malicious or compelled signaling server — or any ID hijacker — substitutes both public keys and decrypts/re-encrypts all audio. The headline guarantee "zero-knowledge server cannot decrypt calls" (README.md:38,123) holds only against a *passive* server. Per-call ephemeral keys still give per-call forward secrecy, so passive recordings stay safe; active interception does not.

**Recommendation:** long-term per-install identity keys with fingerprint/SAS verification UI (Signal model); at minimum bind the transcript: `salt = SHA-256(sessionId ‖ pubkeyA ‖ pubkeyB)` plus a post-handshake key-confirmation message, and document the remaining trust model honestly until identity keys ship.

## STX-22 — High — "Double Ratchet" advertised everywhere — not implemented

**Evidence:** no ratchet code exists anywhere. `core_crypto/src/session/mod.rs:29` contains only a one-shot HKDF (`SessionState::from_shared_secret`) producing a single static AEAD key per call (139 lines total, no DH-ratchet, no KDF chain, no skipped-message keys). The only code references are future-work comments (`SessionKeyController.kt:11` "Später: Noise / DoubleRatchet / X3DH"; `docs/SECURITY_DESIGN.md:189` lists it as a design goal). Meanwhile the claim appears as fact in: README.md:177 ("Forward Secrecy: Double Ratchet protocol"), website/security.html:101-107 ("Each voice frame uses a fresh key derived from the ratchet state"), docs/WIKI/Encryption-Architecture.md:87-92 (same wording), marketing/play_store/en/full_description.txt:9,73, fastlane listing copy.

**Impact:** every public channel promises per-frame key freshness and post-compromise security that the product does not provide. For a security product this is the most damaging class of inaccuracy: users (journalists, activists — the stated target groups) make safety decisions based on it. Also a store-review/consumer-protection risk.

**Recommendation:** correct all copy to the real model ("ephemeral per-call X25519 + HKDF; one key per call; forward secrecy across calls") now; implement an actual ratchet (or Noise) before re-adding the claim.

## STX-23 — Medium — Declarative certificate pin-set expired 2026-08-14

**Evidence:** `client_android/app/src/main/res/xml/network_security_config.xml:14` — `<pin-set expiration="2026-08-14">` with the comment "Rotate before 2026-08-14". Audited on 2026-09-15: Android now **ignores** these declarative pins for `api.stealthx.tech` and falls back to plain CA validation. Mitigating: the OkHttp `CertificatePinner` (`NetworkManager.kt:215-219`, three pins: leaf + R12 + ISRG Root X1, no expiry) still pins all OkHttp-based traffic (signaling WS, heartbeat, HTTP) in every flavor, so the practical degradation is limited to any non-OkHttp TLS path (none found for the API) — but the two layers now disagree, and the update-check host `api.github.com` was never pinned (`UpdateChecker.kt:33-50`, stock client). Note: wiki Known-Issues `#101` "No TLS certificate pinning on WebSocket connection" is stale/false — the WS runs through the pinned OkHttp client (see STX-43).

**Recommendation:** renew/rotate the declarative pins (or drop the XML layer and document OkHttp-only pinning); add pin rotation to the release checklist with calendar lead time; consider pinning or otherwise authenticating the update-metadata channel.

## STX-24 — Medium — No replay protection, no AAD, no direction separation on the media path

**Evidence:** `WebSocketService.kt:457-470, 789-800` uses raw `CoreCrypto.encrypt` with a random 24-byte nonce — no frame counter, no associated data, one HKDF output used for both directions. Both replay detectors are dead code: the Rust `ReplayDetector` (`core_crypto/src/aead/mod.rs:91-146`, expects u64 counter nonces — incompatible with the random-nonce scheme) is never invoked; the Kotlin `ReplayDetector.kt`/`NonceManager.kt` are not in the live path either.

**Impact:** reflection/replay across directions is cryptographically possible at the E2E layer. Practical impact is low today (WebRTC datachannel DTLS adds transport protection; the only E2E-layer adversary is the peer, who can already say anything) — but the bridge-advertised "ReplayDetector" is decorative, and the gap becomes real the moment a relay or multi-hop path is added.

**Recommendation:** add per-frame counters + AAD (`sessionId`, direction bit) and wire a sliding-window detector, or remove the dead detectors and stop citing them.

## STX-25 — Medium — JNI/Java layers leave key-material copies unzeroized

**Evidence:** `core_crypto/src/ffi/mod.rs` — `convert_byte_array` outputs (`key_bytes`, `priv_bytes` at `:160,199,237`) and the `decrypted`/`plaintext` Vecs (`:53,100`) are dropped without zeroization; only stack arrays are wiped; the Rust-side plaintext Vec of `core_crypto_decrypt` is copied out but not zeroized. Java side: the 64-byte `keypair` arrays in `WebSocketService.sendCallInvite/sendCallAccept` (`:1058,1094`) are not wiped after `copyOfRange`. Positive: Rust key types themselves (`SessionKey`, `AeadKey`, `SharedSecret`, `SessionState`) zeroize on drop, and `WebSocketService.kt:115-119` explicitly zeroes `sessionKey`/`localPrivKey` on session clear.

**Impact:** key material persists in freed heap (Rust + ART) longer than necessary — a forensic/cold-boot consideration, relevant to the advertised STEALTH-DELETE guarantees.

**Recommendation:** wrap copies in zeroizing types; wipe Java arrays after use.

## STX-26 — Medium — GhostNet mock crypto layer present in tree

**Evidence (all under `client_android/app/src/main/java/com/securecall/app/ghostnet/`):** `crypto/SessionCryptoContext.kt:36` — `encryptOutbound/decryptInbound` are plaintext passthroughs logged "(NO REAL ENCRYPTION)"; `media/crypto/MediaEncryptor.kt:20-33` returns plaintext; `handshake/HandshakeEngine.kt:36-53` `performMockHandshake` fabricates a random "shared secret" locally; `crypto/SessionKeyDerivation.kt:73-94` **ignores the shared secret and derives keys from randomness**; `GhostTransport.kt:97,110` calls the passthrough variants. The live call path does **not** use this layer (live path: Opus → `CoreCrypto.encrypt` → datachannel, fail-closed; `SessionCipherEngine.kt:26-35` throws rather than sending plaintext).

**Impact:** none today; if any future wiring connects these classes to a live path, calls go out unencrypted or with keys the peer does not share. This is exactly the kind of dead-but-armed code that survives refactors unnoticed. It also muddies the public GhostNet story (see STX-37: the live wiki still advertises GhostNet IP masking).

**Recommendation:** delete or quarantine the mock layer behind an explicit `GHOSTNET_MOCK` build flag before release.

## STX-27 — Low — FFI hardening gaps

**Evidence:** no `catch_unwind` around FFI bodies — any future panic across the boundary is UB/abort (`ffi/mod.rs`); `IdentityKeyPair::drop` (`identity/mod.rs:49-56`) zeroizes by assigning `StaticSecret::from([0;32])` — works, but relies on the assignment not being elided (derived `Zeroize`/`ZeroizeOnDrop` would be cleaner); `encrypt_frame_aead` exposes no AAD parameter (`aead/mod.rs:49`, payload passed with empty AAD).

**Recommendation:** add `catch_unwind` + abort-safe wrappers; derive zeroization; add the AAD parameter when implementing STX-24.

## STX-28 — Info — Stale crate README; test-depth gaps

**Evidence:** `core_crypto/README.md` still describes the crate as "Skeleton / no real encryption / Dummy-Key" while it now implements real XChaCha20-Poly1305/X25519/HKDF — an auditor reading the README could conclude the code is fake. Tests: solid roundtrip/tamper/wrong-key/multi-frame coverage (28 unit + 6 E2E per bridge records), but no known-answer vectors against reference implementations, no nonce-misuse resistance tests, no fuzzing.

**Recommendation:** refresh the README; add Wycheproof/reference KATs and a cargo-fuzz target for the frame parser.

---

## Verified strengths (evidence-checked)

- Primitives/parameters correct: XChaCha20-Poly1305, random 192-bit nonces from `OsRng` (`aead/mod.rs:46`), X25519 via `StaticSecret` (`identity/mod.rs:25`), HKDF-SHA256 (`session/mod.rs:54`), 32-byte keys throughout.
- **Low-order-point check on DH present in the JNI path** (`ffi/mod.rs:260-262`) — beyond the `x25519-dalek` 2.x default.
- FFI discipline: null-pointer checks and documented `# Safety` contracts on all three `unsafe extern "C"` functions; buffer-capacity protocol (`-2` = too small); no `unwrap()`/`panic!` on untrusted input (the single `expect` at `session/mod.rs:58` is unreachable by construction).
- Dependency versions (Cargo.lock) all current and patched: chacha20poly1305 0.10.1, x25519-dalek 2.0.1, curve25519-dalek 4.1.3 (timing fix included), hkdf 0.12.4, sha2 0.10.9, zeroize 1.8.2, rand 0.8.6, getrandom 0.2.17, jni 0.21.1.
- Zeroize-on-drop on all Rust key types; Kotlin side zeroes session key material on session clear.
- Per-call ephemeral keypairs — passive-compromise forward secrecy across calls is genuine.
- Clippy `-D warnings` clean per bridge records.
