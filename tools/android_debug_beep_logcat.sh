#!/bin/bash
set -e

# Einfaches gefiltertes Logcat für den Audio-Inbound-Pfad
echo "== [android_debug_beep_logcat] showing filtered logcat =="
echo "Filter: MEDIA_ROUTER_INBOUND | AUDIO_PLAYBACK_STUB"
echo "Beende mit:  Ctrl + C"
echo

"$HOME/Library/Android/sdk/platform-tools/adb" logcat | egrep "MEDIA_ROUTER_INBOUND|AUDIO_PLAYBACK_STUB"
