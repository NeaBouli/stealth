#!/usr/bin/env bash
# Every container image in deploy/ compose files must be pinned to an
# immutable multi-arch index digest (tag@sha256:<64 hex>); no :latest.
set -euo pipefail

root=$(cd "$(dirname "$0")" && pwd)
status=0
found=0
while IFS= read -r file; do
  while IFS= read -r line; do
    found=$((found + 1))
    image=$(printf '%s\n' "$line" | sed -E 's/^[^:]*:[0-9]+:[[:space:]]*image:[[:space:]]*//; s/[[:space:]]+#.*$//; s/^["'\'']//; s/["'\'']$//')
    if ! printf '%s\n' "$image" | grep -Eq '^[a-z0-9./_-]+:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}$'; then
      printf 'unpinned image: %s\n' "$line" >&2
      status=1
    elif printf '%s\n' "$image" | grep -Eq ':latest@'; then
      printf 'mutable tag name with digest: %s\n' "$image" >&2
      status=1
    fi
  done < <(grep -nH -E '^[[:space:]]*image:' "$file" || true)
done < <(find "$root" -name 'docker-compose*.yml' -o -name 'compose*.yml' -o -name 'compose*.yaml' -o -name 'docker-compose*.yaml')

[ "$found" -gt 0 ] || { echo "no compose images found" >&2; exit 1; }
[ "$status" -eq 0 ] && echo "check_image_pins.sh: PASS ($found images)"
exit "$status"
