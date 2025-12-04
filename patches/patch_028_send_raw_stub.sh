#!/bin/bash
set -e

echo "== patch_028: make sendRawNetworkFrame a pure stub =="

python3 <<'PY'
from pathlib import Path

path = Path("client_android/app/src/main/java/com/securecall/app/ghostnet/transport/net/GhostNetworkSender.kt")
txt = path.read_text()

old = """// CRYPTO-40: tatsächliches Senden über Transport
fun sendRawNetworkFrame(data: ByteArray) {
    try {
        network.send(data)   // hängt von deiner implementierten Netzwerk-Klasse ab
        android.util.Log.d("OUTBOUND", "sent ${'$'}{data.size} bytes")
    } catch (t: Throwable) {
        android.util.Log.e("OUTBOUND", "sendRawNetworkFrame(): failed", t)
    }
}
"""

new = """// CRYPTO-40: tatsächliches Senden über Transport (stub)
// For now we only log; real network wiring will be added later.
fun sendRawNetworkFrame(data: ByteArray) {
    try {
        android.util.Log.d("OUTBOUND", "stub sendRawNetworkFrame(): ${'$'}{data.size} bytes (no real network yet)")
    } catch (t: Throwable) {
        android.util.Log.e("OUTBOUND", "sendRawNetworkFrame(): failed", t)
    }
}
"""

if old not in txt:
    raise SystemExit("Pattern for sendRawNetworkFrame not found in GhostNetworkSender.kt")

path.write_text(txt.replace(old, new))
PY

echo "[OK] Updated sendRawNetworkFrame stub in GhostNetworkSender.kt"
echo "== patch_028 done =="
