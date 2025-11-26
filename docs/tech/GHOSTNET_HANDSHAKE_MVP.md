# GhostNet Handshake (MVP Skeleton)

**Patch:** 199  
**Status:** Platzhalter, nur Struktur – keine echte Kryptografie.

## 1. Komponenten

- `ghostnet.handshake.HandshakeState`
  - IDLE, OUTGOING, INCOMING, ESTABLISHED, FAILED

- `ghostnet.handshake.HandshakeController`
  - `startOutgoing(remotePub: ByteArray)`
  - `acceptIncoming(remotePub: ByteArray)`
  - `reset()`
  - `getState()`

- Abhängigkeiten:
  - `GhostNetKeyMaterial` (liefert Ephemeral Keys)
  - `SessionKeyController` (hält abgeleiteten Session Key)
  - `CoreCrypto.deriveSessionKey()` (JNI-Stub, später Rust)

## 2. Ablauf (MVP)

### Outgoing

1. STATE = OUTGOING
2. Erzeuge Ephemeral Keypair (Fake Random Bytes)
3. `deriveSessionKey(localPriv, remotePub)`
4. Wenn `SessionKeyController.hasSessionKey() == true` → STATE = ESTABLISHED  
   sonst → STATE = FAILED

### Incoming

1. STATE = INCOMING
2. Erzeuge Ephemeral Keypair
3. `deriveSessionKey(localPriv, remotePub)`
4. gleiche Logik

## 3. TODO (später)

- Echte X25519 / X3DH-Implementierung (Rust)
- Austausch der Public Keys über Signaling (Backend)
- Nonce-/PreKey-Handling
- Schutz gegen Replay / Downgrade
- Integrations-Tests und Fuzzing
