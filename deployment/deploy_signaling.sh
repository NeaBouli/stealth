#!/bin/bash
# ═══════════════════════════════════════════════════════════
# SecureCall — Signaling Server Deployment
# Deploys the Node.js signaling server with PM2
# ═══════════════════════════════════════════════════════════
set -euo pipefail

SERVER_DIR="/opt/securecall/signaling"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

echo "╔══════════════════════════════════════════╗"
echo "║   Deploying Signaling Server             ║"
echo "╚══════════════════════════════════════════╝"

# ─── Copy Application Code ──────────────────────────────
echo "[1/5] Copying signaling server..."
sudo mkdir -p "$SERVER_DIR"
sudo chown -R "$USER:$USER" /opt/securecall

rsync -av --exclude='node_modules' --exclude='.env' --exclude='tools/' \
    "$PROJECT_ROOT/backend/signaling/" "$SERVER_DIR/"

# ─── Install Dependencies ───────────────────────────────
echo "[2/5] Installing dependencies..."
cd "$SERVER_DIR"
npm ci --omit=dev

# ─── Generate Environment Config ────────────────────────
echo "[3/5] Generating environment config..."

TURN_PASS=$(openssl rand -base64 32)
ADMIN_KEY=$(openssl rand -base64 32)

if [ ! -f "$SERVER_DIR/.env" ]; then
    cat > "$SERVER_DIR/.env" << EOF
# SecureCall Signaling Server — Production Config
# Generated: $(date -u '+%Y-%m-%d %H:%M:%S UTC')

NODE_ENV=production
PORT=8080

# STUN/TURN
STUN_URL=stun:stun.l.google.com:19302
TURN_URL=turn:turn.stealthx.app:3478
TURN_USER=securecall
TURN_PASS=${TURN_PASS}

# Admin
ADMIN_API_KEY=${ADMIN_KEY}
ALLOWED_ORIGINS=https://signal.stealthx.app,https://stealthx.app
MAX_CONNS_PER_IP=10

# Firebase (optional — set path to service account JSON)
# FIREBASE_SERVICE_ACCOUNT_KEY=/opt/securecall/firebase-sa.json
EOF
    echo "  .env created with fresh credentials"
else
    echo "  .env already exists — skipping (preserving existing credentials)"
fi

# ─── PM2 Process Setup ──────────────────────────────────
echo "[4/5] Setting up PM2..."

# Stop existing process if running
pm2 stop securecall-signaling 2>/dev/null || true
pm2 delete securecall-signaling 2>/dev/null || true

# Create PM2 ecosystem file
cat > "$SERVER_DIR/ecosystem.config.js" << 'EOF'
module.exports = {
  apps: [{
    name: 'securecall-signaling',
    script: 'src/server.js',
    cwd: '/opt/securecall/signaling',
    instances: 1,
    autorestart: true,
    watch: false,
    max_memory_restart: '256M',
    env: {
      NODE_ENV: 'production'
    },
    error_file: '/opt/securecall/logs/signaling-error.log',
    out_file: '/opt/securecall/logs/signaling-out.log',
    merge_logs: true,
    log_date_format: 'YYYY-MM-DD HH:mm:ss Z'
  }]
};
EOF

mkdir -p /opt/securecall/logs

# Start with PM2
pm2 start "$SERVER_DIR/ecosystem.config.js"
pm2 save

# ─── PM2 Startup ────────────────────────────────────────
echo "[5/5] Configuring auto-start..."
pm2 startup systemd -u "$USER" --hp "$HOME" 2>/dev/null || true

echo ""
echo "═══════════════════════════════════════════"
echo " Signaling server deployed!"
echo ""
echo " Status: $(pm2 jlist 2>/dev/null | python3 -c "import sys,json; d=json.load(sys.stdin); print(d[0]['pm2_env']['status'])" 2>/dev/null || echo 'check with pm2 status')"
echo ""
echo " Credentials saved in: $SERVER_DIR/.env"
echo "   TURN_PASS=${TURN_PASS}"
echo "   ADMIN_API_KEY=${ADMIN_KEY}"
echo ""
echo " SAVE THESE CREDENTIALS SECURELY!"
echo ""
echo " Next: Run ./ssl_setup.sh"
echo "═══════════════════════════════════════════"
