#!/bin/bash
set -e

echo "== patch_042: add android_beep_test helper script =="

# Sicherstellen, dass tools/ existiert
mkdir -p tools

cat <<'SH' > tools/android_beep_test.sh
#!/bin/bash
set -e

# Root vom Repo ermitteln (Script liegt in tools/)
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT/client_android"

echo "== android_beep_test: assembleDebug =="
gradle :app:assembleDebug

# ADB Pfad bestimmen
if [ -n "$ANDROID_HOME" ]; then
  ADB_BIN="$ANDROID_HOME/platform-tools/adb"
else
  # Fallback für Georges Setup
  ADB_BIN="$HOME/Library/Android/sdk/platform-tools/adb"
fi

if [ ! -x "$ADB_BIN" ]; then
  echo "ERROR: adb not found at $ADB_BIN"
  exit 1
fi

APK_PATH="app/build/outputs/apk/debug/app-debug.apk"

if [ ! -f "$APK_PATH" ]; then
  echo "ERROR: APK not found at $APK_PATH"
  exit 1
fi

echo "== android_beep_test: install APK =="
"$ADB_BIN" install -r "$APK_PATH"

echo "== android_beep_test: launch app via monkey =="
"$ADB_BIN" shell monkey -p com.securecall.app -c android.intent.category.LAUNCHER 1

echo "== android_beep_test: logcat (MEDIA_ROUTER_INBOUND | AUDIO_PLAYBACK_STUB) =="
echo "Press Ctrl+C to stop logcat."
"$ADB_BIN" logcat | egrep "MEDIA_ROUTER_INBOUND|AUDIO_PLAYBACK_STUB"
SH

chmod +x tools/android_beep_test.sh

echo "[OK] Created tools/android_beep_test.sh"
echo "== patch_042 done =="
