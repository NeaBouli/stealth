#!/usr/bin/env bash
# Sourced only by the manual visual path. Pure parsers/guards have no side effects.

sc241_host_guard() {
  [[ "$#" == 9 && "$1" == true && "$2" == github-hosted &&
    "$3" == workflow_dispatch && "$4" == 1 && "$5" == "$6" &&
    "$6" =~ ^[0-9a-f]{40}$ && "$7" =~ ^[0-9a-f]{40}$ && -z "$8" &&
    "$9" == com.securecall.app.free.test/androidx.test.runner.AndroidJUnitRunner ]]
}

sc241_one_emulator() {
  local line serial='' count=0 header=false
  while IFS= read -r line || [[ -n "$line" ]]; do
    line="${line%$'\r'}"
    [[ -n "$line" ]] || continue
    if [[ "$header" == false ]]; then
      [[ "$line" == 'List of devices attached' ]] || return 1
      header=true
      continue
    fi
    [[ "$line" =~ ^(emulator-[0-9]+)[[:space:]]+device$ ]] || return 1
    serial="${BASH_REMATCH[1]}"
    count=$((count + 1))
  done
  [[ "$header" == true && "$count" == 1 ]] || return 1
  printf '%s\n' "$serial"
}

sc241_fresh_guard() {
  local packages="$1" processes="$2" line header
  [[ ${#packages} -le 65536 && ${#processes} -le 262144 ]] || return 1
  while IFS= read -r line; do
    [[ -z "$line" || "$line" == package:* ]] || return 1
    [[ "$line" != *com.securecall.app* ]] || return 1
  done <<< "$packages"
  IFS= read -r header <<< "$processes"
  [[ "$header" == USER* && "$header" == *PID* &&
    ( "$header" == *NAME || "$header" == *CMD || "$header" == *COMMAND ) ]] || return 1
  [[ "$processes" =~ $'\n'[^[:space:]]+[[:space:]]+[1-9][0-9]*[[:space:]] ]] || return 1
  [[ "$processes" != *com.securecall.app* ]]
}

sc241_policy_guard() {
  [[ "$1" == '-P OUTPUT DROP' && "$2" == '-P OUTPUT DROP' ]]
}

# Reused SC224 counter-reader primitive, public 21fc5b3e:119-134; stdin instead of a file.
sc241_read_drop_count() {
  local line count='' matches=0
  local header='^Chain OUTPUT \(policy DROP ([0-9]{1,9}) packets, ([0-9]{1,9}) bytes\)'
  header+=$'\r?$'
  while IFS= read -r line || test -n "$line"; do
    if [[ "$line" == *'Chain OUTPUT'* ]]; then
      [[ "$line" =~ $header ]] || return 1
      read -r _ _ _ _ count _ <<< "$line"
      matches=$((matches + 1))
      test "$matches" = 1 || return 1
    fi
  done
  test "$matches" = 1 || return 1
  printf '%s\n' "$((10#$count))"
}

sc241_positive_delta() {
  [[ "$1" =~ ^[0-9]{1,9}$ && "$2" =~ ^[0-9]{1,9}$ ]] || return 1
  (( 10#$2 > 10#$1 )) || return 1
  printf '%s\n' "$((10#$2 - 10#$1))"
}

sc241_probe_delta() {
  [[ "$1" == 1 ]] || return 1
  sc241_positive_delta "$2" "$3"
}

sc241_adb() { adb -s "$sc241_serial" "$@" 2>/dev/null; }

# Fixed-enum stage label for an existing prepare failure arm; caller still returns 1.
sc241_reject() { printf 'SC241_PREPARE_REJECTED:%s\n' "$1" >&2; }

# Optional $1 (PRE_PROBE|POST_PROBE) labels prepare-only calls; argless callers stay silent.
sc241_verify_reject() {
  case "$1" in PRE_PROBE | POST_PROBE) sc241_reject "VERIFY_${1}_$2" ;; esac
  return 1
}

sc241_verify_isolation() {
  local devices ipv4 ipv6 stage="${1:-}"
  devices="$(adb devices 2>/dev/null)" || { sc241_verify_reject "$stage" DEVICES_READ; return 1; }
  [[ "$(sc241_one_emulator <<< "$devices")" == "$sc241_serial" ]] || { sc241_verify_reject "$stage" SERIAL; return 1; }
  ipv4="$(sc241_adb shell iptables -w -S OUTPUT)" || { sc241_verify_reject "$stage" IPV4_READ; return 1; }
  ipv6="$(sc241_adb shell ip6tables -w -S OUTPUT)" || { sc241_verify_reject "$stage" IPV6_READ; return 1; }
  sc241_policy_guard "${ipv4//$'\r'/}" "${ipv6//$'\r'/}" || sc241_verify_reject "$stage" POLICY
}

sc241_prepare_isolation() {
  local devices packages processes api qemu hardware uid before after v6before v6after evidence probe=0 v6probe=0
  devices="$(adb devices 2>/dev/null)" || { sc241_reject DEVICES_READ; return 1; }
  sc241_serial="$(sc241_one_emulator <<< "$devices")" || { sc241_reject SERIAL_SELECT; return 1; }
  qemu="$(sc241_adb shell getprop ro.kernel.qemu)" || { sc241_reject QEMU_READ; return 1; }
  hardware="$(sc241_adb shell getprop ro.hardware)" || { sc241_reject HARDWARE_READ; return 1; }
  api="$(sc241_adb shell getprop ro.build.version.sdk)" || { sc241_reject API_READ; return 1; }
  [[ "${qemu//$'\r'/}" == 1 && ( "${hardware//$'\r'/}" == ranchu ||
    "${hardware//$'\r'/}" == goldfish ) && ( "${api//$'\r'/}" == 24 || "${api//$'\r'/}" == 36 ) ]] || { sc241_reject TARGET_PREDICATE; return 1; }
  sc241_api="${api//$'\r'/}"
  packages="$(sc241_adb shell pm list packages)" || { sc241_reject PACKAGES_READ; return 1; }
  # API24 toolbox ps lists all processes by default and rejects -A.
  if [[ "$sc241_api" == 24 ]]; then
    processes="$(sc241_adb shell ps)" || { sc241_reject PROCESSES_READ_API24; return 1; }
  else
    processes="$(sc241_adb shell ps -A)" || { sc241_reject PROCESSES_READ; return 1; }
  fi
  sc241_fresh_guard "${packages//$'\r'/}" "${processes//$'\r'/}" || { sc241_reject FRESH_PREDICATE; return 1; }
  # Only this fresh disposable QEMU is owned; never kill an unadmitted/foreign target.
  sc241_shutdown_required=true
  timeout 30 adb -s "$sc241_serial" root >/dev/null 2>&1 || { sc241_reject ROOT_RESTART; return 1; }
  timeout 30 adb -s "$sc241_serial" wait-for-device >/dev/null 2>&1 || { sc241_reject ROOT_WAIT; return 1; }
  uid="$(sc241_adb shell id -u)" || { sc241_reject UID_READ; return 1; }
  [[ "${uid//$'\r'/}" == 0 ]] || { sc241_reject UID_PREDICATE; return 1; }
  # All policy commands execute inside the selected guest, never on the host/server.
  sc241_adb shell iptables -w -P OUTPUT DROP >/dev/null || { sc241_reject IPV4_POLICY_SET; return 1; }
  sc241_adb shell ip6tables -w -P OUTPUT DROP >/dev/null || { sc241_reject IPV6_POLICY_SET; return 1; }
  sc241_adb shell iptables -w -F OUTPUT >/dev/null || { sc241_reject IPV4_FLUSH; return 1; }
  sc241_adb shell ip6tables -w -F OUTPUT >/dev/null || { sc241_reject IPV6_FLUSH; return 1; }
  sc241_verify_isolation PRE_PROBE || return 1
  before="$(sc241_adb shell iptables -w -L OUTPUT -v -x -n)" || { sc241_reject IPV4_BEFORE_READ; return 1; }
  v6before="$(sc241_adb shell ip6tables -w -L OUTPUT -v -x -n)" || { sc241_reject IPV6_BEFORE_READ; return 1; }
  before="$(sc241_read_drop_count <<< "$before")" || { sc241_reject IPV4_BEFORE_PARSE; return 1; }
  v6before="$(sc241_read_drop_count <<< "$v6before")" || { sc241_reject IPV6_BEFORE_PARSE; return 1; }
  timeout 5 adb -s "$sc241_serial" shell ping -I lo -c 1 -W 1 127.0.0.1 >/dev/null 2>&1 || probe=$?
  after="$(sc241_adb shell iptables -w -L OUTPUT -v -x -n)" || { sc241_reject IPV4_AFTER_READ; return 1; }
  after="$(sc241_read_drop_count <<< "$after")" || { sc241_reject IPV4_AFTER_PARSE; return 1; }
  sc241_ipv4_delta="$(sc241_probe_delta "$probe" "$before" "$after")" || { sc241_reject IPV4_PROBE_DELTA; return 1; }
  timeout 5 adb -s "$sc241_serial" shell ping -6 -I lo -c 1 -W 1 ::1 >/dev/null 2>&1 || v6probe=$?
  [[ "$v6probe" != 0 ]] || { sc241_reject IPV6_PROBE_SUCCEEDED; return 1; }
  v6after="$(sc241_adb shell ip6tables -w -L OUTPUT -v -x -n)" || { sc241_reject IPV6_AFTER_READ; return 1; }
  v6after="$(sc241_read_drop_count <<< "$v6after")" || { sc241_reject IPV6_AFTER_PARSE; return 1; }
  (( v6after >= v6before )) || { sc241_reject IPV6_COUNTER_DECREASE; return 1; }
  sc241_ipv6_probe=false
  sc241_ipv6_delta=0
  if evidence="$(sc241_probe_delta "$v6probe" "$v6before" "$v6after")"; then
    sc241_ipv6_probe=true
    sc241_ipv6_delta="$evidence"
  fi
  sc241_verify_isolation POST_PROBE || return 1
  sc241_isolated=true
  printf 'CONTACT_VISUAL_ISOLATION:head=%s tree=%s api=%s pre_app=true ipv4_drop=true ipv6_drop=true ipv4_probe=true ipv4_delta=%s ipv6_probe=%s ipv6_delta=%s held=true\n' \
    "$head" "$tree" "$sc241_api" "$sc241_ipv4_delta" "$sc241_ipv6_probe" "$sc241_ipv6_delta"
}

sc241_shutdown_guest() {
  local failed=0 held=false shutdown=false
  if [[ "${sc241_isolated:-false}" == true ]] && sc241_verify_isolation; then held=true; else failed=1; fi
  # Deliberately no unblock/uninstall/host-policy cleanup: DROP outlives every target process.
  timeout 30 adb -s "$sc241_serial" emu kill >/dev/null 2>&1 || failed=1
  if timeout 30 adb -s "$sc241_serial" wait-for-disconnect >/dev/null 2>&1; then
    shutdown=true
  else
    failed=1
  fi
  printf 'CONTACT_VISUAL_SHUTDOWN:head=%s tree=%s held=%s disconnected=%s\n' "$head" "$tree" "$held" "$shutdown"
  return "$failed"
}
