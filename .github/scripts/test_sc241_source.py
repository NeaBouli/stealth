#!/usr/bin/env python3
"""Pure SC241 guards/parsers and narrow source contracts. Never invokes Android/network."""

import re
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE = "12756b9591db809f937e6443b3d1c847a622b603"
SCRIPTS = ROOT / ".github/scripts"
TEST_PATH = "client_android/app/src/androidTest/java/com/securecall/app/ContactLookupPreservationInstrumentedTest.kt"
MATRIX_PATH = "client_android/app/src/androidTest/java/com/securecall/app/ContactVisualMatrix.kt"
PRODUCTION = "client_android/app/src/main/java/com/securecall/app/ui/ContactsFragment.kt"
# Source-scope comparison ONLY. BASE above stays the immutable SC241 regression-contract base.
SOURCE_SCOPE_BASE = "2e98fe370a2e007642270e8ac59717bcbdb52692"
INTEGRATED_PATHS = frozenset({
    ".github/scripts/collect_contact_visual.py", ".github/scripts/run-android-smoke.sh",
    ".github/scripts/sc241-guest-isolation.sh", ".github/scripts/test_collect_contact_visual.py",
    ".github/scripts/test_sc241_source.py", ".github/workflows/android-instrumentation.yml",
    ".gitignore", "BRIDGE.md", "docs/architecture/MAP.md", PRODUCTION, TEST_PATH, MATRIX_PATH,
    "client_android/app/src/androidTest/java/com/securecall/app/ContactRootObservation.kt",
})
LOCAL_REPORTS = frozenset({
    ".fleet/reports/SC242-INTEGRATION-PLAN-20261119.md", ".fleet/reports/SC242-FINAL-SOURCE-20261119.md",
    ".fleet/reports/SC241-NORMAL-LIFECYCLE-IMPLEMENT-20261116.md",
    ".fleet/reports/SC242-FIVE-ISOLATED-20261119.md",
    ".fleet/reports/SC241-PREAPP-STAGES-20261119.md",
})
MAIN_TEST_PATH = "client_android/app/src/androidTest/java/com/securecall/app/MainActivityInstrumentedTest.java"
BASE_MAP_SECTION = "# Narrow trace: contact lookup preserves saved identities (2026-11-16)"
PURE = {
    "sc241_host_guard", "sc241_one_emulator", "sc241_fresh_guard", "sc241_policy_guard",
    "sc241_read_drop_count", "sc241_positive_delta", "sc241_probe_delta",
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


def scope_violations(tracked, untracked):
    return (set(tracked) - INTEGRATED_PATHS) | (set(untracked) - LOCAL_REPORTS)


def pure(name, *args, stdin=""):
    if name not in PURE:
        raise ValueError("Only pure function entrypoints are allowed")
    return subprocess.run(
        ["bash", "-euo", "pipefail", "-c", 'source "$1"; shift; "$@"',
         "sc241-pure", str(SCRIPTS / "sc241-guest-isolation.sh"), name, *args],
        input=stdin, text=True, capture_output=True, check=False, timeout=5,
    )


def method(source, name):
    found = re.search(
        rf"(?ms)^    (?:private )?fun {name}\(.*?"
        r"(?=^    (?:@|(?:private )?(?:fun |class |data class |companion object))|^\}|\Z)",
        source,
    )
    if found is None:
        raise AssertionError("Missing source contract method")
    return found.group()


class IsolationParsers(unittest.TestCase):
    def test_manual_first_attempt_exact_clean_host_guard(self):
        args = ["true", "github-hosted", "workflow_dispatch", "1", "a" * 40,
                "a" * 40, "b" * 40, "",
                "com.securecall.app.free.test/androidx.test.runner.AndroidJUnitRunner"]
        self.assertEqual(0, pure("sc241_host_guard", *args).returncode)
        for index, value in [(0, "false"), (1, "self-hosted"), (2, "pull_request"), (3, "2"),
                             (4, "c" * 40), (5, "invalid"), (6, "invalid"), (7, " M source"),
                             (8, "com.securecall.app.premium.test/runner")]:
            rejected = args.copy()
            rejected[index] = value
            self.assertNotEqual(0, pure("sc241_host_guard", *rejected).returncode)

    def test_exactly_one_online_emulator(self):
        header = "List of devices attached\n"
        valid = pure("sc241_one_emulator", stdin=header + "emulator-5554\tdevice\n\n")
        self.assertEqual((0, "emulator-5554\n"), (valid.returncode, valid.stdout))
        for body in ["", "emulator-5554\toffline\n", "physical\tdevice\n",
                     "emulator-5554\tdevice\nemulator-5556\tdevice\n",
                     "emulator-5554\tdevice\nphysical\tdevice\n",
                     "emulator-5554\tdevice product:synthetic\n"]:
            self.assertNotEqual(0, pure("sc241_one_emulator", stdin=header + body).returncode)
        self.assertNotEqual(0, pure("sc241_one_emulator", stdin="emulator-5554\tdevice\n").returncode)

    def test_pre_app_fresh_packages_and_processes(self):
        packages = "package:android\npackage:com.android.shell"
        processes = "USER PID PPID VSZ RSS WCHAN ADDR S NAME\nroot 1 0 0 0 0 0 S init"
        self.assertEqual(0, pure("sc241_fresh_guard", packages, processes).returncode)
        for extra in ["com.securecall.app.free", "com.securecall.app.free.test", "com.securecall.app.premium"]:
            self.assertNotEqual(0, pure("sc241_fresh_guard", packages + "\npackage:" + extra, processes).returncode)
            self.assertNotEqual(0, pure("sc241_fresh_guard", packages, processes + "\nu0 2 1 " + extra + ":worker").returncode)
        for invalid in ["", "unknown", "USER PID", "USER PID PPID VSZ RSS WCHAN ADDR S NAME",
                        "USER PID PPID VSZ RSS WCHAN ADDR S NAME\nnot-a-process-row", "x" * 262145]:
            self.assertNotEqual(0, pure("sc241_fresh_guard", packages, invalid).returncode)

    def test_zero_allow_exceptions_for_both_families(self):
        drop = "-P OUTPUT DROP"
        self.assertEqual(0, pure("sc241_policy_guard", drop, drop).returncode)
        for policy in ["-P OUTPUT ACCEPT", drop + "\n-A OUTPUT -j ACCEPT", drop + "\n-A OUTPUT -j DROP", ""]:
            self.assertNotEqual(0, pure("sc241_policy_guard", policy, drop).returncode)
            self.assertNotEqual(0, pure("sc241_policy_guard", drop, policy).returncode)

    def test_actual_counter_parser_bounds_duplicates_and_crlf(self):
        header = "Chain OUTPUT (policy DROP 000000009 packets, 123 bytes)"
        parsed = pure("sc241_read_drop_count", stdin=header + "\r\n pkts bytes target prot opt in out source destination\n")
        self.assertEqual((0, "9\n"), (parsed.returncode, parsed.stdout))
        for invalid in ["", header + "\n" + header, header.replace("DROP", "ACCEPT"),
                        header.replace("000000009", "1000000000"), header.replace("123", "1000000000"),
                        header.replace("000000009", "-1"), header + " trailing"]:
            self.assertNotEqual(0, pure("sc241_read_drop_count", stdin=invalid).returncode)

    def test_failed_probe_and_positive_delta_not_nonzero_counter(self):
        valid = pure("sc241_probe_delta", "1", "9", "11")
        self.assertEqual((0, "2\n"), (valid.returncode, valid.stdout))
        for args in [("1", "9", "9"), ("1", "10", "9"), ("0", "9", "11"),
                     ("124", "9", "11"), ("127", "9", "11"), ("2", "9", "11"),
                     ("1", "0", "1000000000"), ("1", "x", "11")]:
            self.assertNotEqual(0, pure("sc241_probe_delta", *args).returncode)


class SourceContracts(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.test = (ROOT / TEST_PATH).read_text()
        cls.matrix = (ROOT / MATRIX_PATH).read_text()
        cls.smoke = (SCRIPTS / "run-android-smoke.sh").read_text()
        cls.isolation = (SCRIPTS / "sc241-guest-isolation.sh").read_text()

    def test_seven_original_regression_bodies_and_manual_fixture_byte_identical(self):
        baseline = git("show", f"{BASE}:{TEST_PATH}").decode()
        names = re.findall(r"@Test\s+fun (\w+)\(", baseline)
        self.assertEqual(8, len(names))
        for name in names:
            if name != "syntheticContactWindowMatrix":
                self.assertEqual(method(baseline, name), method(self.test, name), name)
        for name in ["launchContactHost", "seed", "finalizeResults", "cleanUpContactFixture"]:
            self.assertEqual(method(baseline, name), method(self.test, name), name)
        self.assertEqual(git("show", f"{BASE}:{PRODUCTION}"), (ROOT / PRODUCTION).read_bytes())

    def test_actual_loader_name_is_production_source_unique(self):
        hits = []
        for path in (ROOT / "client_android/app/src").rglob("*"):
            if path.suffix in {".java", ".kt"} and "androidTest" not in path.parts and "test" not in path.parts:
                if '"ContactsLoader"' in path.read_text():
                    hits.append(path.relative_to(ROOT).as_posix())
        self.assertEqual([PRODUCTION], hits)
        self.assertEqual(1, (ROOT / PRODUCTION).read_text().count('"ContactsLoader"'))
        barrier = method(self.test, "awaitVisualLoaders")
        for token in ["Looper.myLooper() != Looper.getMainLooper()", "loader.join(remaining)",
                      "!loader.isAlive", "waitForIdleSync()", "contactLoaders().isEmpty()"]:
            self.assertIn(token, barrier)
        self.assertNotIn("Thread.sleep", self.test + self.matrix)
        self.assertNotIn(".recreate(", self.test + self.matrix)

    def test_normal_visual_lifecycle_seed_order_and_current_instance(self):
        launch = method(self.test, "launchVisualContactHost")
        self.assertIn(".replace(R.id.settingsContainer, ContactsFragment())", launch)
        self.assertNotIn("setMaxLifecycle", launch)
        self.assertNotIn("onCreateView", launch)
        self.assertIn("if (!isVisual) launchContactHost()", self.test)
        self.assertLess(self.matrix.index("diagnose(Phase.STORAGE_SEED)"), self.matrix.index("diagnose(Phase.HOST_LAUNCH)"))
        current = method(self.test, "withVisualHost")
        self.assertIn("manager.findFragmentById(R.id.settingsContainer) as ContactsFragment", current)
        self.assertIn("Lifecycle.State.RESUMED", current)
        self.assertIn("PackageManager.PERMISSION_GRANTED", self.test)
        self.assertIn("checkNotNull(cursor).count", self.test)
        self.assertNotRegex(self.test + self.matrix, r"(?:grant|revoke)RuntimePermission|pm (?:grant|revoke)")

    def test_preinstall_hold_shutdown_receipt_and_no_host_firewall(self):
        smoke = self.smoke
        self.assertLess(smoke.index("trap sc241_visual_exit EXIT"), smoke.index("sc241_prepare_isolation ||"))
        self.assertLess(smoke.index("sc241_prepare_isolation ||"), smoke.index("timeout 600 adb install"))
        self.assertIn("trap 'exit 2' HUP INT TERM", smoke)
        self.assertIn("sc241_shutdown_guest", smoke)
        self.assertIn("securecallVisualPreAppIsolation true", smoke)
        self.assertIn("securecallVisualPreAppIsolation", self.matrix)
        for line in self.isolation.splitlines():
            if "iptables -w" in line or "ip6tables -w" in line:
                self.assertIn("sc241_adb shell ", line)
        self.assertNotIn("OUTPUT ACCEPT", self.isolation)
        self.assertNotIn("sudo", self.isolation)
        self.assertIn("wait-for-disconnect", self.isolation)
        self.assertIn("sc241_ipv6_probe=false", self.isolation)
        self.assertIn('sc241_probe_delta "$v6probe"', self.isolation)
        self.assertNotRegex(smoke + self.isolation, r"(?:export\s+)?GITHUB_ACTIONS=")
        self.assertNotIn("sc224-fixture.mjs", smoke + self.isolation)

    def test_api24_process_form(self):
        self.assertIn('if [[ "$sc241_api" == 24 ]]; then\n'
                      '    processes="$(sc241_adb shell ps)" || { sc241_reject PROCESSES_READ_API24; return 1; }\n'
                      '  else\n'
                      '    processes="$(sc241_adb shell ps -A)" || { sc241_reject PROCESSES_READ; return 1; }\n'
                      '  fi', self.isolation)

    def test_sc242_focus_selector_is_exact_five_existing_methods(self):
        import importlib.util
        spec = importlib.util.spec_from_file_location("collector", SCRIPTS / "collect_contact_visual.py")
        collector = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(collector)
        main_source = (ROOT / MAIN_TEST_PATH).read_text()
        self.assertIn("package com.securecall.app;", main_source)
        self.assertIn("public class MainActivityInstrumentedTest", main_source)
        for klass, test in collector.FOCUS_CASES:
            if klass == collector.MAIN_CLASS:
                self.assertRegex(main_source, rf"@Test\s+public void {test}\(\)")
            else:
                self.assertEqual(klass, "com.securecall.app.ContactLookupPreservationInstrumentedTest")
                self.assertRegex(self.test, rf"@Test\s+fun {test}\(\)")
        selector = re.findall(r"^  focus_cases='([^']+)'$", self.smoke, re.MULTILINE)
        self.assertEqual(selector, [collector.FOCUS_SELECTOR])
        focus = self.smoke[self.smoke.index('if [[ "$focus" == true ]]; then\n  # Exactly'):]
        focus = focus[:focus.index("\nfi\n")]
        self.assertIn('-e class "$focus_cases"', focus)
        self.assertNotIn("notClass", focus)
        self.assertNotIn("visual_case", focus)
        self.assertNotIn("tee ", focus)
        self.assertIn('2>"$focus_tmp/stderr.txt"', focus)
        self.assertIn("--focus-probe", focus)
        self.assertEqual(focus.count("sc241_verify_isolation"), 2)
        self.assertIn('exit "$focus_status"', focus)

    def test_sc242_focus_reuses_pre_app_isolation_and_exit_trap(self):
        smoke = self.smoke
        self.assertIn('if [[ "$visual" == true || "$focus" == true ]]; then', smoke)
        self.assertLess(smoke.index("sc241_prepare_isolation ||"), smoke.index("SC242_FOCUS_REJECTED:API24_REQUIRED"))
        self.assertLess(smoke.index("SC242_FOCUS_REJECTED:API24_REQUIRED"), smoke.index("timeout 600 adb install"))
        self.assertLess(smoke.index("timeout 600 adb install"), smoke.index("focus_tmp=\"$(mktemp -d)\""))
        self.assertLess(smoke.index("focus_tmp=\"$(mktemp -d)\""), smoke.index("-e notClass"))
        self.assertEqual(1, smoke.count("trap sc241_visual_exit EXIT"))
        exit_fn = smoke[smoke.index("sc241_visual_exit() {"):smoke.index("trap sc241_visual_exit EXIT")]
        self.assertLess(exit_fn.index("sc241_shutdown_guest"), exit_fn.index('rm -f "$focus_tmp/result.txt"'))
        self.assertIn('if [[ "$primary" == 0 && "$cleanup_failed" == true ]]; then primary=2; fi', exit_fn)
        self.assertIn('[[ "$visual" == false && "$live_pin" == false && "$root_probe" == false ]] || exit 2', smoke)
        workflow = (ROOT / ".github/workflows/android-instrumentation.yml").read_text()
        self.assertIn("      contact_focus_probe:\n", workflow)
        self.assertIn("contact_focus_probe:\n        description:", workflow)
        block = workflow[workflow.index("      contact_focus_probe:"):]
        self.assertIn("type: boolean\n        default: false", block[:300])
        self.assertIn("(inputs.contact_root_probe || inputs.contact_focus_probe) && fromJSON('[24]')", workflow)
        self.assertIn("inputs.contact_focus_probe) && 'securecallContactFocusProbe=true'", workflow)
        self.assertIn("force-avd-creation: true", workflow)
        self.assertIn('export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.0.12077973"', workflow)

    def test_original_geometry_privacy_caps_and_failure_preservation(self):
        baseline = git("show", f"{BASE}:{MATRIX_PATH}").decode()
        for name in ["assertWindow", "measureRow", "bounds", "capture", "expectedNames", "shell"]:
            self.assertEqual(method(baseline, name), method(self.matrix, name), name)
        for token in ["private const val PNG_LIMIT = 4 * 1024 * 1024",
                      "private const val API_LIMIT = 8 * 1024 * 1024",
                      "private const val METADATA_LIMIT = 128 * 1024", "failure!!.addSuppressed(error)",
                      "cleanup(primaryFailure,"]:
            self.assertIn(token, self.matrix)
        self.assertIn("ContactVisualMatrix.cleanup(visualFailure,", self.test)
        self.assertIn("primaryFailure = failure", self.matrix)

    def test_exact_source_whitelist(self):
        subprocess.check_call(["git", "merge-base", "--is-ancestor", SOURCE_SCOPE_BASE, "HEAD"], cwd=ROOT)
        tracked = git("diff", "--name-only", SOURCE_SCOPE_BASE).decode().splitlines()
        untracked = git("ls-files", "--others", "--exclude-standard").decode().splitlines()
        self.assertEqual(scope_violations(tracked, untracked), set(),
                         "Foreign/out-of-scope changes require parent reconciliation")
        scope_map = git("show", f"{SOURCE_SCOPE_BASE}:docs/architecture/MAP.md")
        current_map = (ROOT / "docs/architecture/MAP.md").read_bytes()
        self.assertTrue(current_map.startswith(scope_map))
        original_map = git("show", f"{BASE}:docs/architecture/MAP.md")
        own = original_map[original_map.index(BASE_MAP_SECTION.encode()):]
        self.assertIn(own, current_map[len(scope_map):])

    def test_scope_validator_accepts_integration_and_rejects_extras(self):
        self.assertEqual(BASE, "12756b9591db809f937e6443b3d1c847a622b603")
        self.assertNotEqual(SOURCE_SCOPE_BASE, BASE)
        self.assertEqual(len(INTEGRATED_PATHS), 13)
        self.assertEqual(scope_violations(INTEGRATED_PATHS, LOCAL_REPORTS), set())
        for path in ["client_android/app/src/main/java/com/securecall/app/ui/SettingsFragment.kt",
                     "native/securecall_crypto/src/lib.rs", ".env", "secrets/keystore.jks"]:
            self.assertEqual(scope_violations(INTEGRATED_PATHS | {path}, ()), {path})
            self.assertEqual(scope_violations((), {path}), {path})
        self.assertEqual(scope_violations((), {".fleet/reports/OTHER.md"}), {".fleet/reports/OTHER.md"})

# Synthetic-only harness for the side-effectful prepare/verify helpers. Every external
# command is a shell function stub and PATH points nowhere, so no adb/timeout/ping binary,
# device, network, root or emulator can be reached. Deliberately NOT part of PURE.
STUB = r"""
PATH=/nonexistent-sc241-stub
head=h tree=t
timeout() { shift; "$@"; }
adb() {
  local key="$*" n=0 line
  [[ "$key" == "-s emulator-5554 "* ]] && key="${key#-s emulator-5554 }"
  printf '%s\n' "$key" >> "$STUB_DIR/calls"
  printf 'x\n' >> "$STUB_DIR/n_${key//[^A-Za-z0-9]/_}"
  while IFS= read -r line; do n=$((n + 1)); done < "$STUB_DIR/n_${key//[^A-Za-z0-9]/_}"
  if [[ "$key" == "${FAIL_KEY:-}" && "$n" == "${FAIL_NTH:-1}" ]]; then return 1; fi
  if [[ "$key" == "${OVR_KEY:-}" && "$n" == "${OVR_NTH:-1}" ]]; then printf '%b' "$OVR_OUT"; return 0; fi
  case "$key" in
    devices) printf 'List of devices attached\nemulator-5554\tdevice\n' ;;
    'shell getprop ro.kernel.qemu') printf '1\r\n' ;;
    'shell getprop ro.hardware') printf 'ranchu\n' ;;
    'shell getprop ro.build.version.sdk') printf '%s\n' "${API:-36}" ;;
    'shell pm list packages') printf 'package:android\n' ;;
    'shell ps' | 'shell ps -A') printf 'USER PID PPID VSZ RSS WCHAN ADDR S NAME\nroot 1 0 0 0 0 0 S init\n' ;;
    'shell id -u') printf '0\n' ;;
    'shell iptables -w -S OUTPUT' | 'shell ip6tables -w -S OUTPUT') printf -- '-P OUTPUT DROP\r\n' ;;
    'shell iptables -w -L OUTPUT -v -x -n')
      if [[ "$n" == 1 ]]; then c=5; else c=7; fi
      printf 'Chain OUTPUT (policy DROP %s packets, 0 bytes)\n' "$c" ;;
    'shell ip6tables -w -L OUTPUT -v -x -n')
      if [[ "$n" == 1 ]]; then c=5; else c="${V6AFTER:-6}"; fi
      printf 'Chain OUTPUT (policy DROP %s packets, 0 bytes)\n' "$c" ;;
    *'ping -6 '*) return "${PING6_RC:-1}" ;;
    *ping*) return "${PING_RC:-1}" ;;
  esac
}
"""
# (enum, env) -> each triggers exactly one existing prepare failure arm.
PREP = "shell ip6tables -w -L OUTPUT -v -x -n"
V4L = "shell iptables -w -L OUTPUT -v -x -n"
ARMS = [
    ("DEVICES_READ", {"FAIL_KEY": "devices"}),
    ("SERIAL_SELECT", {"OVR_KEY": "devices", "OVR_OUT": "List of devices attached\\nphysical\\tdevice\\n"}),
    ("QEMU_READ", {"FAIL_KEY": "shell getprop ro.kernel.qemu"}),
    ("HARDWARE_READ", {"FAIL_KEY": "shell getprop ro.hardware"}),
    ("API_READ", {"FAIL_KEY": "shell getprop ro.build.version.sdk"}),
    ("TARGET_PREDICATE", {"API": "35"}),
    ("PACKAGES_READ", {"FAIL_KEY": "shell pm list packages"}),
    ("PROCESSES_READ_API24", {"API": "24", "FAIL_KEY": "shell ps"}),
    ("PROCESSES_READ", {"FAIL_KEY": "shell ps -A"}),
    ("FRESH_PREDICATE", {"OVR_KEY": "shell pm list packages", "OVR_OUT": "package:com.securecall.app.free\\n"}),
    ("ROOT_RESTART", {"FAIL_KEY": "root"}),
    ("ROOT_WAIT", {"FAIL_KEY": "wait-for-device"}),
    ("UID_READ", {"FAIL_KEY": "shell id -u"}),
    ("UID_PREDICATE", {"OVR_KEY": "shell id -u", "OVR_OUT": "2000\\n"}),
    ("IPV4_POLICY_SET", {"FAIL_KEY": "shell iptables -w -P OUTPUT DROP"}),
    ("IPV6_POLICY_SET", {"FAIL_KEY": "shell ip6tables -w -P OUTPUT DROP"}),
    ("IPV4_FLUSH", {"FAIL_KEY": "shell iptables -w -F OUTPUT"}),
    ("IPV6_FLUSH", {"FAIL_KEY": "shell ip6tables -w -F OUTPUT"}),
    ("VERIFY_PRE_PROBE_DEVICES_READ", {"FAIL_KEY": "devices", "FAIL_NTH": "2"}),
    ("VERIFY_PRE_PROBE_SERIAL", {"OVR_KEY": "devices", "OVR_NTH": "2", "OVR_OUT": "List of devices attached\\n"}),
    ("VERIFY_PRE_PROBE_IPV4_READ", {"FAIL_KEY": "shell iptables -w -S OUTPUT"}),
    ("VERIFY_PRE_PROBE_IPV6_READ", {"FAIL_KEY": "shell ip6tables -w -S OUTPUT"}),
    ("VERIFY_PRE_PROBE_POLICY", {"OVR_KEY": "shell ip6tables -w -S OUTPUT", "OVR_OUT": "-P OUTPUT ACCEPT\\n"}),
    ("IPV4_BEFORE_READ", {"FAIL_KEY": V4L}),
    ("IPV6_BEFORE_READ", {"FAIL_KEY": PREP}),
    ("IPV4_BEFORE_PARSE", {"OVR_KEY": V4L, "OVR_OUT": "garbage\\n"}),
    ("IPV6_BEFORE_PARSE", {"OVR_KEY": PREP, "OVR_OUT": "Chain OUTPUT (policy ACCEPT 1 packets, 0 bytes)\\n"}),
    ("IPV4_AFTER_READ", {"FAIL_KEY": V4L, "FAIL_NTH": "2"}),
    ("IPV4_AFTER_PARSE", {"OVR_KEY": V4L, "OVR_NTH": "2", "OVR_OUT": "garbage\\n"}),
    ("IPV4_PROBE_DELTA", {"PING_RC": "0"}),
    ("IPV6_PROBE_SUCCEEDED", {"PING6_RC": "0"}),
    ("IPV6_AFTER_READ", {"FAIL_KEY": PREP, "FAIL_NTH": "2"}),
    ("IPV6_AFTER_PARSE", {"OVR_KEY": PREP, "OVR_NTH": "2", "OVR_OUT": "garbage\\n"}),
    ("IPV6_COUNTER_DECREASE", {"V6AFTER": "4"}),
    ("VERIFY_POST_PROBE_DEVICES_READ", {"FAIL_KEY": "devices", "FAIL_NTH": "3"}),
    ("VERIFY_POST_PROBE_SERIAL", {"OVR_KEY": "devices", "OVR_NTH": "3", "OVR_OUT": "List of devices attached\\n"}),
    ("VERIFY_POST_PROBE_IPV4_READ", {"FAIL_KEY": "shell iptables -w -S OUTPUT", "FAIL_NTH": "2"}),
    ("VERIFY_POST_PROBE_IPV6_READ", {"FAIL_KEY": "shell ip6tables -w -S OUTPUT", "FAIL_NTH": "2"}),
    ("VERIFY_POST_PROBE_POLICY", {"OVR_KEY": "shell iptables -w -S OUTPUT", "OVR_NTH": "2", "OVR_OUT": "-P OUTPUT ACCEPT\\n"}),
]


def stubbed(script, call, env):
    with tempfile.TemporaryDirectory() as tmp:
        stub = Path(tmp) / "stub.sh"
        stub.write_text(STUB)
        # Stubs are sourced before any helper call; env carries no inherited PATH.
        result = subprocess.run(
            ["/bin/bash", "-c", 'source "$1"; source "$2"; ' + call, "sc241-stub", script, str(stub)],
            input="", text=True, capture_output=True, check=False, timeout=10,
            env={"STUB_DIR": tmp, **env},
        )
        calls = (Path(tmp) / "calls").read_text() if (Path(tmp) / "calls").exists() else ""
    return result, calls


class PrepareStageLabels(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp = tempfile.TemporaryDirectory()
        cls.base = Path(cls.tmp.name) / "base.sh"
        cls.base.write_bytes(git("show", "3c0e963677b73d55c6945b35a707925862852ada:"
                                 ".github/scripts/sc241-guest-isolation.sh"))
        cls.current = SCRIPTS / "sc241-guest-isolation.sh"

    @classmethod
    def tearDownClass(cls):
        cls.tmp.cleanup()

    def test_each_existing_arm_emits_one_fixed_label_and_preserves_baseline(self):
        for enum, env in ARMS:
            with self.subTest(enum):
                new, new_calls = stubbed(self.current, "sc241_prepare_isolation", env)
                old, old_calls = stubbed(self.base, "sc241_prepare_isolation", env)
                self.assertEqual((1, ""), (old.returncode, old.stderr))
                self.assertEqual(old.returncode, new.returncode)
                self.assertEqual(old.stdout, new.stdout)
                self.assertEqual("", new.stdout)
                self.assertEqual(old_calls, new_calls)
                self.assertEqual(f"SC241_PREPARE_REJECTED:{enum}\n", new.stderr)

    def test_success_and_ipv6_policy_only_fallback_stay_accepted_and_silent(self):
        for env, v6 in [({}, "ipv6_probe=true ipv6_delta=1"), ({"V6AFTER": "5"}, "ipv6_probe=false ipv6_delta=0")]:
            new, new_calls = stubbed(self.current, "sc241_prepare_isolation", env)
            old, old_calls = stubbed(self.base, "sc241_prepare_isolation", env)
            self.assertEqual((0, ""), (new.returncode, new.stderr))
            self.assertEqual((old.stdout, old_calls), (new.stdout, new_calls))
            self.assertIn(v6 + " held=true", new.stdout)

    def test_argless_verify_callers_keep_silent_stderr(self):
        for env in [{"FAIL_KEY": "devices"}, {"FAIL_KEY": "shell iptables -w -S OUTPUT"},
                    {"OVR_KEY": "shell ip6tables -w -S OUTPUT", "OVR_OUT": "-P OUTPUT ACCEPT\\n"}]:
            call = "sc241_serial=emulator-5554; sc241_verify_isolation"
            new, _ = stubbed(self.current, call, env)
            old, _ = stubbed(self.base, call, env)
            self.assertEqual((old.returncode, old.stdout, old.stderr), (new.returncode, new.stdout, new.stderr))
            self.assertEqual((1, ""), (new.returncode, new.stderr))

    def test_labels_are_unique_literal_enums_and_cover_every_prepare_arm(self):
        source = self.current.read_text()
        prepare = source[source.index("sc241_prepare_isolation() {"):source.index("sc241_shutdown_guest() {")]
        literal = re.findall(r"sc241_reject ([A-Z0-9_]+); return 1; }", prepare)
        self.assertEqual(len(literal), len(set(literal)))
        self.assertNotIn("|| return 1\n", prepare.replace("sc241_verify_isolation PRE_PROBE || return 1\n", "")
                         .replace("sc241_verify_isolation POST_PROBE || return 1\n", ""))
        verify = re.findall(r'sc241_verify_reject "\$stage" ([A-Z0-9_]+)', source)
        self.assertEqual(len(verify), len(set(verify)))
        expected = set(literal) | {f"VERIFY_{s}_{v}" for s in ("PRE_PROBE", "POST_PROBE") for v in verify}
        self.assertEqual(expected, {enum for enum, _ in ARMS})
        self.assertEqual(len(ARMS), len(expected))
        self.assertEqual(1, source.count("SC241_PREPARE_REJECTED:"))
        self.assertNotIn("SC241_PREPARE_REJECTED", (SCRIPTS / "run-android-smoke.sh").read_text())


if __name__ == "__main__":
    unittest.main()
