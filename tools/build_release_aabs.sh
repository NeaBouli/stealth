#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — Build Play AAB and direct APKs
# Enforces the permanent Google Play/direct-download distribution boundary.
# ═══════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../client_android" && pwd)"

echo "╔══════════════════════════════════════════╗"
echo "║   Building SecureCall release set        ║"
echo "╚══════════════════════════════════════════╝"

# ─── Check prerequisites ────────────────────────────────
if [ -z "${SECURECALL_STORE_FILE:-}" ]; then
    echo "ERROR: SECURECALL_STORE_FILE not set"
    echo ""
    echo "Set environment variables:"
    echo "  export SECURECALL_STORE_FILE=/path/to/keystore.jks"
    echo "  export SECURECALL_STORE_PASSWORD=your_password"
    echo "  export SECURECALL_KEY_ALIAS=securecall"
    echo "  export SECURECALL_KEY_PASSWORD=your_key_password"
    exit 1
fi

if [ ! -f "$SECURECALL_STORE_FILE" ]; then
    echo "ERROR: Keystore not found: $SECURECALL_STORE_FILE"
    exit 1
fi

cd "$PROJECT_DIR"

# ─── Clean ───────────────────────────────────────────────
echo "[1/3] Cleaning previous builds..."
./gradlew clean

# ─── Build and enforce distribution boundaries ──────────
echo "[2/3] Building the Free Play AAB and direct APKs..."
./gradlew -Pinternal \
    bundleFreeRelease \
    assembleFreeRelease assembleProRelease assemblePremiumRelease \
    verifyNoVpnServiceSource verifyFreeReleaseVpnPolicy \
    verifyProReleaseVpnPolicy verifyPremiumReleaseVpnRuntime

# ─── List outputs ───────────────────────────────────────
echo "[3/3] Verifying outputs..."
echo ""
echo "═══════════════════════════════════════════"
echo " Google Play AAB:"
echo ""

AAB="app/build/outputs/bundle/freeRelease/app-free-release.aab"
if [ -f "$AAB" ]; then
    SIZE=$(ls -lh "$AAB" | awk '{print $5}')
    echo "  ✓ free: ${AAB} (${SIZE})"
else
    echo "  ✗ free: NOT FOUND"
    exit 1
fi

echo ""
echo " Direct-download APKs:"
for flavor in free pro premium; do
    APK="app/build/outputs/apk/${flavor}/release/app-${flavor}-release.apk"
    if [ -f "$APK" ]; then
        SIZE=$(ls -lh "$APK" | awk '{print $5}')
        echo "  ✓ ${flavor}: ${APK} (${SIZE})"
    else
        echo "  ✗ ${flavor}: NOT FOUND"
    fi
done

echo ""
echo "═══════════════════════════════════════════"
echo " Build complete!"
echo ""
echo " Next steps:"
echo "   1. Install APK on device: adb install <apk>"
echo "   2. Test all QA checklist items"
echo "   3. Upload only the Free AAB to Play Console"
echo "   4. Publish the APKs only through the direct-download release"
echo "═══════════════════════════════════════════"
