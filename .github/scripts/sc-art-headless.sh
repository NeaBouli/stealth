#!/usr/bin/env bash
# TEMP headless JNI runner; isolation functions copied from public 23718a78.

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

sc241_verify_isolation() {
  local devices ipv4 ipv6
  devices="$(adb devices 2>/dev/null)" || return 1
  [[ "$(sc241_one_emulator <<< "$devices")" == "$sc241_serial" ]] || return 1
  ipv4="$(sc241_adb shell iptables -w -S OUTPUT)" || return 1
  ipv6="$(sc241_adb shell ip6tables -w -S OUTPUT)" || return 1
  sc241_policy_guard "${ipv4//$'\r'/}" "${ipv6//$'\r'/}"
}

sc241_prepare_isolation() {
  local devices packages processes api qemu hardware uid before after v6before v6after evidence probe=0 v6probe=0
  devices="$(adb devices 2>/dev/null)" || return 1
  sc241_serial="$(sc241_one_emulator <<< "$devices")" || return 1
  qemu="$(sc241_adb shell getprop ro.kernel.qemu)" || return 1
  hardware="$(sc241_adb shell getprop ro.hardware)" || return 1
  api="$(sc241_adb shell getprop ro.build.version.sdk)" || return 1
  [[ "${qemu//$'\r'/}" == 1 && ( "${hardware//$'\r'/}" == ranchu ||
    "${hardware//$'\r'/}" == goldfish ) && ( "${api//$'\r'/}" == 24 || "${api//$'\r'/}" == 36 ) ]] || return 1
  sc241_api="${api//$'\r'/}"
  packages="$(sc241_adb shell pm list packages)" || return 1
  # API24 toolbox ps lists all processes by default and rejects -A.
  if [[ "$sc241_api" == 24 ]]; then
    processes="$(sc241_adb shell ps)" || return 1
  else
    processes="$(sc241_adb shell ps -A)" || return 1
  fi
  sc241_fresh_guard "${packages//$'\r'/}" "${processes//$'\r'/}" || return 1
  # Only this fresh disposable QEMU is owned; never kill an unadmitted/foreign target.
  sc241_shutdown_required=true
  timeout 30 adb -s "$sc241_serial" root >/dev/null 2>&1 || return 1
  timeout 30 adb -s "$sc241_serial" wait-for-device >/dev/null 2>&1 || return 1
  uid="$(sc241_adb shell id -u)" || return 1
  [[ "${uid//$'\r'/}" == 0 ]] || return 1
  # All policy commands execute inside the selected guest, never on the host/server.
  sc241_adb shell iptables -w -P OUTPUT DROP >/dev/null || return 1
  sc241_adb shell ip6tables -w -P OUTPUT DROP >/dev/null || return 1
  sc241_adb shell iptables -w -F OUTPUT >/dev/null || return 1
  sc241_adb shell ip6tables -w -F OUTPUT >/dev/null || return 1
  sc241_verify_isolation || return 1
  before="$(sc241_adb shell iptables -w -L OUTPUT -v -x -n)" || return 1
  v6before="$(sc241_adb shell ip6tables -w -L OUTPUT -v -x -n)" || return 1
  before="$(sc241_read_drop_count <<< "$before")" || return 1
  v6before="$(sc241_read_drop_count <<< "$v6before")" || return 1
  timeout 5 adb -s "$sc241_serial" shell ping -I lo -c 1 -W 1 127.0.0.1 >/dev/null 2>&1 || probe=$?
  after="$(sc241_adb shell iptables -w -L OUTPUT -v -x -n)" || return 1
  after="$(sc241_read_drop_count <<< "$after")" || return 1
  sc241_ipv4_delta="$(sc241_probe_delta "$probe" "$before" "$after")" || return 1
  timeout 5 adb -s "$sc241_serial" shell ping -6 -I lo -c 1 -W 1 ::1 >/dev/null 2>&1 || v6probe=$?
  [[ "$v6probe" != 0 ]] || return 1
  v6after="$(sc241_adb shell ip6tables -w -L OUTPUT -v -x -n)" || return 1
  v6after="$(sc241_read_drop_count <<< "$v6after")" || return 1
  (( v6after >= v6before )) || return 1
  sc241_ipv6_probe=false
  sc241_ipv6_delta=0
  if evidence="$(sc241_probe_delta "$v6probe" "$v6before" "$v6after")"; then
    sc241_ipv6_probe=true
    sc241_ipv6_delta="$evidence"
  fi
  sc241_verify_isolation || return 1
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

# The host pre-creates this file. It is outside the four stdout receipts.
sc_art_record() {
  if [[ -n "$sc_art_diagnostic" ]]; then
    printf '1 %s %s %s\n' "$sc_art_phase" "$1" "$2" > "$sc_art_diagnostic" || true
  fi
}

sc_art_stage() {
  sc_art_phase="$1"
  sc_art_record UNKNOWN UNKNOWN
}

sc_art_exit() {
  local primary=$? teardown=0 status=0
  trap - EXIT HUP INT TERM
  sc_art_record "$primary" UNKNOWN
  if [[ "${sc241_shutdown_required:-false}" == true ]]; then
    sc241_shutdown_guest || teardown=$?
  fi
  if [[ -n "$sc_art_emulator_pid" ]]; then
    if kill -0 "$sc_art_emulator_pid" 2>/dev/null; then
      kill -TERM "$sc_art_emulator_pid" 2>/dev/null || {
        status=$?
        if (( teardown == 0 )); then teardown=$status; fi
      }
    fi
    wait "$sc_art_emulator_pid" 2>/dev/null || true
  fi
  if [[ "$sc_art_server" == true ]]; then
    timeout 10 adb kill-server >/dev/null 2>&1 || {
      status=$?
      if (( teardown == 0 )); then teardown=$status; fi
    }
  fi
  if [[ -n "$sc_art_result" ]]; then
    rm -f "$sc_art_result" || {
      status=$?
      if (( teardown == 0 )); then teardown=$status; fi
    }
  fi
  sc_art_record "$primary" "$teardown"
  if (( primary == 0 && teardown != 0 )); then exit 2; fi
  exit "$primary"
}

# Only the isolation/shutdown functions above are reused, never the visual runner.
sc_art_main() {
  local preapp=false
  sc_art_emulator_pid=''
  sc_art_server=false
  sc_art_result=''
  sc_art_diagnostic=''
  if [[ "${SC_ART_ROOT:-}" == /* && -d "$SC_ART_ROOT" && ! -L "$SC_ART_ROOT" && -O "$SC_ART_ROOT" ]] &&
     [[ "$(stat -c '%a' "$SC_ART_ROOT")" == 700 ]]; then
    local record="$SC_ART_ROOT/phase-exit"
    if [[ -f "$record" && ! -L "$record" && -O "$record" && "$(stat -c '%a:%h' "$record")" == 600:1 ]]; then
      sc_art_diagnostic="$record"
    fi
  fi
  sc_art_stage entry
  trap sc_art_exit EXIT
  trap 'exit 2' HUP INT TERM
  if [[ "$#" == 1 && "$1" == --pre-app ]]; then
    [[ -n "$sc_art_diagnostic" && -z "${SC_ART_APKS+x}" && -z "${SC_ART_CONTROLLER+x}" ]] || exit 2
    preapp=true
    shift
  fi
  [[ "$#" == 0 && "${SC_ART_SOURCE:-}" =~ ^[0-9a-f]{40}$ &&
    "${SC_ART_TREE:-}" =~ ^[0-9a-f]{40}$ && "${SC_ART_RUN:-}" =~ ^[0-9]{1,20}$ ]] || exit 2
  [[ "$(id -u)" != 0 && "${HOME:-}" == "${SC_ART_ROOT:?}/home" &&
    "${ANDROID_AVD_HOME:-}" == "$SC_ART_ROOT/avd" ]] || exit 2
  if [[ "$preapp" == false ]]; then sc_art_result="$SC_ART_ROOT/result.txt"; fi
  sc_art_stage limits
  head="$SC_ART_SOURCE"
  tree="$SC_ART_TREE"
  group="$(sed -n 's/^0:://p' /proc/self/cgroup)"
  [[ "$group" == /* && "$group" != *..* ]] || exit 2
  group="/sys/fs/cgroup$group"
  [[ "$(cat "$group/memory.max")" == 4294967296 &&
    "$(cat "$group/memory.swap.max")" == 0 &&
    "$(cat "$group/pids.max")" == 256 &&
    "$(cat "$group/cpu.max")" == '100000 100000' ]] || exit 2
  [[ "$(ulimit -t)" == 840 && "$(ulimit -n)" == 1024 ]] || exit 2
  grep -q '^NoNewPrivs:[[:space:]]*1$' /proc/self/status
  grep -q '^CapEff:[[:space:]]*0000000000000000$' /proc/self/status
  printf 'SC_ART_SCOPE cgroup=%s\n' "${group#/sys/fs/cgroup}"
  sc_art_stage home
  mkdir -m 700 "$HOME" "$ANDROID_AVD_HOME" "$ANDROID_USER_HOME" "$TMPDIR"
  [[ ! -e "$HOME/.android" ]] || exit 2
  ln -s "$ANDROID_USER_HOME" "$HOME/.android"
  sc_art_stage avd
  if [[ "$preapp" == true ]]; then
    python3 -I "${BASH_SOURCE[0]%/*}/sc-media-qualification.py" preapp-avd-create
  else
    printf 'no\n' | timeout 60 avdmanager create avd -n "sc-art-$SC_ART_RUN" \
      -k 'system-images;android-24;google_apis;x86_64' --device pixel_2 >/dev/null 2>&1
  fi
  sc_art_stage adb
  timeout 15 adb start-server >/dev/null 2>&1
  sc_art_server=true
  sc_art_stage boot
  emulator -avd "sc-art-$SC_ART_RUN" -port 5554 -no-window -no-audio \
    -no-snapshot -no-boot-anim -wipe-data -memory 2048 -cores 1 \
    -gpu swiftshader_indirect -no-metrics -accel on >/dev/null 2>&1 &
  sc_art_emulator_pid=$!
  deadline=$((SECONDS + 480))
  until [[ "$(timeout 5 adb -s emulator-5554 shell getprop sys.boot_completed 2>/dev/null | tr -d '\r\n')" == 1 ]]; do
    kill -0 "$sc_art_emulator_pid" 2>/dev/null || exit 2
    (( SECONDS < deadline )) || exit 2
    sleep 2
  done
  sc_art_stage abi
  [[ "$(adb -s emulator-5554 shell getprop ro.build.version.sdk | tr -d '\r\n')" == 24 &&
    "$(adb -s emulator-5554 shell getprop ro.product.cpu.abi | tr -d '\r\n')" == x86_64 ]] || exit 2
  sc_art_stage isolation
  sc241_prepare_isolation
  [[ "$sc241_api" == 24 ]]
  sc241_verify_isolation
  if [[ "$preapp" == false ]]; then
    sc_art_stage app_install
    timeout 90 adb -s "$sc241_serial" install --no-streaming -t "$SC_ART_APKS/0.apk" >/dev/null 2>&1
    sc241_verify_isolation
    sc_art_stage test_install
    timeout 90 adb -s "$sc241_serial" install --no-streaming -t "$SC_ART_APKS/1.apk" >/dev/null 2>&1
    sc241_verify_isolation
    sc_art_stage instrument
    (
      ulimit -f 512
      timeout 180 adb -s "$sc241_serial" shell am instrument -w -r \
        -e class com.securecall.app.CoreCryptoMediaV3InstrumentedTest \
        com.securecall.app.free.test/androidx.test.runner.AndroidJUnitRunner \
        >"$SC_ART_ROOT/result.txt" 2>/dev/null
    )
    sc241_verify_isolation
    sc_art_stage instrument_receipt
    python3 -I "$SC_ART_CONTROLLER" instrumentation --result "$SC_ART_ROOT/result.txt"
    sc241_verify_isolation
  fi
  sc_art_stage memory
  python3 -I - "$group/memory.events" <<'PY'
import sys
from pathlib import Path
events = dict(line.split() for line in Path(sys.argv[1]).read_text().splitlines())
assert all(int(events.get(key, "0")) == 0 for key in ("oom", "oom_kill", "oom_group_kill"))
PY
  sc_art_stage complete
  if [[ "$preapp" == true ]]; then printf 'SC_ART_PREAPP tests_executed=0\n'; fi
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  set -euo pipefail
  sc_art_main "$@"
fi
