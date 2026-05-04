# StealthX Platform — Release Process

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
- SecureCall: Stage 2 (Closed Alpha — v1.0.27, vC49 on Play Console, production pending)
- SecureChat: Stage 1 (In development)
- Chameleon: Stage 1 (v0.1.0-alpha)
