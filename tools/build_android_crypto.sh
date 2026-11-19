#!/usr/bin/env bash
# Build libsecurecall_core_crypto.so for Android targets
# Requires: cargo-ndk, Android NDK, rustup targets installed
# Builds from the committed Cargo.lock (--locked), keeps 16 KB LOAD alignment on
# 64-bit ABIs, then runs tools/verify_android_crypto_exports.sh on the output.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_CRYPTO="$PROJECT_ROOT/core_crypto"
JNILIBS="$PROJECT_ROOT/client_android/app/src/main/jniLibs"
NDK_VERSION="27.0.12077973"

# Ensure cargo-ndk is available
if ! command -v cargo-ndk &>/dev/null; then
    echo "ERROR: cargo-ndk not found. Install with: cargo install cargo-ndk"
    exit 1
fi

# Match the application's pinned NDK instead of selecting the newest installation.
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Library/Android/sdk}}"
    export ANDROID_NDK_HOME="$SDK/ndk/$NDK_VERSION"
fi
if [ ! -f "$ANDROID_NDK_HOME/source.properties" ] ||
    ! grep -Eq "^Pkg[.]Revision[[:space:]]*=[[:space:]]*${NDK_VERSION//./[.]}[[:space:]]*$" "$ANDROID_NDK_HOME/source.properties"; then
    echo "ERROR: NDK $NDK_VERSION is required" >&2
    exit 1
fi
if [ -n "${RUSTFLAGS:-}${CARGO_ENCODED_RUSTFLAGS:-}" ]; then
    echo "ERROR: global Rust flags override the required per-target page flags; use the pinned build environment" >&2
    exit 1
fi

TARGETS=(
    aarch64-linux-android
    armv7-linux-androideabi
    x86_64-linux-android
)

echo "Building core_crypto for Android..."
echo "  NDK:    $ANDROID_NDK_HOME"
echo "  Output: $JNILIBS"
echo ""

cd "$CORE_CRYPTO"

# Same 16 KB page size as the Opus JNI library (src/main/cpp/CMakeLists.txt).
PAGE_FLAGS="-C link-arg=-Wl,-z,max-page-size=16384 -C link-arg=-Wl,-z,common-page-size=16384"
export CARGO_TARGET_AARCH64_LINUX_ANDROID_RUSTFLAGS="$PAGE_FLAGS"
export CARGO_TARGET_X86_64_LINUX_ANDROID_RUSTFLAGS="$PAGE_FLAGS"

cargo ndk \
    -t aarch64-linux-android \
    -t armv7-linux-androideabi \
    -t x86_64-linux-android \
    -o "$JNILIBS" \
    build --release --locked

echo ""
echo "Build complete. Libraries:"
find "$JNILIBS" -name "*.so" -exec ls -lh {} \;

"$SCRIPT_DIR/verify_android_crypto_exports.sh" --jnilibs "$JNILIBS"

# Non-secret provenance receipt (stdout only): source, lock, toolchain, output hashes.
sha256() { if command -v sha256sum >/dev/null; then sha256sum "$1"; else shasum -a 256 "$1"; fi | cut -d' ' -f1; }
echo ""
echo "Provenance:"
echo "  git HEAD:      $(git -C "$PROJECT_ROOT" rev-parse HEAD 2>/dev/null || echo unknown)"
echo "  src tree:      $(git -C "$PROJECT_ROOT" rev-parse HEAD:core_crypto 2>/dev/null || echo unknown)"
echo "  dirty sources: $(git -C "$PROJECT_ROOT" status --porcelain -- core_crypto | wc -l | tr -d ' ')"
echo "  Cargo.lock:    $(sha256 "$CORE_CRYPTO/Cargo.lock")"
echo "  rustc:         $(rustc -V)"
echo "  cargo-ndk:     $(cargo ndk --version 2>/dev/null | head -1)"
echo "  NDK:           $(basename "$ANDROID_NDK_HOME")"
for abi in arm64-v8a armeabi-v7a x86_64; do
    echo "  $abi: $(sha256 "$JNILIBS/$abi/libsecurecall_core_crypto.so")"
done
