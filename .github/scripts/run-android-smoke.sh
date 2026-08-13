#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "usage: $0 <app-apk> <test-apk> <instrumentation-component>" >&2
  exit 2
fi

resolve_apk() {
  local requested="$1"
  if [[ -s "$requested" ]]; then
    printf '%s\n' "$requested"
    return
  fi

  local matches=()
  while IFS= read -r match; do
    matches+=("$match")
  done < <(find "${GITHUB_WORKSPACE:?}" -type f -path '*/build/outputs/apk/*' -name "$(basename "$requested")")
  if [[ "${#matches[@]}" -ne 1 || ! -s "${matches[0]:-}" ]]; then
    echo "Expected exactly one non-empty APK named $(basename "$requested"); found ${#matches[@]}" >&2
    return 1
  fi
  printf '%s\n' "${matches[0]}"
}

app_apk="$(resolve_apk "$1")"
test_apk="$(resolve_apk "$2")"
instrumentation="$3"

du -h "$app_apk" "$test_apk"

timeout 600 adb install --no-streaming -r -t -g "$app_apk"
timeout 600 adb install --no-streaming -r -t -g "$test_apk"

result="$(mktemp)"
trap 'rm -f "$result"' EXIT
adb shell am instrument -w -r "$instrumentation" | tee "$result"
grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$result"
