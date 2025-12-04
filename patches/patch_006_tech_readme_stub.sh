#!/bin/bash
set -e

echo "== patch_006: add docs/tech/README.md stub =="

mkdir -p docs/tech

cat <<'DOC' > docs/tech/README.md
# SecureCall / Stealth – Technical Notes (Overview, Stub)

This directory contains technical design notes and patch memos used during
the evolution of GhostNet, media routing, codec integration and FFI work.

Existing documents include (non-exhaustive, filenames only):

- CRYPTO_38_39_OUTBOUND_FRAME_BUILDER.md
- DECISION_216_CODEC_BACKEND_AND_FFI.md
- GHOSTNET_AUDIOPLAYBACK_SKELETON.md
- GHOSTNET_SESSION_LIFECYCLE.md
- PATCH_211_MEDIA_ROUTER_FLOW.md
- PATCH_212_AUDIOTRACK_THREAD.md
- PATCH_213_OPUS_DECODER_SKELETON.md
- PATCH_214_CODEC_INTEGRATION.md
- PATCH_215_JNI_HOOKS.md
- PATCH_218_DECODER_CONTEXT.md
- PATCH_219_OPUS_BINDINGS.md
- PATCH_220_DECODER_CONTEXT_OPUS_FIELD.md
- PATCH_221_REGISTRY_CLEANUP.md
- PATCH_222_LIB_CLEANUP.md
- PATCH_223_FFI_TESTS.md

This README is a stub (v0.1) and can be extended later with links,
summaries and cross-references.
DOC

echo "[OK] Created docs/tech/README.md"
echo "== patch_006 done =="
