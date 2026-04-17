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

## Artifact Hashes (SHA-256)

```
9786c6f402803d9334b0226c32fd560bf7b1186747dc22911649da6b514bed7d  securecall-v1.0.22-free.aab
33e0633111b8680963beb225868f3083d95200649458de198382eb8532a838a9  securecall-v1.0.22-free.apk
ae50e426e889f4771a54a72acd5d0865f8a1e79b588ca68b504316ccd0aeb905  securecall-v1.0.22-fdroid.apk
b6721435b85cbddc0d669c994262add873c92afa07c3d6f1f541657abd2dd7c9  securecall-v1.0.22-premium.apk
```

Verify: `shasum -a 256 releases/v1.0.22/*`

## APK Signing Certificate
SHA-256: `1e0a8eb419540de8545f770e78dcdb93ab1ba8a0713da8999222fc88c3fdb21d`

## Build Info
- versionCode: 43
- versionName: 1.0.22
- minSdk: 26 (Android 8.0+)
- targetSdk: 35
- Signing: securecall-release-key.jks
