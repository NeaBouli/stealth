#!/bin/bash
# ─── SecureCall Backup Script ────────────────────────────
# Backs up configuration and SSL certificates.
# Usage: ./backup.sh [backup_dir]

set -euo pipefail
umask 077

BACKUP_DIR="${1:-/opt/securecall/backups}"
TIMESTAMP=$(date '+%Y%m%d_%H%M%S')
BACKUP_NAME="securecall_backup_${TIMESTAMP}"
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"

echo "Starting backup to $BACKUP_DIR/$BACKUP_NAME..."

mkdir -p "$BACKUP_DIR/$BACKUP_NAME"

# Backup environment config (contains secrets)
if [ -f "$DEPLOY_DIR/.env" ]; then
    cp "$DEPLOY_DIR/.env" "$BACKUP_DIR/$BACKUP_NAME/.env"
    echo "  Backed up .env"
fi

# Backup the secret-free TURN template. The Compose TURN secret is deliberately
# excluded because this archive is not an encrypted secret store.
if [ -f "$DEPLOY_DIR/coturn/turnserver.conf.template" ]; then
    cp "$DEPLOY_DIR/coturn/turnserver.conf.template" "$BACKUP_DIR/$BACKUP_NAME/turnserver.conf.template"
    echo "  Backed up secret-free TURN config template"
fi

# Backup SSL certificates
if [ -d "$DEPLOY_DIR/certbot/conf" ]; then
    tar -czf "$BACKUP_DIR/$BACKUP_NAME/ssl_certs.tar.gz" -C "$DEPLOY_DIR/certbot" conf/
    echo "  Backed up SSL certificates"
fi

# Backup nginx config
if [ -d "$DEPLOY_DIR/nginx" ]; then
    tar -czf "$BACKUP_DIR/$BACKUP_NAME/nginx_config.tar.gz" -C "$DEPLOY_DIR" nginx/
    echo "  Backed up nginx config"
fi

# Create final archive
tar -czf "$BACKUP_DIR/${BACKUP_NAME}.tar.gz" -C "$BACKUP_DIR" "$BACKUP_NAME"
chmod 600 "$BACKUP_DIR/${BACKUP_NAME}.tar.gz"
rm -rf "$BACKUP_DIR/$BACKUP_NAME"

echo "Backup complete: $BACKUP_DIR/${BACKUP_NAME}.tar.gz"
echo "TURN secret excluded; restore requires a private shared secret before services start."

# Cleanup old backups (keep last 10)
ls -t "$BACKUP_DIR"/securecall_backup_*.tar.gz 2>/dev/null | tail -n +11 | xargs -r rm
echo "Old backups cleaned up (keeping last 10)."
