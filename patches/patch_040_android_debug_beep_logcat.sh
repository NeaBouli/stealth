#!/bin/bash
set -e

echo "== patch_040: add CLI helper for filtered logcat (audio beep) =="

mkdir -p tools

cat <<'SH' > tools/android_debug_beep_logcat.sh
#!/bin/bash
set -e

# Einfaches gefiltertes Logcat für den Audio-Inbound-Pfad
echo "== [android_debug_beep_logcat] showing filtered logcat =="
echo "Filter: MEDIA_ROUTER_INBOUND | AUDIO_PLAYBACK_STUB"
echo "Beende mit:  Ctrl + C"
echo

"$HOME/Library/Android/sdk/platform-tools/adb" logcat | egrep "MEDIA_ROUTER_INBOUND|AUDIO_PLAYBACK_STUB"
SH

chmod +x tools/android_debug_beep_logcat.sh

echo "[OK] Created tools/android_debug_beep_logcat.sh"
echo "== patch_040 done =="
