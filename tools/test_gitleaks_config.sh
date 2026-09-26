#!/bin/bash
# Regression test for .gitleaks.toml allowlist entries.
# Fixtures are generated at runtime in a temp dir so no secret-shaped
# literal is ever committed. Synthetic values are random and never used.
# Usage: tools/test_gitleaks_config.sh   (requires gitleaks v8.19+ on PATH or GITLEAKS=...)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG="$ROOT/.gitleaks.toml"
GITLEAKS="${GITLEAKS:-$(command -v gitleaks || echo "$HOME/go/bin/gitleaks")}"
[ -x "$GITLEAKS" ] || { echo "gitleaks not found"; exit 2; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Build identifiers by concatenation so this script itself stays clean.
KEY="TURN""_SECRET"
FN="resolve""TurnSecret"
FP_LINE="const ${KEY} = ${FN}(process.env);"
SYN_TURN="$(openssl rand -hex 16)"
SYN_API="$(openssl rand -hex 20)"

fail=0
check() { # name expected(clean|leak) content
  local name="$1" expected="$2" dir="$WORK/$1"
  mkdir -p "$dir"
  printf '%s\n' "$3" > "$dir/fixture.js"
  local rc=0
  "$GITLEAKS" dir --no-banner --redact --log-level error -c "$CONFIG" "$dir" >/dev/null 2>&1 || rc=$?
  local got="clean"; [ "$rc" -eq 1 ] && got="leak"
  if [ "$rc" -gt 1 ]; then got="error($rc)"; fi
  if [ "$got" = "$expected" ]; then echo "PASS $name ($got)"; else echo "FAIL $name: expected $expected, got $got"; fail=1; fi
}

# False positive must be suppressed.
check fp_exact clean "$FP_LINE"
check fp_indented clean "    $FP_LINE"
# Real leaks must still fail.
check turn_literal leak "const ${KEY} = \"${SYN_TURN}\";"
check turn_fallback_literal leak "const ${KEY} = ${FN}(process.env) || \"${SYN_TURN}\";"
check turn_same_line_suffix leak "$FP_LINE const turn_secret = \"${SYN_TURN}\";"
check turn_env_file leak "${KEY}=${SYN_TURN}"
check generic_api_key leak "const api""_key = \"${SYN_API}\";"

# Optional: replay PR #102 history if the ref is available locally.
if git -C "$ROOT" rev-parse -q --verify origin/pr-102 >/dev/null; then
  rc=0
  "$GITLEAKS" git --no-banner --redact --log-level error -c "$CONFIG" \
    --log-opts="origin/main..origin/pr-102" "$ROOT" >/dev/null 2>&1 || rc=$?
  if [ "$rc" -eq 0 ]; then echo "PASS pr102_history (clean)"; else echo "FAIL pr102_history rc=$rc"; fail=1; fi
fi

exit "$fail"
