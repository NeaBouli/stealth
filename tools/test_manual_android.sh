#!/usr/bin/env bash
# Manual Android Test Helper
# Builds, installs, and launches SecureCall for manual testing
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
CLIENT_DIR="$PROJECT_ROOT/client_android"
APK="$CLIENT_DIR/app/build/outputs/apk/debug/app-debug.apk"
PACKAGE="com.securecall.app"

echo "=== SecureCall Manual Test Helper ==="
echo ""

# Step 1: Build Debug APK
echo "[1/4] Building Debug APK..."
cd "$CLIENT_DIR"
export JAVA_HOME=$(/usr/libexec/java_home -v17 2>/dev/null || echo "$JAVA_HOME")
./gradlew assembleDebug -q

if [ ! -f "$APK" ]; then
    echo "ERROR: APK not found at $APK"
    exit 1
fi
echo "  APK: $APK ($(du -h "$APK" | cut -f1))"

# Step 2: Install
echo ""
echo "[2/4] Installing on device..."
adb install -r "$APK"

# Step 3: Launch
echo ""
echo "[3/4] Launching app..."
adb shell am start -n "$PACKAGE/.MainActivity"

# Step 4: Logcat
echo ""
echo "[4/4] Streaming logcat (Ctrl+C to stop)..."
echo "  Filters: GHOSTNET_WS, CORE_CRYPTO, SESSION_CIPHER, GHOST_SESSION"
echo ""
adb logcat -c
adb logcat | grep -E "(GHOSTNET_WS|CORE_CRYPTO|SESSION_CIPHER|GHOST_SESSION|NONCE_MANAGER|REPLAY_DETECTOR|HKDF_SHA256|CallSessionManager)"
