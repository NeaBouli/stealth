#!/bin/bash
# SecureCall deployment secret-output guard (deterministic, local).
#
# Scope: deployment/*.sh only.
#  1. Flags every echo/printf line that expands a secret-bearing variable
#     (TURN/admin keys, tokens, passwords, secrets, private keys) into
#     stdout/stderr. Findings report path:line only; the offending line
#     content is never printed.
#  2. Synthetic negative control: a generated unsafe sample must be
#     detected, and the guard output must not reproduce its unsafe line.
#  3. Asserts deploy_signaling.sh enforces .env mode 0600 and carries no
#     stale operator instruction to save credential-bearing output.
# Exits non-zero on any failure. No network access, no runtime changes.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
DEPLOY_SCRIPT="$SCRIPT_DIR/deploy_signaling.sh"

SECRET_NAME_RE='(TURN_PASS|TURN_SECRET|ADMIN_KEY|ADMIN_API_KEY|[A-Z0-9_]*(SECRET|PASS|PASSWORD|TOKEN|API_KEY|PRIVATE_KEY)[A-Z0-9_]*)'
UNSAFE_LINE_RE="(echo|printf)[[:space:]]+[^|#]*[\$]\{?${SECRET_NAME_RE}"

fail() {
    echo "GUARD FAIL: $*" >&2
    exit 1
}

# Prints "path:line" findings only; returns 1 when any unsafe line was found.
scan_file() {
    local file="$1" line_no grep_rc match_file
    [ -r "$file" ] || {
        echo "GUARD ERROR: cannot read $file" >&2
        return 2
    }

    match_file="$(mktemp "${TMPDIR:-/tmp}/securecall-guard-matches.XXXXXX")" || return 2
    grep -nE "$UNSAFE_LINE_RE" "$file" > "$match_file"
    grep_rc=$?

    case "$grep_rc" in
        0)
            while IFS=: read -r line_no _; do
                echo "UNSAFE: ${file}:${line_no} expands a secret-bearing variable via echo/printf"
            done < "$match_file"
            rm -f "$match_file"
            return 1
            ;;
        1)
            rm -f "$match_file"
            return 0
            ;;
        *)
            rm -f "$match_file"
            echo "GUARD ERROR: failed to scan $file" >&2
            return 2
            ;;
    esac
}

echo "[1/3] Scanning deployment/*.sh for secret-bearing output..."
findings=0
for script in "$SCRIPT_DIR"/*.sh; do
    # The guard contains its own detection patterns by design; it never
    # expands secret variables and is excluded from the scan scope.
    [ "$(basename "$script")" = "check_deploy_secret_output.sh" ] && continue
    scan_file "$script" || findings=$((findings + 1))
done
[ "$findings" -gt 0 ] && fail "secret-output findings above; remove them before merging"
echo "  OK: no echo/printf expansion of secret-bearing variables"

echo "[2/3] Running synthetic negative control..."
control_dir="$(mktemp -d "${TMPDIR:-/tmp}/securecall-guard-control.XXXXXX")"
trap 'rm -rf "$control_dir"' EXIT
control_file="$control_dir/unsafe_sample.sh"
cat > "$control_file" << 'CONTROL'
#!/bin/bash
echo "TURN_PASS=${TURN_PASS}"
CONTROL
control_rc=0
control_output="$(scan_file "$control_file")" || control_rc=$?
[ "$control_rc" -ne 1 ] && fail "negative control was not detected reliably"
case "$control_output" in
    *'${TURN_PASS}'*|*"TURN_PASS="*)
        fail "guard output reproduced the unsafe line; report path:line only" ;;
esac
echo "  OK: synthetic unsafe line detected; findings report path:line only"

echo "[3/3] Asserting deploy script secret handling..."
grep -qE 'chmod[[:space:]]+0?600[[:space:]]+[^#]*\.env' "$DEPLOY_SCRIPT" \
    || fail "deploy_signaling.sh does not enforce .env mode 0600"
grep -qE 'umask[[:space:]]+0?77' "$DEPLOY_SCRIPT" \
    || fail "deploy_signaling.sh does not create .env under a restrictive umask"
grep -qiE 'SAVE (THE OUTPUT|THESE CREDENTIALS)' "$DEPLOY_SCRIPT" \
    && fail "deploy_signaling.sh still instructs operators to save credential output"
echo "  OK: .env created restrictively, mode 0600 enforced, no stale save-output instruction"

echo "Guard PASS"
