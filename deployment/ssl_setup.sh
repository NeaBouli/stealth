#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — SSL Certificate Setup (Let's Encrypt)
# ═══════════════════════════════════════════════════════════
set -euo pipefail

ADMIN_EMAIL="${1:-admin@example.com}"

echo "╔══════════════════════════════════════════╗"
echo "║   SSL Certificate Setup                  ║"
echo "╚══════════════════════════════════════════╝"

# ─── Ensure Nginx configs are in place ───────────────────
echo "[1/4] Checking Nginx config..."
if [ ! -f /etc/nginx/sites-available/signal.stealthx.app ]; then
    echo "  ERROR: Nginx config not found."
    echo "  Copy deployment/nginx_config/*.conf to /etc/nginx/sites-available/ first."
    echo "  Then: sudo ln -s /etc/nginx/sites-available/signal.stealthx.app /etc/nginx/sites-enabled/"
    exit 1
fi

# ─── SSL for Signaling Server ───────────────────────────
echo "[2/4] Obtaining SSL cert for signal.stealthx.app..."
sudo certbot --nginx \
    -d signal.stealthx.app \
    --non-interactive \
    --agree-tos \
    -m "$ADMIN_EMAIL" \
    --redirect

# ─── SSL for Landing Page ───────────────────────────────
echo "[3/4] Obtaining SSL cert for stealthx.app..."
sudo certbot --nginx \
    -d stealthx.app \
    -d www.stealthx.app \
    --non-interactive \
    --agree-tos \
    -m "$ADMIN_EMAIL" \
    --redirect

# ─── SSL for TURN Server ────────────────────────────────
echo "[4/4] Obtaining SSL cert for turn.stealthx.app..."
# TURN needs standalone mode (no nginx for port 3478)
sudo certbot certonly \
    --standalone \
    -d turn.stealthx.app \
    --non-interactive \
    --agree-tos \
    -m "$ADMIN_EMAIL" \
    --preferred-challenges http

# ─── Auto-renewal ───────────────────────────────────────
sudo systemctl enable certbot.timer
sudo systemctl start certbot.timer

# Renewal hook to restart services
sudo mkdir -p /etc/letsencrypt/renewal-hooks/post
sudo tee /etc/letsencrypt/renewal-hooks/post/restart-services.sh > /dev/null << 'EOF'
#!/bin/bash
systemctl reload nginx
systemctl restart coturn
EOF
sudo chmod +x /etc/letsencrypt/renewal-hooks/post/restart-services.sh

echo ""
echo "═══════════════════════════════════════════"
echo " SSL certificates installed!"
echo ""
echo " Certificates:"
echo "   - signal.stealthx.app  (Nginx)"
echo "   - stealthx.app         (Nginx)"
echo "   - turn.stealthx.app    (Coturn)"
echo ""
echo " Auto-renewal: enabled (certbot timer)"
echo " Test renewal: sudo certbot renew --dry-run"
echo ""
echo " Next: Run ./monitoring_setup.sh"
echo "═══════════════════════════════════════════"
