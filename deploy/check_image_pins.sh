#!/usr/bin/env bash
# Every container image in deploy/ compose files and every Dockerfile FROM in
# the repository must be pinned to an immutable multi-arch index digest
# (tag@sha256:<64 hex>); no :latest. FROM lines are matched
# case-insensitively; earlier build-stage aliases and `scratch` are allowed.
set -euo pipefail

root=${IMAGE_PINS_COMPOSE_ROOT:-$(cd "$(dirname "$0")" && pwd)}
repo_root=${IMAGE_PINS_REPO_ROOT:-$(cd "$(dirname "$0")/.." && pwd)}
pinned='^[a-z0-9./_-]+:[A-Za-z0-9._-]+@sha256:[0-9a-f]{64}$'
status=0
found=0

compose_files=$(find "$root" \( -name 'docker-compose*.yml' -o -name 'docker-compose*.yaml' \
  -o -name 'compose*.yml' -o -name 'compose*.yaml' \) -type f)
while IFS= read -r file; do
  [ -n "$file" ] || continue
  matches=$(grep -nH -E '^[[:space:]]*image:' "$file") || [ $? -eq 1 ] || exit 2
  while IFS= read -r line; do
    [ -n "$line" ] || continue
    found=$((found + 1))
    image=$(printf '%s\n' "$line" | sed -E 's/^[^:]*:[0-9]+:[[:space:]]*image:[[:space:]]*//; s/[[:space:]]+#.*$//; s/^["'\'']//; s/["'\'']$//')
    if ! printf '%s\n' "$image" | grep -Eq "$pinned"; then
      printf 'unpinned image: %s\n' "$line" >&2
      status=1
    elif printf '%s\n' "$image" | grep -Eq ':latest@'; then
      printf 'mutable tag name with digest: %s\n' "$image" >&2
      status=1
    fi
  done <<< "$matches"
done <<< "$compose_files"

# A failing git invocation aborts here instead of yielding an empty list.
dockerfiles=$(git -C "$repo_root" ls-files -- '*Dockerfile*' '*Containerfile*' '*.dockerfile')
while IFS= read -r relative; do
  [ -n "$relative" ] || continue
  # Prints "<line>\t<image>" for every FROM (any case), skipping --flags.
  froms=$(awk 'BEGIN { IGNORECASE = 1 }
    tolower($1) == "from" {
      for (i = 2; i <= NF; i++) if ($i !~ /^--/) { image = $i; break }
      alias = ""
      if (tolower($(i + 1)) == "as") alias = $(i + 2)
      print NR "\t" image "\t" alias
    }' "$repo_root/$relative")
  stages=" "
  while IFS=$'\t' read -r number image alias; do
    [ -n "${number:-}" ] || continue
    found=$((found + 1))
    if [ "$image" = "scratch" ] || [[ "$stages" == *" $image "* ]]; then
      :
    elif ! printf '%s\n' "$image" | grep -Eq "$pinned"; then
      printf 'unpinned base image: %s:%s: %s\n' "$relative" "$number" "$image" >&2
      status=1
    fi
    [ -z "${alias:-}" ] || stages="$stages$alias "
  done <<< "$froms"
done <<< "$dockerfiles"

[ "$found" -gt 0 ] || { echo "no images found" >&2; exit 1; }
[ "$status" -eq 0 ] && echo "check_image_pins.sh: PASS ($found images)"
exit "$status"
