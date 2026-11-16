#!/usr/bin/env bash
set -euo pipefail

if [[ "$#" -lt 3 || "$#" -gt 5 ]]; then
  echo "usage: $0 <app-apk> <test-apk> <instrumentation-component> [securecallLivePinTest=true] [securecallContactVisualGate=true] [securecallContactRootProbe=true]" >&2
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
  if [[ "${#matches[@]}" -eq 0 && -d "$(dirname "$requested")" ]]; then
    while IFS= read -r match; do
      matches+=("$match")
    done < <(find "$(dirname "$requested")" -maxdepth 1 -type f -name '*.apk' | sort)
  fi
  if [[ "${#matches[@]}" -gt 1 ]]; then
    local preferred=()
    local candidate
    for candidate in "${matches[@]}"; do
      [[ "$(basename "$candidate")" == *universal*.apk ]] && preferred+=("$candidate")
    done
    if [[ "${#preferred[@]}" -eq 0 ]]; then
      for candidate in "${matches[@]}"; do
        [[ "$(basename "$candidate")" == *x86_64*.apk ]] && preferred+=("$candidate")
      done
    fi
    [[ "${#preferred[@]}" -eq 1 ]] && matches=("${preferred[0]}")
  fi
  if [[ "${#matches[@]}" -ne 1 || ! -s "${matches[0]:-}" ]]; then
    echo "Expected exactly one non-empty APK named $(basename "$requested"); found ${#matches[@]}" >&2
    return 1
  fi
  printf '%s\n' "${matches[0]}"
}

app_apk="$(resolve_apk "$1")"
test_apk="$(resolve_apk "$2")"
instrumentation="$3"
instrumentation_args=()
visual=false
live_pin=false
root_probe=false
for option in "${@:4}"; do
  case "$option" in
    "") ;;
    securecallLivePinTest=true)
      [[ "$live_pin" == false ]] || exit 2
      live_pin=true
      instrumentation_args+=(-e securecallLivePinTest true)
      ;;
    securecallContactVisualGate=true)
      [[ "$visual" == false ]] || exit 2
      visual=true
      ;;
    securecallContactRootProbe=true)
      [[ "$root_probe" == false ]] || exit 2
      root_probe=true
      ;;
    *) echo 'unsupported instrumentation option' >&2; exit 2 ;;
  esac
done

if [[ "$root_probe" == true ]]; then
  [[ "$visual" == false && "$live_pin" == false ]] || exit 2
  [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted && "${GITHUB_EVENT_NAME:-}" == workflow_dispatch ]] || exit 2
  [[ "$instrumentation" == com.securecall.app.free.test/androidx.test.runner.AndroidJUnitRunner ]] || exit 2
  [[ "$(adb shell getprop ro.build.version.sdk | tr -d '\r\n')" == 24 ]] || exit 2
  head="$(git rev-parse HEAD)"
  tree="$(git rev-parse 'HEAD^{tree}')"
  [[ "${GITHUB_SHA:-}" == "$head" && -z "$(git status --porcelain)" ]] || exit 2
  instrumentation_args+=(-e securecallContactRootProbe true -e securecallRootProbeHosted true
    -e securecallRootProbeHead "$head" -e securecallRootProbeTree "$tree")
fi

visual_case='com.securecall.app.ContactLookupPreservationInstrumentedTest#syntheticContactWindowMatrix'
if [[ "$visual" == true ]]; then
  # This is an explicit opt-in, not permission to emulate/fabricate a hosted environment.
  [[ "${GITHUB_ACTIONS:-}" == true && "${RUNNER_ENVIRONMENT:-}" == github-hosted ]] || {
    echo 'CONTACT_VISUAL_REJECTED:PUBLIC_HOSTED_CI_REQUIRED' >&2; exit 2;
  }
  [[ "$instrumentation" == com.securecall.app.free.test/androidx.test.runner.AndroidJUnitRunner ]] || exit 2
  command -v python3 >/dev/null || { echo 'CONTACT_VISUAL_REJECTED:PYTHON_MISSING' >&2; exit 2; }
fi

du -h "$app_apk" "$test_apk"

timeout 600 adb install --no-streaming -r -t -g "$app_apk"
timeout 600 adb install --no-streaming -r -t -g "$test_apk"

result="${GITHUB_WORKSPACE:?}/client_android/app/build/outputs/androidTest-results/instrumentation/result.txt"
mkdir -p "$(dirname "$result")"
adb shell am instrument -w -r -e notClass "$visual_case" "${instrumentation_args[@]}" "$instrumentation" | tee "$result"
grep -Eq '^OK \([1-9][0-9]* tests?\)$' "$result"

if [[ "$visual" == true ]]; then
  head="$(git rev-parse HEAD)"
  tree="$(git rev-parse 'HEAD^{tree}')"
  visual_tmp="$(mktemp -d)"
  trap 'rm -f "$visual_tmp/result.txt"; rmdir "$visual_tmp"' EXIT
  # Keep protocol/stack output outside artifacts. Export only validated fixed synthetic files.
  if ! timeout 900 adb shell am instrument -w -r -e class "$visual_case" \
      -e securecallContactVisualGate true -e securecallVisualHead "$head" \
      -e securecallVisualTree "$tree" "$instrumentation" >"$visual_tmp/result.txt"; then
    echo 'CONTACT_VISUAL_REJECTED:INSTRUMENTATION_STARTUP_OR_TRANSPORT_FAILURE' >&2
    exit 2
  fi
  api="$(adb shell getprop ro.build.version.sdk | tr -d '\r\n')"
  [[ "$api" == 24 || "$api" == 36 ]] || exit 2
  python3 "${GITHUB_WORKSPACE:?}/.github/scripts/collect_contact_visual.py" \
    --result "$visual_tmp/result.txt" --ordinary-result "$result" \
    --output "$(dirname "$result")/contact-visual-api$api" \
    --head "$head" --tree "$tree"
fi
