#!/bin/bash
# BACKEND-17 Flood Test
# Sendet 5 Nachrichten mit großem Abstand → sollte NICHT blockieren.

echo "START TEST: flood.sh (leichter Verkehr)"

WS="ws://localhost:8080/signal"

# Verbindung mit websocat (falls installiert)
if ! command -v websocat >/dev/null 2>&1; then
  echo "Fehler: websocat nicht installiert"
  echo "brew install websocat"
  exit 1
fi

websocat -t "$WS" <<EOF2
ping1
ping2
ping3
ping4
ping5
EOF2
