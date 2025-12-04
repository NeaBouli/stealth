# GhostNet – Security Model (Stub)

Status: v0.1 (draft)  
Scope: high-level security assumptions and goals for SecureCall / Stealth.

This document is intentionally short. It gives new developers a **mental model**
for what the system protects, against whom, and where the hard limits are.

For legal/compliance details, see:

- `docs/legal/LEGAL_POSITIONING.md`
- `docs/legal/LAW_ENFORCEMENT_FAQ.md`
- `docs/privacy/LOGGING_POLICY.md`

---

## 1. Primary security goals

GhostNet is designed to:

1. **Protect call content**  
   - End-to-end encryption for voice data.  
   - Servers never see plaintext audio.  
   - No key escrow, no master keys.

2. **Minimise exploitable metadata**  
   - No long-term call history (no CDR database).  
   - No global user directory, no social graph.  
   - Infra logs are short-lived and minimised.

3. **Resist server compromise (within reason)**  
   - Attacker who fully compromises one server
     should not be able to:
     - decrypt past calls,
     - silently “join” live calls,
     - rebuild a reliable long-term call graph.

4. **Be verifiable and auditable**  
   - Open-source codebase.  
   - Clear, documented protocol (see `GHOSTNET_WIRE_SPEC_v1.md`).  
   - No hidden “lawful intercept” codepaths.

---

## 2. Non-goals and realistic limits

GhostNet **does not** aim to:

- hide the fact that a device is online and sending encrypted data,
- defeat a fully compromised endpoint (rooted device with active malware),
- stop a powerful adversary from correlating:
  - IP addresses,
  - timing information,
  - and high-level traffic patterns.

GhostNet is **not**:

- an anonymity network like Tor,
- a SIM/eSIM anonymisation service,
- a “perfect deniability” system.

It is a **secure calling layer**, not a full anonymity stack.

---

## 3. Threat model (simplified)

### 3.1 Adversary types

1. **Passive network observer**
   - Can see packet timing, sizes, IPs.
   - Cannot break modern AEAD (XChaCha20-Poly1305 or AES-GCM).
   - Goal: learn who calls whom, when and how often.

2. **Malicious or compromised server operator**
   - Can read all data that hits the servers.  
   - Can modify or drop messages.  
   - Cannot decrypt end-to-end encrypted payloads.  
   - Cannot forge valid end-to-end encrypted packets
     without client keys.

3. **On-device attacker**
   - Has control of the OS or privileged malware on the device.  
   - Can access microphone, screen, filesystem, keys in memory.

GhostNet can do little against a **fully compromised device**.
This is out of scope; mitigation is via hardened OS (GrapheneOS-like).

---

## 4. Trust boundaries

Key trust boundaries:

- **Endpoint boundary**
  - Secret keys live only on devices.  
  - If the endpoint is compromised, all guarantees fail.

- **Server boundary**
  - Servers are assumed “honest-but-curious” in the baseline model.  
  - Design assumes they might be compromised later.  
  - Therefore:
    - no long-term content storage,
    - no long-term call logs,
    - limited, short-lived infra logs.

- **Network boundary**
  - The internet between clients and servers is untrusted.  
  - All sensitive data must be encrypted and authenticated.

---

## 5. Crypto & protocol notes (high level)

- Wire encryption:
  - AEAD cipher (e.g. XChaCha20-Poly1305 or AES-GCM) over:
    - frame header (where possible),
    - frame body (audio/control).
- Keys:
  - derived from a session-level key exchange (to be specified),
  - no reuse across long time spans or many calls,
  - no server-side key escrow.

- Frames:
  - see `GHOSTNET_WIRE_SPEC_v1.md` for frame types and versioning,
  - security-critical changes MUST be documented there and in
    dedicated `DECISION_*.md` files.

---

## 6. Implementation guidance for developers

When touching code that impacts security:

- Do **not**:
  - add new logging of keys, nonces, plaintext,
  - add “temporary” debug endpoints that bypass encryption,
  - weaken or skip authentication checks for “testing”.

- Do:
  - keep crypto code in `core_crypto/` and native/FFI layers,
  - document changes in `docs/tech/` (CRYPTO_* or DECISION_*),
  - cross-check with legal/privacy docs when adding data collection.

If in doubt, assume:

> "If this log line or feature leaked publicly,  
> would it contradict our security model or legal story?"

If the answer is “yes” or “maybe”, do not ship it.

---

## 7. Status and TODO

This document is a stub and must be updated when:

- the final cipher suite and key schedule are chosen,
- replay protection and authentication details are fixed,
- production logging/monitoring settings are finalised.

Until then it provides:

- a concise threat model,
- clear security goals and non-goals,
- a checklist for developers working on sensitive paths.
