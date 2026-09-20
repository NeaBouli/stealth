#!/bin/sh

set -eu
umask 077

fail() {
  printf '%s\n' "render-turn-config: $1" >&2
  exit 1
}

secret_file=${TURN_SECRET_FILE:-/run/secrets/turn_secret}
template=${TURN_CONFIG_TEMPLATE:-/etc/coturn/turnserver.conf.template}
runtime_config=${TURN_CONFIG_RUNTIME:-/run/turn/turnserver.conf}
turnserver_bin=${TURN_SERVER_BIN:-turnserver}
token=__TURN_SECRET_64HEX__

[ -r "$secret_file" ] || fail "secret file is unreadable"
[ -r "$template" ] || fail "configuration template is unreadable"

secret_size=$(wc -c < "$secret_file") || fail "secret file size could not be read"
[ "$secret_size" -le 256 ] || fail "secret file has an invalid size"
secret=$(awk '
  NR == 1 {
    sub(/\r$/, "")
    value = $0
    next
  }
  { invalid = 1 }
  END {
    if (NR != 1 || invalid) exit 1
    printf "%s", value
  }
' "$secret_file") || fail "secret file must contain exactly one line"
case "$secret" in
  ""|*[!0-9a-f]*) fail "secret must contain exactly 64 lowercase hex characters" ;;
esac
[ "${#secret}" -eq 64 ] || fail "secret must contain exactly 64 lowercase hex characters"

token_count=$(awk -v token="$token" '
  {
    line = $0
    while ((position = index(line, token)) > 0) {
      count += 1
      line = substr(line, position + length(token))
    }
  }
  END { print count + 0 }
' "$template")
[ "$token_count" -eq 1 ] || fail "template must contain exactly one secret token"

runtime_dir=${runtime_config%/*}
[ "$runtime_dir" != "$runtime_config" ] || fail "runtime configuration path must include a directory"
[ -d "$runtime_dir" ] || fail "runtime configuration directory is unavailable"

temporary_config="${runtime_config}.tmp.$$"
cleanup() {
  rm -f "$temporary_config"
}
abort() {
  cleanup
  trap - HUP INT TERM
  exit 1
}
trap cleanup 0
trap abort HUP INT TERM
: > "$temporary_config"

while IFS= read -r line || [ -n "$line" ]; do
  case "$line" in
    *"$token"*)
      prefix=${line%%"$token"*}
      suffix=${line#*"$token"}
      printf '%s%s%s\n' "$prefix" "$secret" "$suffix" >> "$temporary_config"
      ;;
    *) printf '%s\n' "$line" >> "$temporary_config" ;;
  esac
done < "$template"

directive_count=$(grep -Ec '^static-auth-secret=[0-9a-f]{64}$' "$temporary_config" || true)
[ "$directive_count" -eq 1 ] || fail "rendered configuration has an invalid secret directive"

secret_occurrences=0
while IFS= read -r line || [ -n "$line" ]; do
  remainder=$line
  while [ "${remainder#*"$secret"}" != "$remainder" ]; do
    secret_occurrences=$((secret_occurrences + 1))
    remainder=${remainder#*"$secret"}
  done
done < "$temporary_config"
[ "$secret_occurrences" -eq 1 ] || fail "rendered configuration contains an invalid secret count"

chmod 600 "$temporary_config"
mv "$temporary_config" "$runtime_config"
trap - 0 HUP INT TERM
unset secret

# Compose retains the image CMD when entrypoint is overridden. Do not forward
# that inherited "turnserver" argument into turnserver a second time.
exec "$turnserver_bin" -c "$runtime_config"
