# StealthX Platform — Release Process

> **Mandatory SecureCall distribution gate:** Before any SecureCall release, read
> [`DISTRIBUTION_MATRIX.md`](DISTRIBUTION_MATRIX.md). Google Play receives only the VPN-service-free
> Free AAB. Direct Premium is a separate package/APK and is the only edition with optional built-in
> WireGuard. These artifacts are never interchangeable.

## The Three Stages (MANDATORY for all StealthX Apps)

### Stage 1 — Internal Alpha (Developer-internal)
- Developers and direct testers only
- Google Play: Internal Test Track
- Goal: Stabilize new features
- Prerequisite for Stage 2: All unit tests green,
  no critical bug in LOGBUCH.md

### Stage 2 — Closed Beta (Pre-Live)
- Closed tester group (max. 100 testers)
- Google Play: Closed Test Track (Alpha)
- Goal: Real-world feedback, final bugs
- Prerequisite for Stage 3: No P0/P1 bugs,
  stable for at least 7 days

### Stage 3 — Production Release (Live)
- Public on Google Play
- Prerequisite: External security audit completed,
  release checklist fully done

## CRITICAL RULE: Secure Stable Builds

BEFORE developing a new feature:
1. Tag the current stable state: git tag stable-DATE
2. Push the tag: git push origin stable-DATE
3. Never touch stable functionality without a separate branch
4. Feature branches: feature/NAME — never directly on main

## Asset-Naming Convention (MANDATORY since v1.0.27)

GitHub Release Assets MUST follow this pattern:
```
securecall-{flavor}-v{version}-vC{code}-{abi}.apk
```

Example v1.0.27 (vC48):
```
securecall-free-v1.0.27-vC48-arm64-v8a.apk
securecall-free-v1.0.27-vC48-armeabi-v7a.apk
securecall-free-v1.0.27-vC48-x86_64.apk
```

**WHY:** `UpdateChecker.kt` uses regex `-vC(\d+)\.apk$` to extract the version code.
Wrong naming = auto-update silently fails (no matching asset found).

## Current Status
- SecureCall: Stage 3 candidate. Public Google Play listing is live for `com.securecall.app.free`; release-clearance still requires the 1.0.45 S10 -> Tab S4 Free incoming-accept retest, S7 network resolution or documented external blocker, and final device-matrix sign-off.
- SecureChat: Stage 1/2 boundary. Public APK/AAB artifacts exist and the Android app is wallet/IFR-free, but full device/function QA and fresh release artifacts are still required before Play production.
- Chameleon: Stage 1/2 boundary. Public APK/AAB artifacts exist and the Android app is wallet/IFR-free, but full device/function QA and fresh release artifacts are still required before Play production.
- Suite sales: launch-gated until Stripe runtime key rotation, webhook/test checkout, activation email, and AADE/myDATA/e-timologio transfer are verified through the private VLABS finance control center.
