#!/bin/bash
set -e

echo "== patch_014: add DEV_GUIDE_STEALTH_MAINDEV3 developer roadmap =="

mkdir -p docs/dev

cat <<'DOC' > docs/dev/DEV_GUIDE_STEALTH_MAINDEV3.md
# Stealth / SecureCall – Developer Roadmap & Working Mode (MainDev3)

Hi,

this is your consolidated handover as the next main developer on the **Stealth / SecureCall** project.

The goal of this document is:
- that you **understand the project end-to-end**,  
- know **what is already fixed**,  
- and have a **clear roadmap** so you don’t need to ask after every small step.

You should ping the architect / previous main dev **only when decisions are truly unclear** (especially around crypto, legal/compliance, or product direction).

---

## 1. What you inherit (stable foundation)

You’re not starting from zero. The repo already contains:

### 1.1 Docs & policy layers

- `docs/legal/LEGAL_POSITIONING.md`  
- `docs/legal/LAW_ENFORCEMENT_FAQ.md`  
- `docs/privacy/LOGGING_POLICY.md`  
- `docs/risk/FEATURE_RISK_REGISTER.md`  
- `docs/brand/MESSAGING_GUIDELINES.md`  
- `docs/dev/DEVELOPER_ONBOARDING.md`  
- `docs/dev/HANDOVER_MAINDEV2_BLOCK001_010.md`  
- `docs/tech/*.md` (CRYPTO_*, DECISION_*, PATCH_21x, ANDROID_*, GHOSTNET_*)  
- `docs/tech/GHOSTNET_FRAME_V1.md` – wire-format spec for FrameV1  
- `docs/tech/GHOSTNET_SESSION_AND_CRYPTO_V1.md` – session & crypto design v1

**Important:**  
These documents define:
- how we position SecureCall (Signal/Proton/GrapheneOS family, NOT SkyECC/Encrochat),
- how little we log,
- which features are “red line” or legal-review-only.

👉 Treat these docs as **contract with the outside world**.  
Don’t rewrite the philosophy; only extend/refine with good reasons.

### 1.2 Backend & transport scaffolding

- `backend/signaling/` – signalling server skeleton (Node.js)
- `backend/ghostnet/` – GhostNet backend stub
- `backend/ghostnet_echo_server.js` – simple echo server

They **run**, log, and are good enough as a playground.

### 1.3 core_crypto (Rust)

- `core_crypto/` with `Cargo.toml`, `README.md`, `src/lib.rs`

This is a **scaffold**:
- Rust will own: crypto, key material, RNG, codec/FFI plumbing.
- Android/Java will only orchestrate sessions and IO.

### 1.4 Android client baseline

- Java-only media path:
  - `MediaRouterInboundStub`  
  - `AudioPlaybackStub`  
- Verified **inbound audio debug path**:
  - 440 Hz beep via **Settings** button
  - Works on real device (RF8N313QMFL)
  - Logged with:
    - `MEDIA_ROUTER_INBOUND`
    - `AUDIO_PLAYBACK_STUB`
- Outbound GhostNet scaffolding (initial):
  - `GhostNetworkSender` (queue + send*FrameV1)
  - `TransportThreadOutbound`
  - Frame parsing / header utils
- Docs:
  - `docs/tech/ANDROID_INBOUND_AUDIO_DEBUG.md`
  - `docs/tech/GHOSTNET_FRAME_V1.md` – wire format spec for FrameV1

### 1.5 Native / NDK outline

- `native/README.md` – placeholder for future NDK/FFI glue

No real native code yet; this is just a parking space.

---

## 2. Repository conventions & working mode

Please stick to this style so history stays clean and other devs can follow you.

### 2.1 Patch-based workflow

All structural work goes through **small patch scripts**:

- Location: `patches/patch_0xx_descriptive_name.sh`
- Pattern:

```bash
# create
cat <<'EOF' > patches/patch_0xx_descriptive_name.sh
#!/bin/bash
set -e
echo "== patch_0xx: short description =="
# ... do file edits via here-docs / rm / mv ...
echo "== patch_0xx done =="
