#!/bin/bash
# BACKEND-17 Burst Test
# Sendet 50 Nachrichten sehr schnell → sollte vom Server getrennt werden.

echo "START TEST: burst.sh (hohe Last, Rate-Limit wird provoziert)"

WS="ws://localhost:8080/signal"

if ! command -v websocat >/dev/null 2>&1; then
  echo "Fehler: websocat nicht installiert"
  echo "brew install websocat"
  exit 1
fi

# 50 Nachrichten ohne Pause
(
  for i in {1..50}; do
     echo "spam-$i"
  done
) | websocat -t "$WS"
