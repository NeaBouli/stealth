#!/usr/bin/env bash
# StealthX Signaling Watchdog
#
# Deploys as a cron job on the Hetzner server:
#   crontab -e
#   * * * * * /opt/stealthx/signaling/watchdog.sh >> /var/log/stealthx/watchdog.log 2>&1
#
# Checks every 60s:
#   1. PM2 process "signaling" is in state "online"
#   2. HTTP /health endpoint returns 200
# If either fails → pm2 restart signaling

set -euo pipefail

LOG_FILE="/var/log/stealthx/watchdog.log"
HEALTH_URL="https://api.stealthx.tech/health"
PM2_APP="signaling"
MAX_LOG_LINES=10000
TS=$(date '+%Y-%m-%d %H:%M:%S')

mkdir -p "$(dirname "$LOG_FILE")"

# Rotate log if it gets too large
line_count=$(wc -l < "$LOG_FILE" 2>/dev/null || echo 0)
if [ "$line_count" -gt "$MAX_LOG_LINES" ]; then
  tail -n 5000 "$LOG_FILE" > "${LOG_FILE}.tmp" && mv "${LOG_FILE}.tmp" "$LOG_FILE"
fi

pm2_online() {
  pm2 describe "$PM2_APP" 2>/dev/null | grep -q "online"
}

health_ok() {
  curl -sf --max-time 8 "$HEALTH_URL" > /dev/null 2>&1
}

restart_app() {
  echo "[$TS] WATCHDOG RESTART: $PM2_APP" >> "$LOG_FILE"
  pm2 restart "$PM2_APP" >> "$LOG_FILE" 2>&1
  sleep 5
  if pm2_online; then
    echo "[$TS] WATCHDOG: restart succeeded — process online" >> "$LOG_FILE"
  else
    echo "[$TS] WATCHDOG: restart FAILED — process not online after 5s" >> "$LOG_FILE"
  fi
}

if ! pm2_online; then
  echo "[$TS] WATCHDOG: PM2 process not online" >> "$LOG_FILE"
  restart_app
elif ! health_ok; then
  echo "[$TS] WATCHDOG: health check failed ($HEALTH_URL)" >> "$LOG_FILE"
  restart_app
else
  echo "[$TS] WATCHDOG: ok" >> "$LOG_FILE"
fi
