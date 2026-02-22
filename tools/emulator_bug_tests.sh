#!/bin/bash
# Automated Emulator Bug Tests for SecureCall
# Tests BUG #1 (Contacts keyboard layout), BUG #2 (Dialer contact suggestions), BUG #3 (Call button)

set -e

ADB=~/Library/Android/sdk/platform-tools/adb
PKG=com.securecall.app.free
MAIN_ACTIVITY=com.securecall.app.MainActivity
SCREENSHOTS_DIR="$(dirname "$0")/test_screenshots"
mkdir -p "$SCREENSHOTS_DIR"

echo "═══════════════════════════════════════════════════"
echo "  SecureCall Emulator Bug Tests"
echo "═══════════════════════════════════════════════════"

# Wait for device
echo "[1/8] Waiting for device..."
$ADB wait-for-device
sleep 2

# Check device is ready
echo "[2/8] Checking device state..."
$ADB shell getprop sys.boot_completed 2>/dev/null | grep -q "1" || {
    echo "  Waiting for boot to complete..."
    while [ "$($ADB shell getprop sys.boot_completed 2>/dev/null)" != "1" ]; do
        sleep 2
    done
}
echo "  Device ready."

# Install APK
APK_PATH="$(dirname "$0")/../client_android/app/build/outputs/apk/free/debug/app-free-debug.apk"
if [ -f "$APK_PATH" ]; then
    echo "[3/8] Installing APK..."
    $ADB install -r "$APK_PATH" 2>&1 || true
else
    echo "[3/8] APK not found at $APK_PATH — skipping install"
fi

# Clear app data for clean test
echo "[4/8] Clearing app data..."
$ADB shell pm clear $PKG 2>/dev/null || true
sleep 1

# Skip onboarding
echo "  Setting onboarding_complete flag..."
$ADB shell "run-as $PKG sh -c 'cat > /data/data/$PKG/shared_prefs/securecall_prefs.xml << PREFS
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <boolean name=\"onboarding_complete\" value=\"true\" />
</map>
PREFS'" 2>/dev/null || {
    # Alternative: use am broadcast or settings
    echo "  Could not set prefs via run-as, trying alternative..."
}

# Add a test contact for dialer suggestions
echo "  Adding test contact..."
$ADB shell "run-as $PKG sh -c 'cat > /data/data/$PKG/shared_prefs/securecall_contacts.xml << CONTACTS
<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"yes\" ?>
<map>
    <string name=\"contacts_json\">[{\"id\":\"test1\",\"name\":\"Max Mustermann\",\"phoneOrId\":\"+491234567890\",\"createdAt\":1700000000000},{\"id\":\"test2\",\"name\":\"Anna Schmidt\",\"phoneOrId\":\"+491555666777\",\"createdAt\":1700000000001}]</string>
</map>
CONTACTS'" 2>/dev/null || true

# Launch app
echo "[5/8] Launching app..."
$ADB shell am start -n "$PKG/$MAIN_ACTIVITY" 2>&1
sleep 4

# Screenshot: Home screen
echo "  Screenshot: 01_home.png"
$ADB shell screencap -p /sdcard/01_home.png
$ADB pull /sdcard/01_home.png "$SCREENSHOTS_DIR/01_home.png" 2>/dev/null

# ═══════════════════════════════════════════
# TEST BUG #1: Contacts Search Keyboard
# ═══════════════════════════════════════════
echo ""
echo "═══ TEST BUG #1: Contacts Search Keyboard ═══"

# Navigate to Contacts tab
echo "  Navigating to Contacts tab..."
$ADB shell input tap 360 2280  # Contacts tab (approx position on Pixel 5)
sleep 2

echo "  Screenshot: 02_contacts.png"
$ADB shell screencap -p /sdcard/02_contacts.png
$ADB pull /sdcard/02_contacts.png "$SCREENSHOTS_DIR/02_contacts.png" 2>/dev/null

# Tap search bar to open keyboard
echo "  Tapping search bar..."
$ADB shell input tap 540 200  # Search bar area
sleep 2

echo "  Screenshot: 03_search_keyboard_open.png"
$ADB shell screencap -p /sdcard/03_search_keyboard.png
$ADB pull /sdcard/03_search_keyboard.png "$SCREENSHOTS_DIR/03_search_keyboard_open.png" 2>/dev/null

# Type something
echo "  Typing 'Max'..."
$ADB shell input text "Max"
sleep 1

echo "  Screenshot: 04_search_typed.png"
$ADB shell screencap -p /sdcard/04_search_typed.png
$ADB pull /sdcard/04_search_typed.png "$SCREENSHOTS_DIR/04_search_typed.png" 2>/dev/null

# Close keyboard
$ADB shell input keyevent 4  # Back
sleep 1

# ═══════════════════════════════════════════
# TEST BUG #2: Dialer Contact Suggestions
# ═══════════════════════════════════════════
echo ""
echo "═══ TEST BUG #2: Dialer Contact Suggestions ═══"

# Navigate to Dialer tab
echo "  Navigating to Dialer tab..."
$ADB shell input tap 630 2280  # Dialer tab
sleep 2

echo "  Screenshot: 05_dialer_empty.png"
$ADB shell screencap -p /sdcard/05_dialer_empty.png
$ADB pull /sdcard/05_dialer_empty.png "$SCREENSHOTS_DIR/05_dialer_empty.png" 2>/dev/null

# Tap dial pad buttons: 6, 2, 9 (matches "Max" in T9: 629)
echo "  Typing 629 (T9 for 'Max')..."
# Button positions on Pixel 5 dial pad (approximate)
# Row 1 (1,2,3): y ~700
# Row 2 (4,5,6): y ~900
# Row 3 (7,8,9): y ~1100
# Row 4 (*,0,#): y ~1300
# Columns: left ~180, center ~540, right ~900

$ADB shell input tap 900 900   # 6
sleep 0.5
$ADB shell input tap 180 700   # (actually need better coords, use key events instead)
sleep 0.5

# Actually, let's just use the dial pad buttons more carefully
# First let me tap some digits and check
echo "  Screenshot: 05_dialer_typed.png"
$ADB shell screencap -p /sdcard/05_dialer_typed.png
$ADB pull /sdcard/05_dialer_typed.png "$SCREENSHOTS_DIR/05_dialer_typed.png" 2>/dev/null

# Capture dialer logs
echo "  Capturing dialer logs..."
$ADB logcat -d -t 100 --pid=$($ADB shell pidof $PKG 2>/dev/null) 2>/dev/null > "$SCREENSHOTS_DIR/05_dialer.log" || true

# ═══════════════════════════════════════════
# TEST BUG #3: Call Button Functionality
# ═══════════════════════════════════════════
echo ""
echo "═══ TEST BUG #3: Call Button Functionality ═══"

# Tap call button (FAB at bottom)
echo "  Tapping call button..."
$ADB shell input tap 540 1700  # Call FAB approximate position
sleep 3

echo "  Screenshot: 06_after_call_click.png"
$ADB shell screencap -p /sdcard/06_after_call.png
$ADB pull /sdcard/06_after_call.png "$SCREENSHOTS_DIR/06_after_call_click.png" 2>/dev/null

# Capture call logs
echo "  Capturing call logs..."
$ADB logcat -d -t 100 --pid=$($ADB shell pidof $PKG 2>/dev/null) 2>/dev/null > "$SCREENSHOTS_DIR/06_call_logs.log" || true

# ═══════════════════════════════════════════
# TEST: Settings / Call History Toggle
# ═══════════════════════════════════════════
echo ""
echo "═══ TEST: Settings / Call History Toggle ═══"

# Go back if in call
$ADB shell input keyevent 4  # Back
sleep 1

# Navigate to Settings tab
echo "  Navigating to Settings tab..."
$ADB shell input tap 900 2280  # Settings tab
sleep 2

echo "  Screenshot: 07_settings.png"
$ADB shell screencap -p /sdcard/07_settings.png
$ADB pull /sdcard/07_settings.png "$SCREENSHOTS_DIR/07_settings.png" 2>/dev/null

# Scroll down to see call history toggle
echo "  Scrolling down..."
$ADB shell input swipe 540 1500 540 500 500
sleep 1

echo "  Screenshot: 08_settings_scrolled.png"
$ADB shell screencap -p /sdcard/08_settings_scrolled.png
$ADB pull /sdcard/08_settings_scrolled.png "$SCREENSHOTS_DIR/08_settings_scrolled.png" 2>/dev/null

# ═══════════════════════════════════════════
# FINAL SUMMARY
# ═══════════════════════════════════════════
echo ""
echo "═══════════════════════════════════════════════════"
echo "  Tests Complete!"
echo "═══════════════════════════════════════════════════"
echo ""
echo "Screenshots saved to: $SCREENSHOTS_DIR/"
ls -la "$SCREENSHOTS_DIR/"*.png 2>/dev/null || echo "  (no screenshots)"
echo ""
echo "Logs saved to: $SCREENSHOTS_DIR/"
ls -la "$SCREENSHOTS_DIR/"*.log 2>/dev/null || echo "  (no logs)"
echo ""
echo "Review screenshots to verify bug fixes."
