> **CLASSIFICATION: RESTRICTED** | **DOCUMENT: SC-DEFECT** | **DIVISION: StealthX // SecureCall**

---

# KNOWN DEFICIENCIES REGISTER

---
#### ████ OPEN MEDIUM-SEVERITY SECURITY ITEMS ████
---

These findings from the [Security Audit](Security-Audit.md) are tracked as GitHub Issues:

### Android Client

| Issue | Description | Priority |
|-------|-------------|----------|
| [#101](https://github.com/NeaBouli/stealth/issues/101) | No TLS certificate pinning on WebSocket connection | High |
| [#102](https://github.com/NeaBouli/stealth/issues/102) | Sensitive data logged via Log.d() in crypto layer | High |
| [#103](https://github.com/NeaBouli/stealth/issues/103) | OutgoingFrameQueue unsynchronized LinkedList | Medium |
| [#104](https://github.com/NeaBouli/stealth/issues/104) | TransportFrameQueue singleton without synchronization | Medium |
| [#105](https://github.com/NeaBouli/stealth/issues/105) | GhostNetSession static listener memory leak | Medium |
| [#106](https://github.com/NeaBouli/stealth/issues/106) | System.currentTimeMillis() used as nonce component | Medium |
| [#107](https://github.com/NeaBouli/stealth/issues/107) | Empty catch blocks in listener callbacks | Low |
| [#108](https://github.com/NeaBouli/stealth/issues/108) | GhostControlRouter.isControlFrame() always returns true | Medium |

### Rust Crypto

| Issue | Description | Priority |
|-------|-------------|----------|
| [#201](https://github.com/NeaBouli/stealth/issues/201) | No replay detection at AEAD layer | High |
| [#202](https://github.com/NeaBouli/stealth/issues/202) | generateKeyPair discards private key | Medium |
| [#203](https://github.com/NeaBouli/stealth/issues/203) | HKDF uses None salt | Medium |
| [#204](https://github.com/NeaBouli/stealth/issues/204) | SessionState.close() doesn't zeroize key | Medium |
| [#205](https://github.com/NeaBouli/stealth/issues/205) | SessionKey.bytes is pub (bypasses zeroization) | Medium |

### Backend Server

| Issue | Description | Priority |
|-------|-------------|----------|
| [#301](https://github.com/NeaBouli/stealth/issues/301) | No client ID length/character validation | Medium |
| [#302](https://github.com/NeaBouli/stealth/issues/302) | No size limit on PKD public key field | Medium |
| [#303](https://github.com/NeaBouli/stealth/issues/303) | User input reflected in error messages | Medium |
| [#304](https://github.com/NeaBouli/stealth/issues/304) | SDP/ICE forwarded without validation | Medium |
| [#305](https://github.com/NeaBouli/stealth/issues/305) | Unknown binary frames echoed back | Low |
| [#306](https://github.com/NeaBouli/stealth/issues/306) | No TLS termination configured on server | High |
| [#307](https://github.com/NeaBouli/stealth/issues/307) | Potential prototype pollution via JSON.parse | Medium |
| [#308](https://github.com/NeaBouli/stealth/issues/308) | O(n) linear scan in forwardBinaryToPeer | Low |
| [#309](https://github.com/NeaBouli/stealth/issues/309) | GHOST_PREPARE handled without auth | Medium |
| [#310](https://github.com/NeaBouli/stealth/issues/310) | No CORS configuration on Express server | Medium |

---
#### ████ FUNCTIONAL LIMITATIONS ████
---

| Limitation | Description | Status |
|------------|-------------|--------|
| Android only | No iOS or desktop client | Planned for v1.0+ |
| No group calls | Only 1:1 voice calls | Planned for v0.3 |
| No messaging | Voice calls only, no text | Planned for v1.0+ |
| 15-min limit (Free) | Free tier has call duration limit | By design |
| 10 contacts (Free) | Free tier has contact limit | By design |
| Cold starts (Railway) | Free hosting may have 5-10s cold starts | Expected on free tier |

---
#### ████ REPORTING NEW ISSUES ████
---

Found a bug? [Open a GitHub Issue](https://github.com/NeaBouli/stealth/issues/new)

---

> DOCUMENT END // CLASSIFICATION: RESTRICTED

[← Return to Operations Center](Home.md)
