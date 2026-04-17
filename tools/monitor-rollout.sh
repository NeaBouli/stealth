#!/bin/bash
# v1.0.22 Rollout Monitor — single check cycle
# Usage: ./tools/monitor-rollout.sh
# Runs one check against Railway logs + GitHub Issues, appends to rollout log.

set -euo pipefail

RAILWAY_TOKEN="dc19624a-543b-4e9b-8992-62788e558a8d"
PROJECT_ID="263caa21-e6f6-4075-9470-22427cfcf5f9"
SERVICE_ID="a01bf60b-97d5-479b-a71c-f7c961b24c53"
ENV_ID="5f573fa5-f6f7-44d0-97ce-f734517d3039"
REPO="NeaBouli/stealth"
LOG_FILE="docs/monitoring/v1.0.22-rollout-log.md"

TIMESTAMP=$(date "+%Y-%m-%d %H:%M %Z")
CHECK_NUM=$(($(grep -c "^### " "$LOG_FILE" 2>/dev/null || echo 0) + 1))

echo "=== Check #${CHECK_NUM} at ${TIMESTAMP} ==="

# 1. Get latest deployment ID
DEPLOY_ID=$(curl -s -X POST "https://backboard.railway.app/graphql/v2" \
  -H "Authorization: Bearer ${RAILWAY_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"query { deployments(first: 1, input: { projectId: \\\"${PROJECT_ID}\\\", serviceId: \\\"${SERVICE_ID}\\\", environmentId: \\\"${ENV_ID}\\\" }) { edges { node { id status } } } }\"}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['deployments']['edges'][0]['node']['id'])" 2>/dev/null || echo "UNKNOWN")

DEPLOY_STATUS=$(curl -s -X POST "https://backboard.railway.app/graphql/v2" \
  -H "Authorization: Bearer ${RAILWAY_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"query { deployments(first: 1, input: { projectId: \\\"${PROJECT_ID}\\\", serviceId: \\\"${SERVICE_ID}\\\", environmentId: \\\"${ENV_ID}\\\" }) { edges { node { status } } } }\"}" \
  | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['data']['deployments']['edges'][0]['node']['status'])" 2>/dev/null || echo "UNKNOWN")

echo "Deploy: ${DEPLOY_ID} (${DEPLOY_STATUS})"

# 2. Count REGISTER success vs reject in last 100 log entries
LOGS_RAW=$(curl -s -X POST "https://backboard.railway.app/graphql/v2" \
  -H "Authorization: Bearer ${RAILWAY_TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"query\":\"query { deploymentLogs(deploymentId: \\\"${DEPLOY_ID}\\\", limit: 200) { message } }\"}")

REG_SUCCESS=$(echo "$LOGS_RAW" | python3 -c "
import sys,json
d=json.load(sys.stdin)
msgs=[e['message'] for e in d.get('data',{}).get('deploymentLogs',[])]
print(sum(1 for m in msgs if '[REGISTER]' in m and 'Rejected' not in m and 'Cleared' not in m and 'Superseding' not in m))
" 2>/dev/null || echo "?")

REG_REJECT=$(echo "$LOGS_RAW" | python3 -c "
import sys,json
d=json.load(sys.stdin)
msgs=[e['message'] for e in d.get('data',{}).get('deploymentLogs',[])]
print(sum(1 for m in msgs if 'Rejected' in m))
" 2>/dev/null || echo "?")

FCM_PERSIST=$(echo "$LOGS_RAW" | python3 -c "
import sys,json
d=json.load(sys.stdin)
msgs=[e['message'] for e in d.get('data',{}).get('deploymentLogs',[])]
print(sum(1 for m in msgs if 'Token registered + persisted' in m))
" 2>/dev/null || echo "?")

FCM_ERRORS=$(echo "$LOGS_RAW" | python3 -c "
import sys,json
d=json.load(sys.stdin)
msgs=[e['message'] for e in d.get('data',{}).get('deploymentLogs',[])]
print(sum(1 for m in msgs if 'EACCES' in m or 'FCM] Failed' in m))
" 2>/dev/null || echo "?")

ERRORS_5XX=$(echo "$LOGS_RAW" | python3 -c "
import sys,json
d=json.load(sys.stdin)
msgs=[e['message'] for e in d.get('data',{}).get('deploymentLogs',[])]
print(sum(1 for m in msgs if '500' in m or 'INTERNAL' in m or 'uncaught' in m.lower()))
" 2>/dev/null || echo "?")

echo "REGISTER success: ${REG_SUCCESS}, reject: ${REG_REJECT}"
echo "FCM persisted: ${FCM_PERSIST}, errors: ${FCM_ERRORS}"
echo "5xx/errors: ${ERRORS_5XX}"

# 3. Check GitHub issues
NEW_ISSUES=$(gh issue list --repo "${REPO}" --state open --label "user-report" --json number --created ">$(date -v-1H +%Y-%m-%dT%H:%M:%S 2>/dev/null || date -d '1 hour ago' +%Y-%m-%dT%H:%M:%S 2>/dev/null || echo '2026-04-16')" 2>/dev/null | python3 -c "import sys,json; print(len(json.load(sys.stdin)))" 2>/dev/null || echo "?")

echo "New user-report issues (1h): ${NEW_ISSUES}"

# 4. Determine status
STATUS="GREEN"
if [ "$REG_REJECT" != "?" ] && [ "$REG_REJECT" -gt 5 ] 2>/dev/null; then
  STATUS="YELLOW"
fi
if [ "$FCM_ERRORS" != "?" ] && [ "$FCM_ERRORS" -gt 0 ] 2>/dev/null; then
  STATUS="YELLOW"
fi
if [ "$ERRORS_5XX" != "?" ] && [ "$ERRORS_5XX" -gt 3 ] 2>/dev/null; then
  STATUS="RED"
fi

echo ""
echo "STATUS: ${STATUS}"
echo ""

# 5. Append to log file
cat >> "$LOG_FILE" << ENTRY

### ${TIMESTAMP} — Check #${CHECK_NUM} — ${STATUS}

**Railway Signaling (deploy: ${DEPLOY_ID:0:8}, ${DEPLOY_STATUS}):**
- REGISTER success: ${REG_SUCCESS} / reject: ${REG_REJECT}
- FCM persist: ${FCM_PERSIST} / errors: ${FCM_ERRORS}
- 5xx/uncaught: ${ERRORS_5XX}

**Play Console:** (manual check required)
**User-Reports:** ${NEW_ISSUES} neue Issues (1h)
**Auffälligkeiten:** —
**Aktion:** Keine
ENTRY

echo "Appended to ${LOG_FILE}"
