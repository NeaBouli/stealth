#!/bin/sh
# Fix Railway volume uid mismatch at runtime.
# Railway mounts volumes as root; the image's chown is overridden.
# This runs as root, fixes /app/data ownership, then drops to securecall.
chown -R securecall:securecall /app/data 2>/dev/null || true
exec su-exec securecall "$@"
