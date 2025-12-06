#!/bin/bash
set -e

echo "== patch_043: add link to ANDROID_INBOUND_AUDIO_DEBUG in client_android/README.md =="

README="client_android/README.md"

# Falls die Datei noch nicht existiert, minimalen Header anlegen
if [ ! -f "$README" ]; then
  echo "[INFO] $README does not exist, creating new file"
  cat <<'RMD' > "$README"
# Stealth Android Client

This directory contains the Android client project for the Stealth / SecureCall app.
RMD
fi

# Prüfen, ob der Abschnitt schon existiert
if grep -q "Android inbound audio debug" "$README"; then
  echo "[INFO] README already mentions Android inbound audio debug, nothing to do."
  echo "== patch_043 done =="
  exit 0
fi

cat <<'RMD' >> "$README"

## Android inbound audio debug

The Android inbound audio beep test and the end-to-end media pipeline
(MediaRouterInboundStub → AudioPlaybackStub → AudioTrack) are documented in:

- \`docs/tech/ANDROID_INBOUND_AUDIO_DEBUG.md\`

If your decoder produces 16-bit mono 48 kHz PCM, you can feed it directly into:

```java
byte[] pcm = ...; // decoded audio
com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(pcm);
On the maintainer's test device (RF8N313QMFL) the audio will be played immediately,
and logcat will show the MEDIA_ROUTER_INBOUND / AUDIO_PLAYBACK_STUB path.
RMD

echo "[OK] Updated $README with Android inbound audio debug link"
echo "== patch_043 done =="
