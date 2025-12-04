#!/bin/bash
# BACKEND-20 – Routing Layer Test
#
# Der Server muss laufen (npm run dev).
# Dieses Skript testet die Schritte:
# 1. CALL_INVITE
# 2. CALL_ACCEPT  -> Routing wird erzeugt
# 3. GET /routing/list
# 4. CALL_END     -> Routing wird entfernt

WS="ws://localhost:8080/signal"

if ! command -v websocat >/dev/null 2>&1; then
  echo "Fehler: websocat nicht installiert (brew install websocat)"
  exit 1
fi

echo "-------------------------------------------------------"
echo "TEST: Call Routing Layer"
echo "-------------------------------------------------------"

echo "Sende CALL_INVITE..."
SESSION=$(uuidgen)

websocat -t "$WS" <<EOF2
{"type":"CALL_INVITE","to":"peer-123"}
{"type":"CALL_ACCEPT","sessionId":"$SESSION"}
EOF2

echo
echo "-------------------------------------------------------"
echo "Routing-Tabelle abrufen:"
echo "GET http://localhost:8080/routing/list"
echo "-------------------------------------------------------"

curl -s http://localhost:8080/routing/list | jq .

echo
echo "Sende CALL_END an Session:"
websocat -t "$WS" <<EOF3
{"type":"CALL_END","sessionId":"$SESSION"}
EOF3

echo
echo "-------------------------------------------------------"
echo "Routing-Tabelle erneut abrufen:"
curl -s http://localhost:8080/routing/list | jq .
echo "-------------------------------------------------------"
