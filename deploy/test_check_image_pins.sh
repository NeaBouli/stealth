#!/usr/bin/env bash
# Positive and negative probes for deploy/check_image_pins.sh on throwaway
# git repositories (compose + Dockerfile FROM, any case, stage aliases).
set -euo pipefail

checker="$(cd "$(dirname "$0")" && pwd)/check_image_pins.sh"
digest="sha256:$(printf '0%.0s' $(seq 1 64))"
work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
fail() { printf 'test_check_image_pins.sh: FAIL: %s\n' "$1" >&2; exit 1; }

probe() {
  local name=$1 expected=$2 dockerfile=$3 compose=$4
  local repo="$work/$name"
  mkdir -p "$repo/deploy"
  git -C "$repo" init -q
  printf '%s\n' "$dockerfile" > "$repo/Dockerfile"
  printf 'services:\n  app:\n    image: %s\n' "$compose" > "$repo/deploy/docker-compose.yml"
  git -C "$repo" add -A
  local actual=0
  IMAGE_PINS_REPO_ROOT="$repo" IMAGE_PINS_COMPOSE_ROOT="$repo/deploy" \
    bash "$checker" > "$repo/out" 2>&1 || actual=$?
  [ "$actual" -eq "$expected" ] || { cat "$repo/out" >&2; fail "$name: exit $actual, expected $expected"; }
}

pinned_image="node:22-alpine@$digest"
probe pinned 0 "FROM $pinned_image AS base" "nginx:1.25-alpine@$digest"
probe stage-alias 0 $'FROM '"$pinned_image"$' AS build\nFROM build AS final\nFROM scratch' "nginx:1.25-alpine@$digest"
probe platform-flag 0 "FROM --platform=linux/amd64 $pinned_image" "nginx:1.25-alpine@$digest"
probe unpinned-from 1 "FROM node:22-alpine" "nginx:1.25-alpine@$digest"
probe lowercase-from 1 "from node:22-alpine" "nginx:1.25-alpine@$digest"
probe indented-mixed-case 1 "  From node:22-alpine as base" "nginx:1.25-alpine@$digest"
probe platform-unpinned 1 "FROM --platform=linux/amd64 node:22-alpine" "nginx:1.25-alpine@$digest"
probe unknown-alias 1 "FROM build" "nginx:1.25-alpine@$digest"
probe unpinned-compose 1 "FROM $pinned_image" "coturn/coturn:latest"
probe latest-with-digest 1 "FROM $pinned_image" "coturn/coturn:latest@$digest"

# A git failure must not turn into an empty, passing file list.
not_repo="$work/not-a-repo"
mkdir -p "$not_repo/deploy"
printf 'services:\n  app:\n    image: nginx:1.25-alpine@%s\n' "$digest" > "$not_repo/deploy/docker-compose.yml"
if IMAGE_PINS_REPO_ROOT="$not_repo" IMAGE_PINS_COMPOSE_ROOT="$not_repo/deploy" \
    bash "$checker" > "$work/not-repo.out" 2>&1; then
  fail "checker passed although git ls-files failed"
fi

echo "test_check_image_pins.sh: PASS"
