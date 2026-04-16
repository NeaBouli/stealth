# SecureCall v1.0.22 — Release Notes

## What's New

### Bug Fixes
- **REGISTERED-gated registration** — client only proceeds after server ACK
- **Close code 4003 stop** — unauthorized client signature rejects cleanly
- **Subscription resync** — inconsistent tier state auto-corrected
- **Custom-ID token validation** — proper JWT verification
- **Subscription verify endpoint** — server-side state check
- **SECUREID_CHANGED hardened** — atomic JSON writes prevent race conditions

### Security Improvements
- **Origin-less WS clients** — native apps without browser Origin header
- **CORS allowlist hardened** — signaling server restricts origins
- **Stripe idempotency** — duplicate webhook calls safe
- **Admin key unified** — single source of truth
- **PII redacted from release logs** — LOGGING_LEVEL tightened
- **HIGH-002/005** — signaling server hardening (details in security audit)

### Verified Devices
- Samsung Galaxy S10 (Premium tier)
- Samsung Galaxy S7 (Free tier)
- Samsung Galaxy Tab S4 (Free tier)

---

## Google Play — Short Release Notes (EN, max 500 chars)

```
Security update: REGISTERED-gated connection flow, hardened signaling server (CORS, Stripe idempotency, atomic writes), PII redacted from logs, subscription state auto-resync. Custom Call ID token validation improved. Verified on S10, S7, Tab S4.
```

## Google Play — Short Release Notes (DE, max 500 chars)

```
Sicherheitsupdate: REGISTERED-gesicherte Verbindung, gehärteter Signaling-Server (CORS, Stripe, atomare Schreibvorgänge), PII aus Logs entfernt, Abo-Status Resync. Custom Call ID Validierung verbessert. Getestet auf S10, S7, Tab S4.
```

---

## APK Signing Certificate
SHA-256: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`

## Build Info
- versionCode: 43
- versionName: 1.0.22
- minSdk: 26 (Android 8.0+)
- targetSdk: 34
- Signing: securecall-release-key.jks
