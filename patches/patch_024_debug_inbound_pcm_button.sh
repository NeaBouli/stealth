#!/bin/bash
set -e

echo "== patch_024: add debug button to inject fake inbound PCM =="

cat <<'JAVA' >> client_android/app/src/main/java/com/securecall/app/MainActivity.java

// DEBUG: inject fake inbound PCM into MediaRouterInboundStub
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
JAVA

echo "[OK] Appended setupInjectFakePcmButton() to MainActivity.java"
echo "== patch_024 done =="
