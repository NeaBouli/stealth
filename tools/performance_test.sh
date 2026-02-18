#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — Performance Testing Script
# Tests app performance metrics on a connected device
# ═══════════════════════════════════════════════════════════
set -euo pipefail

PACKAGE="com.securecall.app.free"
OUTPUT_DIR="tools/perf_results"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')

echo "╔══════════════════════════════════════════╗"
echo "║   SecureCall Performance Test            ║"
echo "╚══════════════════════════════════════════╝"

# ─── Check device ───────────────────────────────────────
if ! adb devices | grep -q "device$"; then
    echo "ERROR: No Android device connected"
    echo "Connect a device or start an emulator"
    exit 1
fi

mkdir -p "$OUTPUT_DIR"

DEVICE=$(adb shell getprop ro.product.model | tr -d '\r')
ANDROID=$(adb shell getprop ro.build.version.release | tr -d '\r')
echo "Device: $DEVICE (Android $ANDROID)"
echo ""

# ─── 1. APK Size ────────────────────────────────────────
echo "[1/6] Checking APK size..."
APK_PATH="client_android/app/build/outputs/apk/free/release/app-free-release.apk"
if [ -f "$APK_PATH" ]; then
    APK_SIZE=$(ls -lh "$APK_PATH" | awk '{print $5}')
    echo "  APK size: $APK_SIZE"
else
    echo "  APK not found — build first"
fi

# ─── 2. Install & Cold Start ────────────────────────────
echo "[2/6] Testing cold start time..."
adb shell am force-stop "$PACKAGE" 2>/dev/null || true
sleep 1

START_TIME=$(date +%s%N)
adb shell am start -W -n "$PACKAGE/.MainActivity" 2>/dev/null | tee "$OUTPUT_DIR/startup_$TIMESTAMP.txt"
END_TIME=$(date +%s%N)

STARTUP_MS=$(( (END_TIME - START_TIME) / 1000000 ))
echo "  Cold start: ~${STARTUP_MS}ms"

sleep 3

# ─── 3. Memory Usage (Idle) ─────────────────────────────
echo "[3/6] Measuring idle memory usage..."
adb shell dumpsys meminfo "$PACKAGE" > "$OUTPUT_DIR/meminfo_idle_$TIMESTAMP.txt"

TOTAL_PSS=$(adb shell dumpsys meminfo "$PACKAGE" | grep "TOTAL PSS" | awk '{print $3}' | head -1)
if [ -n "$TOTAL_PSS" ]; then
    TOTAL_MB=$((TOTAL_PSS / 1024))
    echo "  Idle memory (TOTAL PSS): ${TOTAL_MB} MB"
else
    echo "  Could not read memory info"
fi

# ─── 4. Battery Snapshot ────────────────────────────────
echo "[4/6] Taking battery snapshot..."
adb shell dumpsys battery > "$OUTPUT_DIR/battery_$TIMESTAMP.txt"
BATTERY=$(adb shell dumpsys battery | grep "level:" | awk '{print $2}')
echo "  Current battery: ${BATTERY}%"

# ─── 5. CPU Info ────────────────────────────────────────
echo "[5/6] Checking CPU usage..."
adb shell top -n 1 -b | grep "$PACKAGE" > "$OUTPUT_DIR/cpu_$TIMESTAMP.txt" 2>/dev/null || true
CPU=$(adb shell top -n 1 -b 2>/dev/null | grep "$PACKAGE" | awk '{print $9}' | head -1)
echo "  CPU usage: ${CPU:-N/A}%"

# ─── 6. Network Info ────────────────────────────────────
echo "[6/6] Checking network stats..."
adb shell cat /proc/net/dev > "$OUTPUT_DIR/network_$TIMESTAMP.txt" 2>/dev/null || true

echo ""
echo "═══════════════════════════════════════════"
echo " Performance Test Results"
echo ""
echo "  Device:      $DEVICE (Android $ANDROID)"
echo "  APK size:    ${APK_SIZE:-N/A}"
echo "  Cold start:  ~${STARTUP_MS}ms"
echo "  Idle RAM:    ${TOTAL_MB:-N/A} MB"
echo "  Battery:     ${BATTERY:-N/A}%"
echo "  CPU (idle):  ${CPU:-N/A}%"
echo ""
echo " Detailed results: $OUTPUT_DIR/"
echo ""
echo " Targets:"
echo "   Cold start  < 3000ms"
echo "   Idle RAM    < 150 MB"
echo "   APK size    < 15 MB"
echo "═══════════════════════════════════════════"
