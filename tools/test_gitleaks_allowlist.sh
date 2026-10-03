#!/usr/bin/env bash
# Regression test for the .gitleaks.toml turn-credentials exception.
# Uses only synthetic values, assembled at runtime so this file never matches a rule.
# Prints rule IDs and counts only; scanner output is redacted and kept in a temp dir.
#
# Usage: tools/test_gitleaks_allowlist.sh   (GITLEAKS=/path/to/gitleaks to override)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
CONFIG="$ROOT/.gitleaks.toml"
GITLEAKS="${GITLEAKS:-$(command -v gitleaks || echo "$HOME/go/bin/gitleaks")}"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

SERVER_PATH="backend/signaling/src/server.js"
# Synthetic fixtures, split so the literal text of this script is not a finding.
T="TURN"; S="_SECRET"; P="_PASSWORD"
BENIGN="const $T$S = resolveTurnSecret(process.env);"
FAKE="Synthetic""Fixture""Value0001"
STRIPE="sk_""test_""SyntheticFixtureValue000000"
APIKEY="api""_key = \"SyntheticFixtureValue00000001\""

pass=0; fail=0

# run_case <name> <path> <content> <expected rule id or "none">
run_case() {
  local name="$1" path="$2" content="$3" expected="$4"
  local repo="$WORK/$name"
  mkdir -p "$repo/$(dirname "$path")"
  printf '%s\n' "$content" > "$repo/$path"
  git -C "$repo" init -q
  git -C "$repo" -c user.name=t -c user.email=t@example.invalid add -A
  git -C "$repo" -c user.name=t -c user.email=t@example.invalid commit -qm fixture

  local mode flags got
  for mode in history tree; do
    flags=()
    [ "$mode" = tree ] && flags=(--no-git)
    "$GITLEAKS" detect "${flags[@]}" -s "$repo" -c "$CONFIG" --redact --no-banner \
      --exit-code 0 -r "$WORK/$name-$mode.json" >/dev/null 2>&1
    got="$(python3 -c 'import json,sys
ids=sorted({f["RuleID"] for f in json.load(open(sys.argv[1]))})
print(",".join(ids) or "none")' "$WORK/$name-$mode.json")"
    if [ "$got" = "$expected" ]; then
      pass=$((pass + 1)); echo "PASS $name [$mode] -> $got"
    else
      fail=$((fail + 1)); echo "FAIL $name [$mode] expected=$expected got=$got"
    fi
  done
}

run_case benign_exact        "$SERVER_PATH" "$BENIGN" none
run_case benign_indented     "$SERVER_PATH" "  $BENIGN  " none
run_case literal_same_line   "$SERVER_PATH" "$BENIGN const $T$P = \"$FAKE\";" turn-credentials
run_case literal_fallback    "$SERVER_PATH" "const $T$S = resolveTurnSecret(process.env) || \"$FAKE\";" turn-credentials
run_case literal_assignment  "$SERVER_PATH" "const $T$S = \"$FAKE\";" turn-credentials
run_case benign_other_file   "backend/signaling/src/other.js" "$BENIGN" turn-credentials
run_case stripe_in_server    "$SERVER_PATH" "const key = \"$STRIPE\";" stripe-access-token,stripe-secret-key
run_case apikey_in_server    "$SERVER_PATH" "const $APIKEY;" hardcoded-api-key

echo "passed=$pass failed=$fail"
[ "$fail" -eq 0 ]
