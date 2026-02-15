#!/usr/bin/env bash
# Build libsecurecall_core_crypto.so for Android targets
# Requires: cargo-ndk, Android NDK, rustup targets installed

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CORE_CRYPTO="$PROJECT_ROOT/core_crypto"
JNILIBS="$PROJECT_ROOT/client_android/app/src/main/jniLibs"

# Ensure cargo-ndk is available
if ! command -v cargo-ndk &>/dev/null; then
    echo "ERROR: cargo-ndk not found. Install with: cargo install cargo-ndk"
    exit 1
fi

# Auto-detect NDK if not set
if [ -z "${ANDROID_NDK_HOME:-}" ]; then
    NDK_BASE="$HOME/Library/Android/sdk/ndk"
    if [ -d "$NDK_BASE" ]; then
        ANDROID_NDK_HOME="$(ls -d "$NDK_BASE"/*/ 2>/dev/null | sort -V | tail -1)"
        ANDROID_NDK_HOME="${ANDROID_NDK_HOME%/}"
        export ANDROID_NDK_HOME
        echo "Auto-detected NDK: $ANDROID_NDK_HOME"
    else
        echo "ERROR: ANDROID_NDK_HOME not set and no NDK found"
        exit 1
    fi
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

cargo ndk \
    -t aarch64-linux-android \
    -t armv7-linux-androideabi \
    -t x86_64-linux-android \
    -o "$JNILIBS" \
    build --release

echo ""
echo "Build complete. Libraries:"
find "$JNILIBS" -name "*.so" -exec ls -lh {} \;
