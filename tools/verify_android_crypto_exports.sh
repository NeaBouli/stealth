#!/usr/bin/env bash
# Fail-closed export guard for the prebuilt Rust crypto engine
# (client_android/app/src/main/jniLibs/<abi>/libsecurecall_core_crypto.so).
#
# Every supported ABI must export all JNI entry points that
# com.securecall.crypto.CoreCrypto declares (legacy + STX-24 media v3), and the
# 64-bit libraries must keep 16 KB LOAD alignment (same value as the Opus JNI
# library in src/main/cpp/CMakeLists.txt). Uses the NDK's llvm-nm/llvm-readelf.
# There is no skip path: missing NDK, tool, library or symbol exits non-zero.
#
# Usage: verify_android_crypto_exports.sh [--ndk-version <ver>] [--jnilibs <dir>]
# NDK lookup: $ANDROID_NDK_HOME, else <sdk>/ndk/<ver> where <sdk> is
# $ANDROID_HOME, $ANDROID_SDK_ROOT or sdk.dir from client_android/local.properties.

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
JNILIBS="$PROJECT_ROOT/client_android/app/src/main/jniLibs"
NDK_VERSION=""

while [ $# -gt 0 ]; do
    case "$1" in
        --ndk-version) NDK_VERSION="${2:-}"; shift 2 ;;
        --jnilibs) JNILIBS="${2:-}"; shift 2 ;;
        *) echo "ERROR: unknown argument: $1" >&2; exit 2 ;;
    esac
done

LIB_NAME="libsecurecall_core_crypto.so"
ABIS=(arm64-v8a armeabi-v7a x86_64)
JNI_PREFIX="Java_com_securecall_crypto_CoreCrypto_"
REQUIRED_METHODS=(encrypt decrypt deriveSessionKey generateKeyPair selfTest mediaSeal mediaOpen)
MIN_ALIGN_64BIT=16384

fail() { echo "ERROR: $*" >&2; exit 1; }

resolve_ndk() {
    if [ -n "${ANDROID_NDK_HOME:-}" ]; then
        [ -d "$ANDROID_NDK_HOME" ] || fail "ANDROID_NDK_HOME does not exist: $ANDROID_NDK_HOME"
        echo "$ANDROID_NDK_HOME"
        return
    fi
    [ -n "$NDK_VERSION" ] || fail "ANDROID_NDK_HOME not set and no --ndk-version given"
    local sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
    local props="$PROJECT_ROOT/client_android/local.properties"
    if [ -z "$sdk" ] && [ -f "$props" ]; then
        sdk="$(sed -n 's/^sdk\.dir=//p' "$props" | tail -1)"
    fi
    [ -n "$sdk" ] || fail "Android SDK not found (ANDROID_HOME, ANDROID_SDK_ROOT, local.properties sdk.dir)"
    [ -d "$sdk/ndk/$NDK_VERSION" ] || fail "NDK $NDK_VERSION not installed under $sdk/ndk"
    echo "$sdk/ndk/$NDK_VERSION"
}

NDK="$(resolve_ndk)"
TOOL_BIN=""
for candidate in "$NDK"/toolchains/llvm/prebuilt/*/bin; do
    if [ -x "$candidate/llvm-nm" ] && [ -x "$candidate/llvm-readelf" ]; then
        TOOL_BIN="$candidate"
        break
    fi
done
[ -n "$TOOL_BIN" ] || fail "llvm-nm/llvm-readelf not found in NDK: $NDK"

errors=0
for abi in "${ABIS[@]}"; do
    lib="$JNILIBS/$abi/$LIB_NAME"
    if [ ! -f "$lib" ]; then
        echo "ERROR: $abi: missing $lib" >&2
        errors=$((errors + 1))
        continue
    fi
    if ! exports="$("$TOOL_BIN/llvm-nm" -D --defined-only --format=just-symbols "$lib")"; then
        echo "ERROR: $abi: llvm-nm failed for $lib" >&2
        errors=$((errors + 1))
        continue
    fi
    for method in "${REQUIRED_METHODS[@]}"; do
        if ! grep -qx "${JNI_PREFIX}${method}" <<<"$exports"; then
            echo "ERROR: $abi: missing export ${JNI_PREFIX}${method}" >&2
            errors=$((errors + 1))
        fi
    done
    case "$abi" in
        arm64-v8a|x86_64)
            if ! aligns="$("$TOOL_BIN/llvm-readelf" -lW "$lib" | awk '$1 == "LOAD" { print $NF }')"; then
                echo "ERROR: $abi: llvm-readelf failed for $lib" >&2
                errors=$((errors + 1))
                continue
            fi
            if [ -z "$aligns" ]; then
                echo "ERROR: $abi: no LOAD segments in $lib" >&2
                errors=$((errors + 1))
            fi
            for align in $aligns; do
                if [ $((align)) -lt $MIN_ALIGN_64BIT ]; then
                    echo "ERROR: $abi: LOAD alignment $align < $MIN_ALIGN_64BIT" >&2
                    errors=$((errors + 1))
                fi
            done
            ;;
    esac
done

if [ "$errors" -ne 0 ]; then
    fail "$errors check(s) failed: $LIB_NAME is stale or incomplete; rebuild with tools/build_android_crypto.sh"
fi
echo "OK: $LIB_NAME exports ${#REQUIRED_METHODS[@]} JNI methods for ${ABIS[*]} (64-bit LOAD align >= $MIN_ALIGN_64BIT)"
