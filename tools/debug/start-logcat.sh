#!/bin/bash
# SecureCall v1.0.22 — Combined Bug Reproduction Log Session
# Usage: ./tools/debug/start-logcat.sh [SERIAL]
# Default serial: RF8N313QMFL (S10)
# Output: /tmp/securecall-session.log + /tmp/securecall-app-info.txt
#
# Press Ctrl+C to stop capture when reproduction is done.

set -euo pipefail
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"

SERIAL="${1:-RF8N313QMFL}"
LOG_FILE="/tmp/securecall-session.log"
INFO_FILE="/tmp/securecall-app-info.txt"
TIMESTAMP=$(date "+%Y-%m-%d_%H-%M-%S")

echo "=== SecureCall Debug Log Session ==="
echo "Device: $SERIAL"
echo "Output: $LOG_FILE"
echo ""

# 1. Verify ADB
if ! adb -s "$SERIAL" get-state 2>/dev/null | grep -q "device"; then
    echo "ERROR: Device $SERIAL not found or unauthorized."
    echo "Check: USB-Debugging enabled? RSA fingerprint accepted on device?"
    adb devices -l
    exit 1
fi

echo "[OK] Device connected"

# 2. Capture app info
echo "Capturing app info..."
PKG=""
for P in com.securecall.app.premium com.securecall.app.free; do
    if adb -s "$SERIAL" shell pm list packages 2>/dev/null | grep -q "$P"; then
        PKG="$P"
        break
    fi
done

if [ -z "$PKG" ]; then
    echo "WARNING: No SecureCall package found on device"
else
    echo "Package: $PKG"
    adb -s "$SERIAL" shell dumpsys package "$PKG" 2>&1 \
        | grep -E "versionName|versionCode|firstInstall|lastUpdate" \
        > "$INFO_FILE"
    cat "$INFO_FILE"
fi

echo ""
echo "Session timestamp: $TIMESTAMP" >> "$INFO_FILE"
echo "Device serial: $SERIAL" >> "$INFO_FILE"
echo "Package: $PKG" >> "$INFO_FILE"

# 3. Clear all buffers
echo "Clearing log buffers..."
adb -s "$SERIAL" logcat -b main -c 2>/dev/null
adb -s "$SERIAL" logcat -b crash -c 2>/dev/null
adb -s "$SERIAL" logcat -b events -c 2>/dev/null

# 4. Tombstone check (best effort, needs root on most devices)
echo "Checking tombstones (best effort)..."
adb -s "$SERIAL" shell ls -la /data/tombstones/ 2>/dev/null || echo "(not accessible without root — expected)"

echo ""
echo "==========================================="
echo "  LOGCAT CAPTURE STARTING"
echo "  Press Ctrl+C when reproduction is done"
echo "==========================================="
echo ""
echo "Tip: Note the TIMESTAMP of each reproduction attempt"
echo "     (e.g. 'Versuch 1, 11:42:30 — Crash')"
echo ""

# 5. Start capture — all buffers, threadtime format
adb -s "$SERIAL" logcat -b all -v threadtime > "$LOG_FILE"

# On Ctrl+C:
echo ""
echo "==========================================="
echo "  CAPTURE STOPPED"
echo "==========================================="
echo "Log: $LOG_FILE ($(wc -l < "$LOG_FILE") lines)"
echo "Info: $INFO_FILE"
echo ""
echo "Quick analysis commands:"
echo "  grep 'AndroidRuntime\|FATAL' $LOG_FILE          # Crashes"
echo "  grep 'WS_SERVICE\|HB\|WEBRTC' $LOG_FILE         # Connection"
echo "  grep 'MEDIA_ROUTER\|AUDIO\|GHOST' $LOG_FILE      # Audio"
echo "  grep 'speaker\|SPEAKER\|setSpeaker' $LOG_FILE    # Speaker bug"
