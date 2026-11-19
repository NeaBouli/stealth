"""Pure synthetic parser/PNG/cap tests. Never invokes adb or fabricates CI environment."""

import binascii
import importlib.util
import json
from pathlib import Path
import re
import struct
import sys
import unittest
import zlib

SPEC = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect_contact_visual.py"))
collector = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(collector)
HEAD = "a" * 40
TREE = "b" * 40


def result(codes=(1, 0), count=1, terminal=-1):
    lines = []
    for code in codes:
        lines.extend((f"INSTRUMENTATION_STATUS: class={collector.CASE[0]}",
                      f"INSTRUMENTATION_STATUS: test={collector.CASE[1]}",
                      f"INSTRUMENTATION_STATUS: numtests={count}", f"INSTRUMENTATION_STATUS_CODE: {code}"))
    lines.extend(("OK (1 test)" if codes == (1, 0) else "FAILURES!!!", f"INSTRUMENTATION_CODE: {terminal}"))
    return "\n".join(lines).encode()


def diagnostic_block(phase="DISPLAY_RESTORE", failure="ASSERTION", extra=""):
    return (f"INSTRUMENTATION_STATUS: sc241_phase={phase}\n"
            f"INSTRUMENTATION_STATUS: sc241_failure={failure}\n{extra}"
            "INSTRUMENTATION_STATUS_CODE: 2\n").encode()


def diagnosed_result(block=None, codes=(1, -2)):
    marker = b"INSTRUMENTATION_STATUS_CODE: 1\n"
    return result(codes).replace(marker, marker + (diagnostic_block() if block is None else block), 1)


def metadata():
    return {"schema": 1, "captureMode": "uiAutomation-window", "head": HEAD, "tree": TREE,
            "api": 24, "status": "passed", "restored": True, "violations": 0,
            "captures": [{"name": name, "width": w, "height": h, "fontScale": font,
                          "state": state, "densityDpi": 160, "rowCount": 20, "rowsChecked": 20,
                          "minTargetPx": 48, "minRowHeightPx": 64, "maxTextLines": 3, "violations": 0,
                          "errors": {},
                          "scrollReachedEnd": True, "touchScroll": True, "adapterSame": True, "savedDataPreserved": True,
                          "windowFocused": True, "pixelColors": 32}
                         for name, (w, h, font, state) in collector.EXPECTED.items()]}


def chunk(kind, data):
    return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", binascii.crc32(kind + data) & 0xffffffff)


def image(width=320, height=676, extra=b"", raw=None):
    pixels = raw if raw is not None else (b"\0" + b"\xff\0\0\xff" * width) * height
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0))
            + extra + chunk(b"IDAT", zlib.compress(pixels)) + chunk(b"IEND", b""))


class ProtocolTests(unittest.TestCase):
    def test_original_seven_must_each_really_pass_before_visual_export(self):
        ordinary = b"\n".join(result().replace(collector.CASE[1].encode(), name.encode())
                                for name in sorted(collector.ORIGINAL_CASES))
        collector.original_cases(ordinary)
        for invalid in (b"", ordinary + result(), ordinary.replace(b"STATUS_CODE: 0", b"STATUS_CODE: -4", 1)):
            with self.assertRaises(collector.GateError):
                collector.original_cases(invalid)

    def test_requires_exact_case_start_and_code_zero(self):
        self.assertEqual("passed", collector.protocol(result()))
        self.assertEqual("assertion_failed", collector.protocol(result((1, -2))))

    def test_zero_skips_duplicates_and_startup_fail_closed(self):
        for data in (result((), 0), result((1, -3)), result((1, -4)), result((1, -1)),
                     result((1, 0, 0)), result(count=0), result(terminal=0), b"OK (1 test)",
                     result().replace(collector.CASE[1].encode(), b"otherTest")):
            with self.subTest(data=data[:40]), self.assertRaises(collector.GateError):
                collector.protocol(data)


class MetadataTests(unittest.TestCase):
    def validate(self, value, outcome="passed"):
        return collector.matrix(json.dumps(value).encode(), HEAD, TREE, 24, outcome)

    def test_exact_sixteen_state_window_matrix(self):
        self.assertEqual(16, len(self.validate(metadata())["captures"]))

    def test_layout_failure_can_be_exported_but_never_qualifies(self):
        value = metadata()
        value.update(status="failed", violations=1)
        value["captures"][0]["violations"] = 1
        value["captures"][0]["errors"] = {"text_width": 1}
        self.validate(value, "assertion_failed")
        with self.assertRaises(collector.GateError):
            self.validate(value)

    def test_missing_duplicate_unexpected_and_private_fields_rejected(self):
        mutations = [lambda v: v["captures"].pop(),
                     lambda v: v["captures"].__setitem__(0, v["captures"][1]),
                     lambda v: v.update(deviceIdentity="not-exportable"),
                     lambda v: v["captures"][0].update(name="../../preferences.xml"),
                     lambda v: v["captures"][0].update(rawText="not-exportable")]
        for mutate in mutations:
            value = metadata()
            mutate(value)
            with self.assertRaises(collector.GateError):
                self.validate(value)

    def test_wrong_dimensions_font_source_mode_restore_and_invariants_rejected(self):
        mutations = [lambda v: v.update(head="c" * 40), lambda v: v.update(api=36),
                     lambda v: v.update(restored=False), lambda v: v.update(captureMode="offscreen"),
                     lambda v: v["captures"][0].update(width=319),
                     lambda v: v["captures"][0].update(fontScale=3),
                     lambda v: v["captures"][0].update(adapterSame=False),
                     lambda v: v["captures"][0].update(savedDataPreserved=False),
                     lambda v: v["captures"][0].update(windowFocused=False),
                     lambda v: v["captures"][0].update(scrollReachedEnd=False),
                     lambda v: v["captures"][0].update(touchScroll=False),
                     lambda v: v["captures"][0].update(rowsChecked=19),
                     lambda v: v["captures"][0].update(minTargetPx=47)]
        for mutate in mutations:
            value = metadata()
            mutate(value)
            with self.assertRaises(collector.GateError):
                self.validate(value)

    def test_duplicate_json_key_and_noninteger_counts_rejected(self):
        data = json.dumps(metadata()).replace('"schema": 1', '"schema": 1, "schema": 1').encode()
        with self.assertRaises(collector.GateError):
            collector.matrix(data, HEAD, TREE, 24, "passed")
        value = metadata()
        value["captures"][0]["rowsChecked"] = True
        with self.assertRaises(collector.GateError):
            self.validate(value)


class FailureDiagnosticTests(unittest.TestCase):
    def test_assertion_phase_survives_without_any_image_or_matrix_file(self):
        outcome, diagnostics = collector.execution(diagnosed_result())
        self.assertEqual("assertion_failed", outcome)
        self.assertEqual(["CONTACT_VISUAL_FAILURE:ASSERTION:DISPLAY_RESTORE"],
                         collector.failure_labels(outcome, diagnostics))

    def test_snapshot_loop_restore_and_final_metadata_have_fixed_labels(self):
        for phase in ("DISPLAY_SNAPSHOT", "MATRIX_LOOP", "SCREENSHOT", "DISPLAY_RESTORE", "METADATA",
                      "CAPTURE_COUNT", "API_BUDGET", "LAYOUT_ASSERTION", "FIXTURE_CLEANUP"):
            outcome, diagnostics = collector.execution(diagnosed_result(diagnostic_block(phase)))
            self.assertEqual([f"CONTACT_VISUAL_FAILURE:ASSERTION:{phase}"],
                             collector.failure_labels(outcome, diagnostics))

    def test_cleanup_failure_does_not_hide_observed_primary_failure(self):
        blocks = diagnostic_block("SCREENSHOT", "EXECUTION") + diagnostic_block("DISPLAY_RESTORE")
        outcome, diagnostics = collector.execution(diagnosed_result(blocks))
        self.assertEqual(["CONTACT_VISUAL_FAILURE:EXECUTION:SCREENSHOT",
                          "CONTACT_VISUAL_FAILURE:ASSERTION:DISPLAY_RESTORE"],
                         collector.failure_labels(outcome, diagnostics))

    def test_legacy_missing_diagnostic_stays_unreported_not_a_guessed_cause(self):
        outcome, diagnostics = collector.execution(result((1, -2)))
        self.assertEqual(["CONTACT_VISUAL_FAILURE:ASSERTION:UNREPORTED"],
                         collector.failure_labels(outcome, diagnostics))

    def test_private_unrecognized_extra_and_duplicate_payloads_are_rejected(self):
        private = "PRIVATE_SENTINEL_CONTACT_DEVICE_EXCEPTION"
        blocks = (diagnostic_block(phase=private), diagnostic_block(failure=private),
                  diagnostic_block(extra=f"INSTRUMENTATION_STATUS: stack={private}\n"),
                  diagnostic_block(extra=f"INSTRUMENTATION_STATUS: contact={private}\n"),
                  diagnostic_block(extra="INSTRUMENTATION_STATUS: sc241_phase=METADATA\n"),
                  diagnostic_block().replace(b"STATUS_CODE: 2", f"STATUS_CODE: {private}".encode()),
                  diagnostic_block(phase="UNKNOWN_PHASE"))
        for block in blocks:
            with self.assertRaises(collector.GateError) as error:
                collector.execution(diagnosed_result(block))
            self.assertNotIn(private, str(error.exception))
        with self.assertRaises(collector.GateError) as error:
            collector.failure_labels("assertion_failed", [{"sc241_phase": private, "sc241_failure": "ASSERTION"}])
        self.assertNotIn(private, str(error.exception))

    def test_diagnostic_cannot_create_success_or_attach_to_an_unstarted_case(self):
        for data in (diagnosed_result(codes=(1, 0)), diagnostic_block() + result((1, -2)),
                     result((1, -2)) + b"\n" + diagnostic_block(), diagnosed_result(diagnostic_block() * 5),
                     result() + b"\nINSTRUMENTATION_STATUS: sc241_phase=METADATA"):
            with self.assertRaises(collector.GateError):
                collector.execution(data)

    def test_startup_is_not_classified_as_a_test_assertion(self):
        for data in (b"INSTRUMENTATION_FAILED: synthetic startup failure", result((), count=0), result(terminal=0)):
            with self.assertRaises(collector.GateError) as error:
                collector.execution(data)
            self.assertEqual(2, error.exception.code)
        outcome, diagnostics = collector.execution(diagnosed_result(diagnostic_block("HOST_LAUNCH", "EXECUTION"),
                                                                   codes=(1, -1)))
        self.assertEqual("execution_failed", outcome)
        self.assertEqual(["CONTACT_VISUAL_FAILURE:EXECUTION:HOST_LAUNCH"],
                         collector.failure_labels(outcome, diagnostics))

    def test_producer_enum_and_collector_vocabulary_are_identical(self):
        source = (Path(__file__).resolve().parents[2] / "client_android/app/src/androidTest/java/"
                  "com/securecall/app/ContactVisualMatrix.kt").read_text()
        body = re.search(r"enum class Phase\s*\{([^}]+)\}", source).group(1)
        self.assertEqual(collector.PHASES, set(re.findall(r"\b[A-Z][A-Z_]+\b", body)))


class PngAndBudgetTests(unittest.TestCase):
    def test_exact_png_and_optional_color_chunk(self):
        collector.png(image(), (320, 676))
        collector.png(image(extra=chunk(b"sRGB", b"\0")), (320, 676))

    def test_corruption_truncation_mismatch_private_metadata_and_bombs_rejected(self):
        data = image()
        corrupt = bytearray(data)
        corrupt[30] ^= 1
        for invalid in (bytes(corrupt), data[:-1], data + b"trailing", image(390, 844),
                        image(extra=chunk(b"tEXt", b"unapproved")),
                        image(raw=b"\0" * (676 * (1 + 320 * 4) + 10000)),
                        b"\0" * (collector.PNG_LIMIT + 1)):
            with self.assertRaises(collector.GateError):
                collector.png(invalid, (320, 676))

    def test_total_includes_metadata_receipt_and_all_sixteen_images(self):
        files = {name: b"a" * (collector.API_LIMIT // 16 - 1024) for name in collector.EXPECTED}
        files.update({"matrix.json": b"a" * 4096, "source-receipt.json": b"a" * 4096})
        collector.budget(files)
        files["matrix.json"] = b"a" * 20000
        with self.assertRaises(collector.GateError):
            collector.budget(files)

    def test_command_reader_caps_bytes_and_classifies_missing_tools(self):
        with self.assertRaises(collector.GateError) as error:
            collector.bounded_command([sys.executable, "-c", "import sys; sys.stdout.buffer.write(b'x'*2000)"], 100)
        self.assertEqual("GUEST_EXPORT_TOO_LARGE", str(error.exception))
        with self.assertRaises(collector.GateError) as missing:
            collector.bounded_command(["/nonexistent-sc241-tool"], 100)
        self.assertEqual(2, missing.exception.code)


def root_status(causes=("IllegalStateException",), suppressed=(), drop=None, **override):
    fields = {"head": HEAD, "tree": TREE, "phase": "MENU", "selected": "UNKNOWN", "candidate_count": "1",
              "candidates_incomplete": "false", "exception_class": "RootViewWithoutFocusException",
              "cause_count": str(len(causes)), "cause_incomplete": "false",
              "suppressed_count": str(len(suppressed)), "suppressed_incomplete": "false"}
    for key in ("host_started", "host_resumed", "host_destroyed", "host_finishing", "host_focus",
                "interactive", "keyguard_locked", "device_locked"):
        fields[key] = "UNKNOWN"
    for index in range(8):
        fields[f"candidate_{index}_type"] = "2" if index == 0 else "-1"
        for key in ("attached", "focus", "synthetic_title", "host_context"):
            fields[f"candidate_{index}_{key}"] = "false" if index == 0 else "UNKNOWN"
    fields.update({f"cause_{i}_class": name for i, name in enumerate(causes)})
    fields.update({f"suppressed_{i}_class": name for i, name in enumerate(suppressed)})
    fields.update(override)
    fields.pop(drop, None)
    return "".join(f"INSTRUMENTATION_STATUS: sc241_root_{k}={v}\n" for k, v in fields.items()) + \
        "INSTRUMENTATION_STATUS_CODE: 2\n"


def root_result(codes=(1, -2), status=None, case=None, count=1, terminal=-1, starts=1):
    klass, test = case or collector.ROOT_CASE
    lines = []
    for _ in range(starts):
        for index, code in enumerate(codes):
            lines.append(f"INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_STATUS: test={test}\n"
                         f"INSTRUMENTATION_STATUS: numtests={count}\n")
            if code != 1:
                lines.append("INSTRUMENTATION_STATUS: stack=java.lang.SecretMessage: private-ui-text\n")
            lines.append(f"INSTRUMENTATION_STATUS_CODE: {code}\n")
            if index == 0 and status is not None:
                lines.append(status)
    lines.append("OK (1 test)\n" if codes == (1, 0) else "FAILURES!!!\n")
    lines.append(f"INSTRUMENTATION_CODE: {terminal}\n")
    return "".join(lines).encode()


class RootProbeTests(unittest.TestCase):
    def run_root(self, data):
        return collector.root_execution(data, HEAD, TREE)

    def assert_rejected(self, data, label):
        with self.assertRaisesRegex(collector.GateError, label):
            self.run_root(data)

    def test_failure_with_class_fields_survives_whitelist_and_stays_failure(self):
        outcome, diagnostics = self.run_root(root_result(
            status=root_status(causes=("A", "B"), suppressed=("C",))))
        self.assertEqual(outcome, "assertion_failed")
        self.assertEqual(len(diagnostics), 1)
        value = diagnostics[0]
        self.assertEqual(value["exception_class"], "RootViewWithoutFocusException")
        self.assertEqual((value["cause_0_class"], value["cause_1_class"], value["suppressed_0_class"]), ("A", "B", "C"))
        self.assertFalse(any("Secret" in item or "private" in item for item in value.values()))

    def test_four_slot_caps_and_unknown_class_accepted(self):
        status = root_status(causes=("A", "B", "C", "UNKNOWN"), suppressed=("D", "E", "F", "G"),
                             cause_incomplete="true", suppressed_incomplete="true")
        self.assertEqual(self.run_root(root_result(status=status))[0], "assertion_failed")
        self.assertEqual(self.run_root(root_result(status=root_status(suppressed_count="-1")))[0], "assertion_failed")

    def test_execution_failure_and_unreported_failure_remain_failures(self):
        self.assertEqual(self.run_root(root_result(codes=(1, -1)))[0], "execution_failed")
        self.assertEqual(self.run_root(root_result()), ("assertion_failed", []))

    def test_pass_without_diagnostic_only(self):
        self.assertEqual(self.run_root(root_result(codes=(1, 0))), ("passed", []))
        self.assert_rejected(root_result(codes=(1, 0), status=root_status()), "FAILURE_DIAGNOSTIC_WITH_SUCCESS")

    def test_zero_or_more_than_one_executed_case_rejected(self):
        self.assert_rejected(b"INSTRUMENTATION_CODE: -1\n", "ROOT_CASE_DID_NOT_START")
        self.assert_rejected(root_result(starts=2), "ROOT_REQUIRES_EXACTLY_ONE_TEST")
        self.assert_rejected(root_result(count=2), "ROOT_REQUIRES_EXACTLY_ONE_TEST")
        self.assert_rejected(root_result(case=collector.CASE), "UNEXPECTED_OR_MISSING_ROOT_CASE")
        self.assert_rejected(root_result(terminal=0), "INSTRUMENTATION_DID_NOT_COMPLETE")
        self.assert_rejected(root_result(codes=(1, -3)), "ROOT_CASE_SKIPPED")

    def test_unapproved_or_payload_fields_rejected(self):
        self.assert_rejected(root_result(status=root_status(exception_message="secret")), "UNAPPROVED_ROOT_FIELDS")
        self.assert_rejected(root_result(status=root_status(exception_class="java.lang.Secret text")),
                             "UNAPPROVED_ROOT_CLASS")
        self.assert_rejected(root_result(status=root_status(exception_class="A" * 65)), "UNAPPROVED_ROOT_CLASS")
        self.assert_rejected(root_result(status=root_status(cause_count="2")), "UNAPPROVED_ROOT_FIELDS")
        self.assert_rejected(root_result(status=root_status(cause_count="5")), "UNAPPROVED_ROOT_COUNT")
        self.assert_rejected(root_result(status=root_status(drop="host_focus")), "UNAPPROVED_ROOT_FIELDS")
        self.assert_rejected(root_result(status=root_status(host_focus="maybe")), "UNAPPROVED_ROOT_VALUE")
        self.assert_rejected(root_result(status=root_status(candidate_0_type="3000")), "UNAPPROVED_ROOT_VALUE")
        self.assert_rejected(root_result(status=root_status(phase="CONFIRM")), "UNAPPROVED_ROOT_PHASE")
        self.assert_rejected(root_result(status=root_status(head="c" * 40)), "ROOT_SOURCE_RECEIPT_MISMATCH")
        mixed = root_status().replace("INSTRUMENTATION_STATUS_CODE", "INSTRUMENTATION_STATUS: sc241_phase=MENU\n"
                                      "INSTRUMENTATION_STATUS_CODE", 1)
        self.assert_rejected(root_result(status=mixed), "UNAPPROVED_ROOT_FIELDS")

    def test_single_diagnostic_inside_active_case_only(self):
        self.assert_rejected(root_result(status=root_status() + root_status()), "ROOT_DIAGNOSTIC_COUNT_EXCEEDED")
        outside = root_status().encode() + root_result()
        self.assert_rejected(outside, "ROOT_DIAGNOSTIC_OUTSIDE_ACTIVE_CASE")

    def test_visual_collector_still_rejects_root_fields(self):
        with self.assertRaisesRegex(collector.GateError, "UNAPPROVED_DIAGNOSTIC_FIELDS"):
            collector.execution(diagnosed_result(root_status().encode()))


if __name__ == "__main__":
    unittest.main()
