#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — Monitoring & Logging Setup
# ═══════════════════════════════════════════════════════════
set -euo pipefail

echo "╔══════════════════════════════════════════╗"
echo "║   Monitoring & Logging Setup             ║"
echo "╚══════════════════════════════════════════╝"

# ─── PM2 Log Rotation ───────────────────────────────────
echo "[1/4] Configuring PM2 log rotation..."
pm2 install pm2-logrotate
pm2 set pm2-logrotate:max_size 10M
pm2 set pm2-logrotate:retain 30
pm2 set pm2-logrotate:compress true

# ─── Coturn Log Rotation ────────────────────────────────
echo "[2/4] Configuring Coturn log rotation..."
sudo tee /etc/logrotate.d/coturn > /dev/null << 'EOF'
/var/log/coturn/*.log {
    daily
    rotate 30
    compress
    delaycompress
    notifempty
    missingok
    create 0640 turnserver turnserver
    postrotate
        systemctl reload coturn 2>/dev/null || true
    endscript
}
EOF

# ─── Health Check Cron ───────────────────────────────────
echo "[3/4] Setting up health check cron..."

sudo tee /opt/securecall/healthcheck.sh > /dev/null << 'SCRIPT'
#!/bin/bash
# SecureCall Production Health Check
TIMESTAMP=$(date '+%Y-%m-%d %H:%M:%S')
FAILURES=0

# Check signaling server
HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" --max-time 5 http://localhost:8080/ 2>/dev/null || echo "000")
if [ "$HTTP_CODE" != "200" ]; then
    echo "[$TIMESTAMP] FAIL: Signaling server HTTP $HTTP_CODE"
    FAILURES=$((FAILURES + 1))
    pm2 restart securecall-signaling
fi

# Check PM2 process
if ! pm2 jlist 2>/dev/null | grep -q '"status":"online"'; then
    echo "[$TIMESTAMP] FAIL: PM2 process not online"
    FAILURES=$((FAILURES + 1))
    pm2 restart securecall-signaling
fi

# Check disk space
DISK_USAGE=$(df / | awk 'NR==2 {print $5}' | tr -d '%')
if [ "$DISK_USAGE" -gt 90 ]; then
    echo "[$TIMESTAMP] WARN: Disk usage ${DISK_USAGE}%"
    FAILURES=$((FAILURES + 1))
fi

if [ "$FAILURES" -eq 0 ]; then
    echo "[$TIMESTAMP] OK: All checks passed"
fi
SCRIPT

sudo chmod +x /opt/securecall/healthcheck.sh

# Add cron job (every 5 minutes)
(crontab -l 2>/dev/null | grep -v 'healthcheck.sh'; echo "*/5 * * * * /opt/securecall/healthcheck.sh >> /opt/securecall/logs/healthcheck.log 2>&1") | crontab -

# ─── PM2 Monitoring Dashboard ───────────────────────────
echo "[4/4] PM2 monitoring info..."

echo ""
echo "═══════════════════════════════════════════"
echo " Monitoring setup complete!"
echo ""
echo " Logs:"
echo "   pm2 logs securecall-signaling"
echo "   tail -f /opt/securecall/logs/healthcheck.log"
echo "   tail -f /var/log/nginx/access.log"
echo ""
echo " Status:"
echo "   pm2 status"
echo "   pm2 monit  (real-time dashboard)"
echo ""
echo " Recommended: Set up UptimeRobot (free)"
echo "   URL: https://signal.stealthx.app/"
echo "   Interval: 5 minutes"
echo "═══════════════════════════════════════════"
