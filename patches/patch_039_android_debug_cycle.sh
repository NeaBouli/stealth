#!/bin/bash
set -e

echo "== patch_039: add CLI helper for Android debug beep cycle =="

# Stelle sicher, dass tools/ existiert
mkdir -p tools

cat <<'SH' > tools/android_debug_beep_cycle.sh
#!/bin/bash
set -e

# Root des Stealth-Repos ermitteln (dieses Script liegt in tools/)
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR/client_android"

echo "== [android_debug_beep_cycle] build debug APK =="
gradle :app:assembleDebug

echo "== [android_debug_beep_cycle] install debug APK on connected device =="
"$HOME/Library/Android/sdk/platform-tools/adb" install -r app/build/outputs/apk/debug/app-debug.apk

echo "== [android_debug_beep_cycle] launch app via monkey =="
"$HOME/Library/Android/sdk/platform-tools/adb" shell monkey -p com.securecall.app -c android.intent.category.LAUNCHER 1

echo "== [android_debug_beep_cycle] done =="
echo "Hinweis: Für Logs kannst du z.B. aus einem zweiten Terminal:"
echo "  adb logcat | egrep \"MEDIA_ROUTER_INBOUND|AUDIO_PLAYBACK_STUB\""
SH

chmod +x tools/android_debug_beep_cycle.sh

echo "[OK] Created tools/android_debug_beep_cycle.sh"
echo "== patch_039 done =="
