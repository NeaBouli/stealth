#!/bin/sh
# Fix Railway volume uid mismatch at runtime.
# Railway mounts volumes as root; the image's chown is overridden.
# This runs as root, fixes /app/data ownership, then drops to securecall.
set -eu

echo "[entrypoint] preparing data directory"
mkdir -p /app/data

# Do not recurse through the whole mounted volume on every start. Railway can
# keep historical JSON/state files here; a recursive chown can stall deployment.
chown securecall:securecall /app/data 2>/dev/null || true
find /app/data -maxdepth 1 -type f -exec chown securecall:securecall {} + 2>/dev/null || true

if [ "$#" -eq 0 ]; then
  set -- dumb-init -- node src/server.js
fi

echo "[entrypoint] starting: $*"
exec su-exec securecall "$@"
