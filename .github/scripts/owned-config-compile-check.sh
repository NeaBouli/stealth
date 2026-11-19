#!/usr/bin/env bash
# Hosted compile-only qualification of the owned-test network variant data
# (SC-OWNED-CALL-COMPILE-PREP-20261119). Drives existing Gradle tasks only and prints
# fixed receipts. No emulator, device, connected test or network to an owned service.
# Throwaway public CA material and the CI-only keystore live in RUNNER_TEMP and are
# removed on exit. Must be run from client_android/. Never enable xtrace here.
set -euo pipefail
set +x
umask 077

: "${RUNNER_TEMP:?RUNNER_TEMP is required}"
: "${ANDROID_HOME:?ANDROID_HOME is required}"

WORK="${RUNNER_TEMP}/owned-config-compile"
LOG="${WORK}/gradle.log"
BUILD_TOOLS="${ANDROID_HOME}/build-tools/36.0.0"
AAPT2="${BUILD_TOOLS}/aapt2"
APKSIGNER="${BUILD_TOOLS}/apksigner"
OWNED_HOST="10.0.2.2"
OWNED_SIGNAL_URL="wss://${OWNED_HOST}:18443/signal"
OWNED_STUN_URL="stun:${OWNED_HOST}:13478"
PROD_SIGNAL_URL="wss://api.stealthx.tech/signal"
PROD_STUN_URL="stun:stun.l.google.com:19302"
FREE_PACKAGE="com.securecall.app.free"

cleanup() { rm -rf "${WORK}"; }
trap cleanup EXIT

receipt() { printf 'RECEIPT %s\n' "$*"; }
fail() { printf 'FAILED %s\n' "$*" >&2; exit 1; }

# Prints only fixed failure lines, never the full log (no config dumps).
gradle_run() {
  if ! ./gradlew --no-daemon --max-workers=1 --console=plain "$@" > "${LOG}" 2>&1; then
    grep -E '^(FAILURE:|\* What went wrong:|> |e: )' "${LOG}" | head -n 40 || true
    return 1
  fi
}

count_fixed() { # count_fixed <needle> <file>: number of matching lines, 0 when absent
  grep -caF -- "$1" "$2" || true
}

rm -rf "${WORK}"
mkdir -m 0700 "${WORK}"
chmod +x gradlew
export ANDROID_NDK_HOME="${ANDROID_HOME}/ndk/27.0.12077973"
export ANDROID_NDK_ROOT="${ANDROID_NDK_HOME}"

# --- Step 1: throwaway public CA + IP-SAN leaf (keys deleted right after use) ---
CA="${WORK}/owned_ca.pem"
openssl req -x509 -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
  -keyout "${WORK}/ca.key" -out "${CA}" -days 2 -subj "/CN=SecureCall CI Owned Test CA" \
  -addext "basicConstraints=critical,CA:TRUE" -addext "keyUsage=critical,keyCertSign,cRLSign" \
  > /dev/null 2>&1
openssl req -newkey ec -pkeyopt ec_paramgen_curve:prime256v1 -nodes \
  -keyout "${WORK}/leaf.key" -out "${WORK}/leaf.csr" -subj "/CN=${OWNED_HOST}" > /dev/null 2>&1
printf 'subjectAltName=IP:%s\nbasicConstraints=critical,CA:FALSE\nextendedKeyUsage=serverAuth\n' \
  "${OWNED_HOST}" > "${WORK}/leaf.ext"
openssl x509 -req -in "${WORK}/leaf.csr" -CA "${CA}" -CAkey "${WORK}/ca.key" \
  -CAcreateserial -days 2 -extfile "${WORK}/leaf.ext" -out "${WORK}/leaf.pem" > /dev/null 2>&1
rm -f "${WORK}/ca.key" "${WORK}/leaf.key" "${WORK}/leaf.csr" "${WORK}/owned_ca.srl"
openssl verify -CAfile "${CA}" -verify_ip "${OWNED_HOST}" "${WORK}/leaf.pem" > /dev/null 2>&1 \
  || fail "leaf_ip_san_verify=false"
chmod 0600 "${CA}" "${WORK}/leaf.pem" "${WORK}/leaf.ext"
test "$(grep -c -- '-----BEGIN ' "${CA}")" = 1 || fail "ca_pem_single_block=false"
test "$(grep -c 'PRIVATE KEY' "${CA}" || true)" = 0 || fail "ca_pem_no_key=false"
PIN_B64="$(openssl x509 -in "${CA}" -pubkey -noout | openssl pkey -pubin -outform der \
  | openssl dgst -sha256 -binary | base64)"
OWNED_PIN="sha256/${PIN_B64}"
CA_TOKEN="$(sed -n 3p "${CA}")"
test "${#PIN_B64}" = 44 || fail "pin_length=false"
test -n "${CA_TOKEN}" || fail "ca_token=false"
test "$(find "${WORK}" -name '*.key' | wc -l | tr -d ' ')" = 0 || fail "private_keys_removed=false"
receipt "step=ca_generated private_keys_remaining=0 leaf_ip_san_verify=true"

OWNED_ARGS=(
  -PisolatedDeviceTest
  "-PownedTestSignalUrl=${OWNED_SIGNAL_URL}"
  "-PownedTestStunUrl=${OWNED_STUN_URL}"
  "-PownedTestCaCert=${CA}"
  "-PownedTestPin=${OWNED_PIN}"
)

# --- Step 2: execute only the scoped owned/relay JVM tests for Free and Premium. ---
UNIT_CASES=(--tests com.securecall.app.net.OwnedTestNetworkConfigTest
  --tests com.securecall.app.net.OwnedIcePolicyTest --tests com.securecall.app.net.WebRtcRelayPolicyTest --tests com.securecall.app.net.OwnedMediaReceiptTest --tests com.securecall.app.net.CallMediaReceiveGateTest)
gradle_run -Pinternal testFreeDebugUnitTest "${UNIT_CASES[@]}" testPremiumDebugUnitTest "${UNIT_CASES[@]}" \
  || fail "step=unit_tests"
python3 - <<'PY'
import pathlib
import sys
import xml.etree.ElementTree as ET
try:
    for variant in ("Free", "Premium"):
        total = 0
        for name in ("OwnedTestNetworkConfigTest", "OwnedIcePolicyTest", "WebRtcRelayPolicyTest", "OwnedMediaReceiptTest", "CallMediaReceiveGateTest"):
            report = pathlib.Path(f"app/build/test-results/test{variant}DebugUnitTest/TEST-com.securecall.app.net.{name}.xml")
            result = ET.parse(report).getroot()
            count = int(result.attrib["tests"])
            assert count > 0 and all(int(result.attrib.get(key, "0")) == 0 for key in ("failures", "errors", "skipped"))
            total += count
        print(f"RECEIPT step=unit_tests variant={variant} tests={total} failures=0 errors=0 skipped=0")
except (OSError, ET.ParseError, KeyError, ValueError, AssertionError):
    sys.exit("FAILED step=unit_results")
PY
for variant in freeDebugUnitTest premiumDebugUnitTest; do
  for cls in OwnedTestNetworkConfigTest OwnedIcePolicyTest WebRtcRelayPolicyTest OwnedMediaReceiptTest CallMediaReceiveGateTest; do
    n="$(find app/build -type f -name "${cls}.class" -path "*${variant}*" \
      -path '*com/securecall/app/net/*' | wc -l | tr -d ' ')"
    test "${n}" -ge 1 || fail "step=unit_test_compile variant=${variant} class=${cls} count=0"
    receipt "step=unit_test_compile variant=${variant} class=${cls} class_files=${n}"
  done
done

# --- Step 3: fixed refusals via --dry-run (configuration-time GradleException only) ---
expect_refusal() { # expect_refusal <label> <exact category> <gradle args...>
  local label="$1" category="$2"
  shift 2
  if ./gradlew --no-daemon --console=plain --dry-run "$@" > "${LOG}" 2>&1; then
    fail "step=refusal case=${label} exit_nonzero=false"
  fi
  local categories
  categories="$(grep -aoE 'Owned-test network refused: [^"]*' "${LOG}" | sed 's/[[:space:]]*$//' | sort -u)"
  test "${categories}" = "Owned-test network refused: ${category}" \
    || fail "step=refusal case=${label} exact_category=false"
  receipt "step=refusal case=${label} exit_nonzero=true exact_category=true"
}
expect_refusal direct_release "inputs are accepted only for explicit debug tasks" \
  "${OWNED_ARGS[@]}" assembleFreeRelease
expect_refusal missing_isolated "requires -PisolatedDeviceTest" \
  "${OWNED_ARGS[@]:1}" assembleFreeDebug
receipt "step=refusal case=indirect_release_graph status=NOT_RUN reason=no_existing_debug_labelled_release_dependent_task"

# BuildConfig source receipt: exactly one generated source (Java) or bytecode jar per variant.
buildconfig_text() { # buildconfig_text <flavor> <buildType> <out>
  local flavor="$1" type="$2" out="$3" variant="$1${2^}"
  mapfile -t java < <(find app/build/generated -type f -name BuildConfig.java \
    \( -path "*/${flavor}/${type}/*" -o -path "*/${variant}/*" \) 2>/dev/null)
  mapfile -t jars < <(find app/build/intermediates -type f -name '*.jar' -ipath '*buildconfig*' \
    -path "*${variant}*" 2>/dev/null)
  if [ "${#java[@]}" -eq 1 ] && [ "${#jars[@]}" -eq 0 ]; then
    cp "${java[0]}" "${out}"
  elif [ "${#java[@]}" -eq 0 ] && [ "${#jars[@]}" -eq 1 ]; then
    javap -constants -cp "${jars[0]}" com.securecall.app.BuildConfig \
      | sed 's/java\.lang\.String/String/' > "${out}"
  else
    fail "step=buildconfig variant=${variant} java=${#java[@]} jars=${#jars[@]}"
  fi
}
expect_field() { # expect_field <file> <variant> <name> <exact value>
  local file="$1" variant="$2" name="$3" value="$4" got
  got="$(awk -v p="public static final String $3 = " \
    '{ sub(/^[ \t]+/, ""); if (index($0, p) == 1) print substr($0, length(p) + 1) }' "${file}")"
  test "$(printf '%s\n' "${got}" | grep -c . || true)" -le 1 || fail "step=buildconfig variant=${variant} field=${name} unique=false"
  test "${got}" = "\"${value}\";" || fail "step=buildconfig variant=${variant} field=${name} match=false"
  receipt "step=buildconfig variant=${variant} field=${name} match=true"
}
single_apk() { # single_apk <dir> <buildType>: universal split APK, else the unsplit APK
  local dir="$1" type="$2"
  mapfile -t apks < <(find "${dir}" -maxdepth 1 -type f \
    \( -name "app-free-universal-${type}.apk" -o -name "app-free-${type}.apk" \) | sort)
  test "${#apks[@]}" -eq 1 || fail "step=apk dir_type=${type} count=${#apks[@]}"
  printf '%s\n' "${apks[0]}"
}

# --- Step 4: explicit owned isolated Free debug assemble ---
gradle_run "${OWNED_ARGS[@]}" assembleFreeDebug || fail "step=owned_debug_assemble"
receipt "step=owned_debug_assemble ok=true"
buildconfig_text free debug "${WORK}/bc-free-debug.txt"
expect_field "${WORK}/bc-free-debug.txt" freeDebug SIGNAL_WS_URL "${OWNED_SIGNAL_URL}"
expect_field "${WORK}/bc-free-debug.txt" freeDebug STUN_URL "${OWNED_STUN_URL}"
expect_field "${WORK}/bc-free-debug.txt" freeDebug OWNED_TEST_PIN_HOST "${OWNED_HOST}"
expect_field "${WORK}/bc-free-debug.txt" freeDebug OWNED_TEST_PIN "${OWNED_PIN}"
DEBUG_APK="$(single_apk app/build/outputs/apk/free/debug debug)"
test "$("${AAPT2}" dump packagename "${DEBUG_APK}")" = "${FREE_PACKAGE}.devtest" \
  || fail "step=owned_debug_apk package_devtest=false"
unzip -p "${DEBUG_APK}" res/raw/owned_test_ca.pem | cmp -s - "${CA}" \
  || fail "step=owned_debug_apk ca_resource_identical=false"
"${AAPT2}" dump xmltree --file res/xml/network_security_config.xml "${DEBUG_APK}" > "${WORK}/nsc-debug.txt"
test "$(count_fixed "${PIN_B64}" "${WORK}/nsc-debug.txt")" = 1 || fail "step=owned_debug_apk nsc_owned_pin_count!=1"
receipt "step=owned_debug_apk package_devtest=true ca_resource_identical=true nsc_owned_pin_count=1"

# --- Step 5: normal Free release with ephemeral CI-only signing (never a personal key) ---
STORE="${WORK}/securecall-ci-only.jks"
STORE_PASS="$(openssl rand -hex 24)"
printf '::add-mask::%s\n' "${STORE_PASS}"
keytool -genkeypair -noprompt -keystore "${STORE}" -storepass "${STORE_PASS}" \
  -keypass "${STORE_PASS}" -alias securecall-ci -keyalg RSA -keysize 3072 -validity 2 \
  -dname "CN=CI Verification Only" > /dev/null 2>&1
SECURECALL_STORE_FILE="${STORE}" SECURECALL_STORE_PASSWORD="${STORE_PASS}" \
  SECURECALL_KEY_ALIAS=securecall-ci SECURECALL_KEY_PASSWORD="${STORE_PASS}" \
  gradle_run assembleFreeRelease || fail "step=release_assemble"
unset STORE_PASS
receipt "step=release_assemble ok=true signing=ephemeral_ci_only"
buildconfig_text free release "${WORK}/bc-free-release.txt"
expect_field "${WORK}/bc-free-release.txt" freeRelease SIGNAL_WS_URL "${PROD_SIGNAL_URL}"
expect_field "${WORK}/bc-free-release.txt" freeRelease STUN_URL "${PROD_STUN_URL}"
expect_field "${WORK}/bc-free-release.txt" freeRelease OWNED_TEST_PIN_HOST ""
expect_field "${WORK}/bc-free-release.txt" freeRelease OWNED_TEST_PIN ""
RELEASE_APK="$(single_apk app/build/outputs/apk/free/release release)"
"${APKSIGNER}" verify "${RELEASE_APK}" > /dev/null 2>&1 || fail "step=release_apk signature_verify=false"
test "$("${AAPT2}" dump packagename "${RELEASE_APK}")" = "${FREE_PACKAGE}" \
  || fail "step=release_apk package_exact=false"
"${AAPT2}" dump resources "${RELEASE_APK}" > "${WORK}/res-release.txt"
unzip -p "${RELEASE_APK}" > "${WORK}/release-entries.bin"
unzip -p "${RELEASE_APK}" 'classes*.dex' > "${WORK}/release-dex.bin"
# Unique random tokens of this run (CA body line, SPKI pin) and the full owned URL; no IP substring.
test "$(count_fixed 'owned_test_ca' "${WORK}/res-release.txt")" = 0 || fail "step=release_apk owned_ca_resource=true"
test "$(count_fixed "${CA_TOKEN}" "${WORK}/release-entries.bin")" = 0 || fail "step=release_apk generated_ca_bytes=true"
test "$(count_fixed "${PIN_B64}" "${WORK}/release-entries.bin")" = 0 || fail "step=release_apk generated_pin=true"
test "$(count_fixed "${OWNED_SIGNAL_URL}" "${WORK}/release-dex.bin")" = 0 || fail "step=release_apk owned_url=true"
prod_hits="$(count_fixed "${PROD_SIGNAL_URL}" "${WORK}/release-dex.bin")"
test "${prod_hits}" -ge 1 || fail "step=release_apk production_url_present=false"
receipt "step=release_apk signature_verify=true package_exact=true owned_ca_resource=0 generated_ca_bytes=0 generated_pin=0 owned_url=0 production_url_present=true"
receipt "result=PASS"
