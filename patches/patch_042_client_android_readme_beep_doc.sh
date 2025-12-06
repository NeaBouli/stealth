#!/bin/bash
set -e

echo "== patch_042: document android_beep_test in client_android/README.md =="

python3 - <<'PY'
from pathlib import Path

path = Path("client_android/README.md")
text = path.read_text() if path.exists() else ""

snippet = """
## Android inbound audio debug

This repo includes a minimal end-to-end inbound audio debug path on Android.

- Target device (current maintainer): `RF8N313QMFL` (real phone, recommended)
- App entry screen shows **Start Call** and **Settings** buttons.
- Tapping **Settings**:
  - generates a short 440 Hz sine beep (250 ms, 48 kHz, 16-bit, mono),
  - calls `com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(pcm)`,
  - which forwards the PCM data to `AudioPlaybackStub` (AudioTrack-based playback).

### One-shot debug script

From the repo root:

```bash
./tools/android_beep_test.sh
This script will:

build the debug APK for client_android,

install it on the attached device via adb install -r,

launch the app via adb shell monkey …,

and start a filtered logcat for:

bash
Code kopieren
MEDIA_ROUTER_INBOUND
AUDIO_PLAYBACK_STUB
Integration hint for Stealth developers
If your GhostNet or decoder implementation produces raw PCM
(16-bit, mono, 48 kHz) on Android, you can route it directly into
the existing media pipeline:

java
Code kopieren
byte[] pcm = ...; // decoded audio
com.securecall.app.ghostnet.media.MediaRouterInboundStub.handleDecodedPcm(pcm);
On the maintainer's test device the audio will be played immediately,
and the logcat output will confirm the data flow through:

MEDIA_ROUTER_INBOUND: handleDecodedPcm()

AUDIO_PLAYBACK_STUB: wrote <N> bytes to AudioTrack
"""

if "Android inbound audio debug" in text:
raise SystemExit("README already contains Android inbound audio debug section")

if text.strip():
text = text.rstrip() + "\n\n" + snippet.strip() + "\n"
else:
text = snippet.strip() + "\n"

path.write_text(text)
PY

echo "[OK] Updated client_android/README.md"
echo "== patch_042 done =="
