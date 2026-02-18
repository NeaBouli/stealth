#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — Build All Release AABs
# Produces Play Store-ready Android App Bundles
# ═══════════════════════════════════════════════════════════
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../client_android" && pwd)"

echo "╔══════════════════════════════════════════╗"
echo "║   Building Release AABs                  ║"
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
echo "[1/5] Cleaning previous builds..."
./gradlew clean

# ─── Build AABs ─────────────────────────────────────────
echo "[2/5] Building FREE Release AAB..."
./gradlew bundleFreeRelease

echo "[3/5] Building PRO Release AAB..."
./gradlew bundleProRelease

echo "[4/5] Building PREMIUM Release AAB..."
./gradlew bundlePremiumRelease

# ─── List outputs ───────────────────────────────────────
echo "[5/5] Verifying outputs..."
echo ""
echo "═══════════════════════════════════════════"
echo " Release AABs:"
echo ""

for flavor in free pro premium; do
    AAB="app/build/outputs/bundle/${flavor}Release/app-${flavor}-release.aab"
    if [ -f "$AAB" ]; then
        SIZE=$(ls -lh "$AAB" | awk '{print $5}')
        echo "  ✓ ${flavor}: ${AAB} (${SIZE})"
    else
        echo "  ✗ ${flavor}: NOT FOUND"
    fi
done

# ─── Also build APKs for testing ────────────────────────
echo ""
echo " Building APKs for testing..."
./gradlew assembleFreeRelease assembleProRelease assemblePremiumRelease

echo ""
echo " Release APKs:"
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
echo "   3. Upload AABs to Play Console (Internal Testing)"
echo "═══════════════════════════════════════════"
