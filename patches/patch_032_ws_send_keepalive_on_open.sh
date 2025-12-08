#!/bin/bash
set -e

echo "== patch_032: call sendKeepalive() right after onOpen() =="

FILE="client_android/app/src/main/java/com/securecall/app/ghostnet/transport/ws/GhostNetWebSocketClient.java"

# Direkt nach dem onOpen()-Log den Keepalive-Aufruf einfügen
perl -0pi -e 's|Log\.d\(TAG, "onOpen\(\): " \+ response\);|Log.d(TAG, "onOpen(): " + response);\n                sendKeepalive();|' "$FILE"

echo "== patch_032 done =="
