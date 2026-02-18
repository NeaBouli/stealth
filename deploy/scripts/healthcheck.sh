#!/bin/bash
# ─── SecureCall Health Check Script ──────────────────────
# Checks all services and alerts on failures.
# Usage: ./healthcheck.sh
# Recommended: Run via cron every 5 minutes.

set -euo pipefail

SIGNAL_URL="${SIGNAL_URL:-https://signal.securecall.app/}"
WEBSITE_URL="${WEBSITE_URL:-https://stealthx.app/}"
ALERT_EMAIL="${ALERT_EMAIL:-admin@stealthx.app}"
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
FAILURES=0

log() {
    echo "[$TIMESTAMP] $1"
}

check_http() {
    local name="$1"
    local url="$2"
    local expected="${3:-200}"

    status=$(curl -s -o /dev/null -w "%{http_code}" --max-time 10 "$url" 2>/dev/null || echo "000")

    if [ "$status" = "$expected" ]; then
        log "OK: $name ($url) — HTTP $status"
    else
        log "FAIL: $name ($url) — HTTP $status (expected $expected)"
        FAILURES=$((FAILURES + 1))
    fi
}

check_docker() {
    local container="$1"

    if docker inspect --format='{{.State.Running}}' "$container" 2>/dev/null | grep -q true; then
        log "OK: Container $container is running"
    else
        log "FAIL: Container $container is NOT running"
        FAILURES=$((FAILURES + 1))
    fi
}

check_ssl() {
    local domain="$1"
    local expiry

    expiry=$(echo | openssl s_client -servername "$domain" -connect "$domain:443" 2>/dev/null | \
             openssl x509 -noout -enddate 2>/dev/null | cut -d= -f2)

    if [ -n "$expiry" ]; then
        expiry_epoch=$(date -d "$expiry" +%s 2>/dev/null || date -jf "%b %d %T %Y %Z" "$expiry" +%s 2>/dev/null || echo 0)
        now_epoch=$(date +%s)
        days_left=$(( (expiry_epoch - now_epoch) / 86400 ))

        if [ "$days_left" -lt 7 ]; then
            log "WARN: SSL cert for $domain expires in $days_left days!"
            FAILURES=$((FAILURES + 1))
        else
            log "OK: SSL cert for $domain valid for $days_left days"
        fi
    else
        log "FAIL: Cannot check SSL cert for $domain"
        FAILURES=$((FAILURES + 1))
    fi
}

# ─── Run Checks ──────────────────────────────────────────
log "Starting health check..."

# HTTP endpoints
check_http "Signaling Server" "$SIGNAL_URL"
check_http "Website" "$WEBSITE_URL"

# Docker containers
check_docker "securecall-signaling"
check_docker "securecall-nginx"
check_docker "securecall-turn"

# SSL certificates
check_ssl "signal.securecall.app"
check_ssl "stealthx.app"

# ─── Disk Space ──────────────────────────────────────────
disk_usage=$(df / | awk 'NR==2 {print $5}' | tr -d '%')
if [ "$disk_usage" -gt 90 ]; then
    log "WARN: Disk usage at ${disk_usage}%"
    FAILURES=$((FAILURES + 1))
else
    log "OK: Disk usage at ${disk_usage}%"
fi

# ─── Memory ─────────────────────────────────────────────
mem_available=$(free -m | awk 'NR==2 {printf "%.0f", $7/$2*100}')
if [ "$mem_available" -lt 10 ]; then
    log "WARN: Only ${mem_available}% memory available"
    FAILURES=$((FAILURES + 1))
else
    log "OK: ${mem_available}% memory available"
fi

# ─── Summary ────────────────────────────────────────────
if [ "$FAILURES" -gt 0 ]; then
    log "ALERT: $FAILURES check(s) failed!"
    # Uncomment to send email alerts:
    # echo "SecureCall: $FAILURES health check(s) failed at $TIMESTAMP" | \
    #   mail -s "SecureCall Health Alert" "$ALERT_EMAIL"
    exit 1
else
    log "All checks passed."
    exit 0
fi
