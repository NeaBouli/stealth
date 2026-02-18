#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — VPS Initial Setup Script
# Target: Ubuntu 22.04 LTS
# ═══════════════════════════════════════════════════════════
set -euo pipefail

echo "╔══════════════════════════════════════════╗"
echo "║   SecureCall Server Setup (bare-metal)   ║"
echo "╚══════════════════════════════════════════╝"

# ─── System Update ───────────────────────────────────────
echo "[1/7] Updating system..."
sudo apt update && sudo apt upgrade -y
sudo apt install -y curl git ufw fail2ban unzip

# ─── Node.js 18 LTS ─────────────────────────────────────
echo "[2/7] Installing Node.js 18..."
if ! command -v node &> /dev/null; then
    curl -fsSL https://deb.nodesource.com/setup_18.x | sudo -E bash -
    sudo apt install -y nodejs
fi
echo "Node.js $(node --version)"
echo "npm $(npm --version)"

# ─── PM2 Process Manager ────────────────────────────────
echo "[3/7] Installing PM2..."
sudo npm install -g pm2

# ─── Nginx Reverse Proxy ────────────────────────────────
echo "[4/7] Installing Nginx..."
sudo apt install -y nginx
sudo systemctl enable nginx

# ─── Certbot (Let's Encrypt) ────────────────────────────
echo "[5/7] Installing Certbot..."
sudo apt install -y certbot python3-certbot-nginx

# ─── Coturn TURN Server ─────────────────────────────────
echo "[6/7] Installing Coturn..."
sudo apt install -y coturn
# Enable coturn daemon
sudo sed -i 's/#TURNSERVER_ENABLED=1/TURNSERVER_ENABLED=1/' /etc/default/coturn

# ─── Firewall ───────────────────────────────────────────
echo "[7/7] Configuring firewall..."
sudo ufw default deny incoming
sudo ufw default allow outgoing
sudo ufw allow 22/tcp        # SSH
sudo ufw allow 80/tcp        # HTTP (certbot + redirect)
sudo ufw allow 443/tcp       # HTTPS
sudo ufw allow 3478/tcp      # TURN TCP
sudo ufw allow 3478/udp      # TURN UDP
sudo ufw allow 5349/tcp      # TURNS TLS
sudo ufw allow 5349/udp      # TURNS DTLS
sudo ufw allow 49152:49200/udp  # TURN relay ports
sudo ufw --force enable

# ─── Create app user ────────────────────────────────────
if ! id -u securecall &>/dev/null 2>&1; then
    sudo adduser --system --group --home /opt/securecall securecall
fi
sudo mkdir -p /opt/securecall/signaling
sudo mkdir -p /var/log/coturn
sudo chown -R securecall:securecall /opt/securecall

echo ""
echo "═══════════════════════════════════════════"
echo " Server setup complete!"
echo ""
echo " Installed:"
echo "   - Node.js $(node --version)"
echo "   - PM2 $(pm2 --version 2>/dev/null || echo 'installed')"
echo "   - Nginx $(nginx -v 2>&1 | cut -d'/' -f2)"
echo "   - Certbot $(certbot --version 2>&1 | awk '{print $2}')"
echo "   - Coturn"
echo "   - UFW firewall"
echo ""
echo " Next: Run ./deploy_signaling.sh"
echo "═══════════════════════════════════════════"
