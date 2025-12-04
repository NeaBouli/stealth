# Handover – Main Dev 2 – Block 011–025

## 1. Scope

This handover document covers patches:

- 011 – 015: GhostNet wire/inbound/security docs + planning
- 016 – 020: Android inbound stubs + parser bridge + audio/crypto design docs
- 021 – 025: Media inbound routing stubs, debug PCM path, and local Opus workspace cleanup

It should be read after:

- HANDOVER_MAINDEV2_BLOCK001_010.md
- NEXT_STEPS_MAINDEV2.md

## 2. Android – GhostNet inbound & media path

The following pieces now exist and compile:

- Inbound transport thread:
  - `client_android/.../ghostnet/transport/thread/TransportThreadInbound.kt`
  - Polls `GhostNetworkReceiver.pollInboundFrame()` (stub) and logs inbound raw frames.

- Inbound frame parser stub:
  - `client_android/.../ghostnet/frame/FrameParserStub.kt`
  - Parses a minimal header from raw bytes (stub format),
  - For AUDIO frames, forwards the payload to `MediaRouterInboundStub`.

- Frame body dispatch:
  - `FrameBodyParser.kt` + `AudioBodyParser.kt` / `ControlBodyParser.kt` / `KeepAliveBodyParser.kt`
  - Logs and returns type-specific body objects (still stubs).

- Media inbound router + playback:
  - `MediaRouterInboundStub.kt`:
    - Receives decoded PCM,
    - Forwards it to `AudioPlaybackStub.enqueuePcm(...)`.
  - `AudioPlaybackStub.kt`:
    - Maintains a PCM queue and logs playback events (no real AudioTrack yet).

- Debug button in `MainActivity.java`:
  - `setupInjectFakePcmButton()`:
    - Creates a "Inject FAKE PCM" button,
    - Sends 320 bytes of zero PCM into `MediaRouterInboundStub`,
    - End-to-end test of the inbound media chain (without real network / codec).

Outbound path and existing debug buttons (e.g. CALL-INVITE) remain unchanged and compatible.

## 3. Backend – Signaling & GhostNet scaffolding

Earlier patches in this block consolidated the backend side:

- `backend/signaling/`:
  - New modules for:
    - broadcast, call routing, presence, rate limiting, validation,
    - session registry, metrics, cleanup.
  - `server.js` updated to use these modules.
  - `tools/` provides small shell scripts to stress-test signaling behaviour.

- `backend/ghostnet/`:
  - `ghostnet_router.js` and `ghostnet_server_stub.js` added as placeholders
    for the future encrypted media plane.

Nothing in this block introduces breaking protocol changes – everything is scaffolding.

## 4. Technical documentation – wire, security, audio, crypto

These stubs were added under `docs/tech/`:

- `GHOSTNET_WIRE_SPEC_v1.md`
  - Defines the mental model of GhostNet frames and versions.
- `GHOSTNET_INBOUND_FLOW.md`
  - Describes the desired inbound pipeline from network to media router.
- `GHOSTNET_SECURITY_MODEL.md`
  - Threat model, security goals and non-goals for GhostNet.
- `AUDIO_PIPELINE_OVERVIEW.md`
  - High-level audio flow: capture → encode → transport → decode → playback.
- `CRYPTO_DESIGN_V1.md`
  - Overview of planned primitives, responsibilities of `core_crypto`, and
    separation of concerns between Rust, native, and Android layers.

Status: all of these are **stubs**, but they give future devs a single place
to align on design before implementing real crypto and Opus.

## 5. Core crypto & native layer

- `core_crypto/`:
  - Cargo + README + `lib.rs` were aligned with the CRYPTO_DESIGN_V1 narrative.
  - Still no production-grade crypto inside this repo. All sensitive logic
    must be implemented here later, not in random Kotlin or JS files.

- `native/`:
  - `native/README.md` clarifies its role as NDK/FFI glue layer.
  - `native/securecall_opus/` is considered a local development workspace.

## 6. Local Opus workspace – cleanup and .gitignore

A recurring source of irritation was:

- `native/securecall_opus/` being untracked and
- a local-only helper script `patch_024_ignore_local_opus_workspace.sh`.

This is now fixed in patch_025:

- `.gitignore` created (if missing) and extended to include:

  - `native/securecall_opus/`

- Old helper script removed:

  - `patches/patch_024_ignore_local_opus_workspace.sh` no longer exists.

Result:

- Fresh clones and future developers will **not** see Opus workspace noise in `git status`.
- The official way to ignore the directory is now part of the repository history.

## 7. Recommended workflow going forward (Android + GhostNet)

For Main Dev 2 and successors:

1. Keep using small patch scripts under `patches/patch_0xx_*.sh`.
2. For Android GhostNet work:
   - extend stubs in `GhostNetworkSender`, `GhostNetworkReceiver`,
     `TransportThreadOutbound/Inbound`, and `FrameParserStub`,
   - but always keep them in sync with `GHOSTNET_WIRE_SPEC_v1.md`
     and `GHOSTNET_INBOUND_FLOW.md`.
3. For audio:
   - replace `AudioPlaybackStub` with a real `AudioTrack`-based implementation,
   - keep `MediaRouterInboundStub` as the central ingress point for PCM.
4. For crypto:
   - all cryptography must live in `core_crypto/` and `native/` glue,
   - allow Android and Node.js to treat encrypted frames as opaque blobs.
5. Before larger changes:
   - update or add a short memo under `docs/tech/` (CRYPTO_*, DECISION_*, GHOSTNET_*),
   - then implement matching code in small, reviewable patches.

## 8. Summary for the predecessor / architect

- The original mental model (separation of concerns, legal/privacy story,
  cryptography only in core_crypto, no "magic" debug backdoors) is preserved.
- Block 011–025 turns that model into:
  - concrete Android inbound scaffolding,
  - a minimal media-router bridge,
  - clear wire, audio, security, and crypto design docs,
  - and a cleaner repository state without local-only Opus clutter.

New developers can now:

- clone the repo,
- read `DEVELOPER_ONBOARDING.md` + both handover docs,
- skim the tech docs under `docs/tech/`,
- and continue from a consistent, documented baseline without guessing.
