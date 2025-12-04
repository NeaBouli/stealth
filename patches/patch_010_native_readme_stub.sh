#!/bin/bash
set -e

echo "== patch_010: add native/README stub =="

mkdir -p native

cat <<'DOC' > native/README.md
# native/ – NDK / FFI glue (stub)

This directory is reserved for native code that glues together:

- Android NDK components (C/C++),
- Rust-based FFI artefacts (built via cargo-ndk),
- and any low-level codec or crypto helpers that must live outside the JVM.

Current state (v0.1, scaffolding only):

- No production-ready C/C++ sources yet.
- No stable build pipeline wired into Gradle.
- All JNI/FFI integration is described in the tech memos under `docs/tech/`
  (see PATCH_215_JNI_HOOKS, PATCH_219_OPUS_BINDINGS, PATCH_223_FFI_TESTS).

Until the toolchain is final:

- Do not add heavy native code here without a dedicated patch and memo.
- Keep this directory buildable on a clean macOS/Android Studio setup.
DOC

echo "[OK] Created native/README.md"
echo "== patch_010 done =="
