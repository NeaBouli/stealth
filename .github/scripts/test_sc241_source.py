#!/usr/bin/env python3
"""Pure SC241 guards/parsers and narrow source contracts. Never invokes Android/network."""

import re
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
BASE = "12756b9591db809f937e6443b3d1c847a622b603"
SCRIPTS = ROOT / ".github/scripts"
TEST_PATH = "client_android/app/src/androidTest/java/com/securecall/app/ContactLookupPreservationInstrumentedTest.kt"
MATRIX_PATH = "client_android/app/src/androidTest/java/com/securecall/app/ContactVisualMatrix.kt"
PRODUCTION = "client_android/app/src/main/java/com/securecall/app/ui/ContactsFragment.kt"
PURE = {
    "sc241_host_guard", "sc241_one_emulator", "sc241_fresh_guard", "sc241_policy_guard",
    "sc241_read_drop_count", "sc241_positive_delta", "sc241_probe_delta",
}


def git(*args):
    return subprocess.check_output(["git", *args], cwd=ROOT)


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
        for invalid in ["", "unknown", "USER PID", "x" * 262145]:
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
        allowed = {TEST_PATH, MATRIX_PATH, "docs/architecture/MAP.md",
                   ".github/scripts/run-android-smoke.sh", ".github/scripts/sc241-guest-isolation.sh",
                   ".github/scripts/test_sc241_source.py",
                   ".fleet/reports/SC241-NORMAL-LIFECYCLE-IMPLEMENT-20261116.md"}
        changed = set(git("diff", "--name-only", BASE).decode().splitlines())
        changed.update(git("ls-files", "--others", "--exclude-standard").decode().splitlines())
        self.assertTrue(changed <= allowed, "Foreign/out-of-scope changes require parent reconciliation")
        original_map = git("show", f"{BASE}:docs/architecture/MAP.md")
        self.assertTrue((ROOT / "docs/architecture/MAP.md").read_bytes().startswith(original_map))


if __name__ == "__main__":
    unittest.main()
