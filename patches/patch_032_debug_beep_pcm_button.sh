#!/bin/bash
set -e

echo "== patch_032: change debug button to inject audible sine PCM =="

python3 - <<'PY'
from pathlib import Path

path = Path("client_android/app/src/main/java/com/securecall/app/MainActivity.java")
txt = path.read_text()

old = """// DEBUG: inject fake inbound PCM into MediaRouterInboundStub
private void setupInjectFakePcmButton() {
    android.widget.Button b = new android.widget.Button(this);
    b.setText("Inject FAKE PCM");
    b.setOnClickListener(v -> {
        // 320 bytes of zero PCM (stub) – just to exercise the pipeline
        byte[] pcm = new byte[320];
        com.securecall.app.ghostnet.media.MediaRouterInboundStub.INSTANCE.handleDecodedPcm(pcm);
    });
    addDebugButton(b);
}
"""

new = """// DEBUG: inject audible sine-wave PCM into MediaRouterInboundStub
private void setupInjectFakePcmButton() {
    android.widget.Button b = new android.widget.Button(this);
    b.setText("Inject BEEP PCM");
    b.setOnClickListener(v -> {
        final int sampleRate = 48000;
        final int durationMs = 250;
        final double freqHz = 440.0;

        int numSamples = sampleRate * durationMs / 1000;
        byte[] pcm = new byte[numSamples * 2]; // 16-bit mono

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / (double) sampleRate;
            double sample = Math.sin(2.0 * Math.PI * freqHz * t);
            short s = (short) (sample * 32767.0);

            int idx = i * 2;
            pcm[idx] = (byte) (s & 0xFF);
            pcm[idx + 1] = (byte) ((s >> 8) & 0xFF);
        }

        com.securecall.app.ghostnet.media.MediaRouterInboundStub.INSTANCE.handleDecodedPcm(pcm);
    });
    addDebugButton(b);
}
"""

if old not in txt:
    raise SystemExit("old setupInjectFakePcmButton block not found")

path.write_text(txt.replace(old, new))
PY

echo "[OK] Updated setupInjectFakePcmButton() to generate audible sine PCM"
echo "== patch_032 done =="
