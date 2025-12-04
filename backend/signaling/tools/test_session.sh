#!/bin/bash
# BACKEND-18 – Session Registry Test
#
# Testet grundlegende CALL_* Nachrichten:
# - CALL_INVITE
# - CALL_RINGING
# - CALL_ACCEPT
# - CALL_END

WS="ws://localhost:8080/signal"

if ! command -v websocat >/dev/null 2>&1; then
  echo "Fehler: websocat nicht installiert!"
  echo "Installieren via: brew install websocat"
  exit 1
fi

echo "---------------------------------------------------"
echo "TEST: Session Registry CALL_* Events"
echo "---------------------------------------------------"

websocat -t "$WS" <<EOF2
{"type":"CALL_INVITE","to":"peer-123"}
{"type":"CALL_RINGING","sessionId":"test-session-id"}
{"type":"CALL_ACCEPT","sessionId":"test-session-id"}
{"type":"CALL_END","sessionId":"test-session-id"}
EOF2
