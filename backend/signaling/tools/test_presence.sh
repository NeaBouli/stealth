#!/bin/bash
# BACKEND-16 Testskript: Presence REST Endpoint
# Aufruf:
#   ./test_presence.sh
#
# Der Server muss bereits laufen (npm run dev)

URL="http://localhost:8080/presence/online"

echo "---------------------------------------------"
echo "TEST: GET /presence/online"
echo "---------------------------------------------"

curl -s "$URL" | jq .
