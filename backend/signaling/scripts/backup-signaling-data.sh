#!/bin/sh
set -eu

DATA_DIR="${DATA_DIR:-/opt/stealthx/signaling/data}"
BACKUP_DIR="${BACKUP_DIR:-/opt/stealthx/backups/signaling-data}"
LOG_FILE="${LOG_FILE:-/var/log/stealthx/backup-signaling-data.log}"
RETENTION_DAYS="${RETENTION_DAYS:-30}"
LOCK_FILE="${LOCK_FILE:-/tmp/stealthx-signaling-backup.lock}"

mkdir -p "$BACKUP_DIR" "$(dirname "$LOG_FILE")"

log() {
  printf '%s %s\n' "$(date -u '+%Y-%m-%dT%H:%M:%SZ')" "$*" >> "$LOG_FILE"
}

if [ ! -d "$DATA_DIR" ]; then
  log "ERROR data directory not found: $DATA_DIR"
  exit 1
fi

(
  if ! flock -n 9; then
    log "SKIP backup already running"
    exit 0
  fi

  timestamp="$(date -u '+%Y%m%dT%H%M%SZ')"
  archive="$BACKUP_DIR/signaling-data-$timestamp.tar.gz"
  tmp_archive="$archive.tmp"

  log "START backup data_dir=$DATA_DIR archive=$archive"
  tar -C "$DATA_DIR" -czf "$tmp_archive" .
  mv "$tmp_archive" "$archive"
  chmod 600 "$archive"

  size="$(du -h "$archive" | awk '{print $1}')"
  log "OK backup archive=$archive size=$size"

  find "$BACKUP_DIR" -type f -name 'signaling-data-*.tar.gz' -mtime "+$RETENTION_DAYS" -delete
  log "OK retention retention_days=$RETENTION_DAYS"
) 9>"$LOCK_FILE"
