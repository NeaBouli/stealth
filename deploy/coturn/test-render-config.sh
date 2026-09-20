#!/bin/sh

set -eu

script_dir=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
renderer="$script_dir/render-config.sh"
template="$script_dir/turnserver.conf.template"
compose_file="$script_dir/../docker-compose.yml"
temporary_dir=$(mktemp -d "${TMPDIR:-/tmp}/securecall-turn-render.XXXXXX")
trap 'rm -rf "$temporary_dir"' 0 HUP INT TERM

fail() {
  printf '%s\n' "test-render-config: $1" >&2
  exit 1
}

synthetic_secret=$(printf '0123456789abcdef%.0s' 1 2 3 4)
secret_file="$temporary_dir/turn_secret"
runtime_dir="$temporary_dir/runtime"
runtime_config="$runtime_dir/turnserver.conf"
argv_file="$temporary_dir/turnserver.argv"
stdout_file="$temporary_dir/stdout"
stderr_file="$temporary_dir/stderr"
stub="$temporary_dir/turnserver"

mkdir -p "$runtime_dir"
printf '%s\n' '#!/bin/sh' 'printf "%s\n" "$@" > "$ARGV_FILE"' > "$stub"
chmod 700 "$stub"
printf '%s\r\n' "$synthetic_secret" > "$secret_file"
chmod 600 "$secret_file"

ARGV_FILE="$argv_file" \
TURN_SERVER_BIN="$stub" \
TURN_SECRET_FILE="$secret_file" \
TURN_CONFIG_TEMPLATE="$template" \
TURN_CONFIG_RUNTIME="$runtime_config" \
  "$renderer" turnserver inherited-image-argument > "$stdout_file" 2> "$stderr_file"

[ -s "$runtime_config" ] || fail "renderer did not create a runtime config"
mode=$(stat -c '%a' "$runtime_config" 2>/dev/null || stat -f '%Lp' "$runtime_config")
[ "$mode" = "600" ] || fail "runtime config mode is not 600"
[ "$(grep -c '^static-auth-secret=' "$runtime_config")" -eq 1 ] \
  || fail "runtime config does not contain exactly one secret directive"
grep -Eq '^static-auth-secret=[0-9a-f]{64}$' "$runtime_config" \
  || fail "runtime config secret directive is malformed"

for output in "$stdout_file" "$stderr_file" "$argv_file"; do
  case "$(cat "$output")" in
    *"$synthetic_secret"*) fail "secret leaked to process output or argv" ;;
  esac
done
[ "$(wc -l < "$argv_file" | tr -d ' ')" -eq 2 ] \
  || fail "turnserver received inherited image arguments"
[ "$(sed -n '1p' "$argv_file")" = "-c" ] || fail "turnserver config flag is missing"
[ "$(sed -n '2p' "$argv_file")" = "$runtime_config" ] \
  || fail "turnserver config path is missing"

expect_failure() {
  case_name=$1
  case_value=$2
  case_dir="$temporary_dir/$case_name"
  mkdir -p "$case_dir/runtime"
  printf '%s' "$case_value" > "$case_dir/secret"
  if TURN_SERVER_BIN="$stub" \
      TURN_SECRET_FILE="$case_dir/secret" \
      TURN_CONFIG_TEMPLATE="$template" \
      TURN_CONFIG_RUNTIME="$case_dir/runtime/turnserver.conf" \
      "$renderer" > "$case_dir/stdout" 2> "$case_dir/stderr"; then
    fail "$case_name unexpectedly succeeded"
  fi
  [ ! -e "$case_dir/runtime/turnserver.conf" ] \
    || fail "$case_name left a runtime config behind"
  case "$(cat "$case_dir/stdout")$(cat "$case_dir/stderr")" in
    *"$case_value"*) [ -z "$case_value" ] || fail "$case_name leaked its rejected value" ;;
  esac
}

expect_failure empty ""
expect_failure short "${synthetic_secret%?}"
expect_failure uppercase "$(printf '%s' "$synthetic_secret" | tr '[:lower:]' '[:upper:]')"
expect_failure nonhex "${synthetic_secret%?}g"
half_secret=$(printf '0123456789abcdef%.0s' 1 2)
expect_failure multiline "$(printf '%s\n%s' "$half_secret" "$half_secret")"
oversized_value="${synthetic_secret}${synthetic_secret}${synthetic_secret}${synthetic_secret}a"
expect_failure oversized "$oversized_value"

expect_render_failure() {
  case_name=$1
  case_template=$2
  case_runtime=$3
  case_dir="$temporary_dir/$case_name"
  mkdir -p "$case_dir"
  if TURN_SERVER_BIN="$stub" \
      TURN_SECRET_FILE="$secret_file" \
      TURN_CONFIG_TEMPLATE="$case_template" \
      TURN_CONFIG_RUNTIME="$case_runtime" \
      "$renderer" > "$case_dir/stdout" 2> "$case_dir/stderr"; then
    fail "$case_name unexpectedly succeeded"
  fi
  [ ! -e "$case_runtime" ] || fail "$case_name left a runtime config behind"
  case "$(cat "$case_dir/stdout")$(cat "$case_dir/stderr")" in
    *"$synthetic_secret"*) fail "$case_name leaked the secret" ;;
  esac
}

token=__TURN_SECRET_64HEX__
no_token_template="$temporary_dir/no-token.template"
sed "s/$token/REMOVED_SECRET_TOKEN/" "$template" > "$no_token_template"
mkdir -p "$temporary_dir/no-token-runtime"
expect_render_failure no-token "$no_token_template" \
  "$temporary_dir/no-token-runtime/turnserver.conf"

two_token_template="$temporary_dir/two-token.template"
cp "$template" "$two_token_template"
printf '%s\n' "# $token" >> "$two_token_template"
mkdir -p "$temporary_dir/two-token-runtime"
expect_render_failure two-token "$two_token_template" \
  "$temporary_dir/two-token-runtime/turnserver.conf"

duplicate_secret_template="$temporary_dir/duplicate-secret.template"
cp "$template" "$duplicate_secret_template"
printf '%s\n' "# $synthetic_secret" >> "$duplicate_secret_template"
mkdir -p "$temporary_dir/duplicate-secret-runtime"
expect_render_failure duplicate-secret "$duplicate_secret_template" \
  "$temporary_dir/duplicate-secret-runtime/turnserver.conf"

expect_render_failure missing-runtime-dir "$template" \
  "$temporary_dir/no-such-runtime/turnserver.conf"

missing_dir="$temporary_dir/missing"
mkdir -p "$missing_dir/runtime"
if TURN_SERVER_BIN="$stub" \
    TURN_SECRET_FILE="$missing_dir/no-secret" \
    TURN_CONFIG_TEMPLATE="$template" \
    TURN_CONFIG_RUNTIME="$missing_dir/runtime/turnserver.conf" \
    "$renderer" > "$missing_dir/stdout" 2> "$missing_dir/stderr"; then
  fail "missing secret unexpectedly succeeded"
fi

grep -F 'TURN_SECRET_FILE=/run/secrets/turn_secret' "$compose_file" >/dev/null \
  || fail "compose does not configure TURN_SECRET_FILE"
legacy_env_assignment=$(printf '%s%s' 'TURN_SECRET=$' '{TURN_SECRET}')
if grep -F "$legacy_env_assignment" "$compose_file" >/dev/null; then
  fail "compose still exposes TURN_SECRET through the environment"
fi
grep -F 'turn_secret:' "$compose_file" >/dev/null \
  || fail "compose secret declaration is missing"

if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
  docker compose -f "$compose_file" config --quiet \
    || fail "docker compose configuration is invalid"
elif command -v docker-compose >/dev/null 2>&1; then
  docker-compose -f "$compose_file" config --quiet \
    || fail "docker-compose configuration is invalid"
fi

printf '%s\n' "test-render-config.sh: PASS"
