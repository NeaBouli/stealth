#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -ne 3 ]]; then
  echo "usage: $0 <app-apk> <test-apk> <instrumentation-component>" >&2
  exit 2
fi

app_apk="$1"
test_apk="$2"
instrumentation="$3"

test -s "$app_apk"
test -s "$test_apk"

timeout 600 adb install --no-streaming -r -t -g "$app_apk"
timeout 600 adb install --no-streaming -r -t -g "$test_apk"

result="$(mktemp)"
trap 'rm -f "$result"' EXIT
adb shell am instrument -w -r "$instrumentation" | tee "$result"
grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$result"

