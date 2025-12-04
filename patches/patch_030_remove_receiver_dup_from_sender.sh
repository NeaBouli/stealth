#!/bin/bash
set -e

echo "== patch_030: remove duplicate GhostNetworkReceiver from GhostNetworkSender.kt =="

python3 - <<'PY'
from pathlib import Path

path = Path("client_android/app/src/main/java/com/securecall/app/ghostnet/transport/net/GhostNetworkSender.kt")
txt = path.read_text()

start_marker = "object GhostNetworkReceiver {"
end_marker   = "// CRYPTO-40: Outbound FrameV1"

start = txt.find(start_marker)
if start == -1:
    raise SystemExit("start_marker not found (object GhostNetworkReceiver {)")

end = txt.find(end_marker, start)
if end == -1:
    raise SystemExit("end_marker not found (// CRYPTO-40: Outbound FrameV1)")

# wir wollen alles von vor dem 'object' bis direkt vor das Outbound-Comment erhalten
before = txt[:start]
after  = txt[end:]

path.write_text(before + after)
PY

echo "[OK] Removed duplicate GhostNetworkReceiver object from GhostNetworkSender.kt"
echo "== patch_030 done =="
