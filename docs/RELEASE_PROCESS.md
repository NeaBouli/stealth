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
- Public on Google Play / F-Droid
- Prerequisite: External security audit completed,
  release checklist fully done

## CRITICAL RULE: Secure Stable Builds

BEFORE developing a new feature:
1. Tag the current stable state: git tag stable-DATE
2. Push the tag: git push origin stable-DATE
3. Never touch stable functionality without a separate branch
4. Feature branches: feature/NAME — never directly on main

## Current Status
- SecureCall: Stage 2 (Closed Alpha — v1.0.22, beta-test23, production pending)
- SecureChat: Stage 1 (In development)
- Chameleon: Stage 1 (v0.1.0-alpha)
