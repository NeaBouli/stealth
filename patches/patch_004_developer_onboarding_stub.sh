#!/bin/bash
set -e

echo "== patch_004: add developer onboarding stub =="

mkdir -p docs/dev

cat <<'DOC' > docs/dev/DEVELOPER_ONBOARDING.md
# SecureCall / Stealth – Developer Onboarding (Stub)

This is a short onboarding stub for new developers.

- Goal: end-to-end encrypted, low-latency voice calls.
- Main components:
  - Android client: `client_android/`
  - Signaling server: `backend/signaling/`
  - Crypto crate: `core_crypto/`
- Documentation:
  - Technical notes: `docs/tech/`
  - Legal & compliance: `docs/legal/`
  - Privacy & logging: `docs/privacy/`
  - Brand & messaging: `docs/brand/`

This document is a stub (v0.1) and can be extended in future patches.
DOC

echo "[OK] Created docs/dev/DEVELOPER_ONBOARDING.md"
echo "== patch_004 done =="
