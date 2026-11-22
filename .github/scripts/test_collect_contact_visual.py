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

    def test_protocol_order_and_block_counterexamples_rejected(self):
        ok = root_result()
        terminal_first = b"INSTRUMENTATION_CODE: -1\n" + ok.replace(b"INSTRUMENTATION_CODE: -1\n", b"")
        self.assert_rejected(terminal_first, "ROOT_STATUS_AFTER_TERMINAL")
        self.assert_rejected(ok + b"INSTRUMENTATION_STATUS_CODE: -2\n", "ROOT_STATUS_AFTER_TERMINAL")
        self.assert_rejected(ok + b"INSTRUMENTATION_STATUS: class=X\n", "ROOT_STATUS_AFTER_TERMINAL")
        klass, test = collector.ROOT_CASE
        duplicate = ok.replace(f"INSTRUMENTATION_STATUS: test={test}\n".encode(),
                               f"INSTRUMENTATION_STATUS: test={test}\nINSTRUMENTATION_STATUS: test=other\n"
                               f"INSTRUMENTATION_STATUS: test={test}\n".encode(), 1)
        self.assert_rejected(duplicate, "ROOT_DUPLICATE_STATUS_FIELD")
        pending = ok.replace(b"INSTRUMENTATION_CODE: -1\n",
                             f"INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_CODE: -1\n".encode())
        self.assert_rejected(pending, "ROOT_INCOMPLETE_STATUS_BLOCK")
        self.assert_rejected(ok + f"INSTRUMENTATION_STATUS: class={klass}\n".encode(), "ROOT_STATUS_AFTER_TERMINAL")
        self.assert_rejected(ok.replace(b"INSTRUMENTATION_CODE: -1\n", f"INSTRUMENTATION_STATUS: class={klass}\n".encode()),
                             "ROOT_INCOMPLETE_STATUS_BLOCK")
        self.assert_rejected(ok + b"INSTRUMENTATION_CODE: -1\n", "ROOT_MULTIPLE_TERMINAL_MARKERS")

    def test_real_runner_protocol_shape_still_classified(self):
        klass, test = collector.ROOT_CASE
        def block(code, extra=""):
            return (f"INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_STATUS: current=1\n"
                    f"INSTRUMENTATION_STATUS: id=AndroidJUnitRunner\nINSTRUMENTATION_STATUS: numtests=1\n{extra}"
                    f"INSTRUMENTATION_STATUS: stream=\ncom.securecall.app.X:\nINSTRUMENTATION_STATUS: test={test}\n"
                    f"INSTRUMENTATION_STATUS_CODE: {code}\n")
        tail = "INSTRUMENTATION_RESULT: stream=\n\nTime: 1\n\n{}\n\nINSTRUMENTATION_CODE: -1\n"
        failed = block(1) + root_status() + block(-2, "INSTRUMENTATION_STATUS: stack=java.lang.X: private\n\tat a\n") \
            + tail.format("FAILURES!!!\nTests run: 1,  Failures: 1")
        outcome, diagnostics = self.run_root(failed.encode())
        self.assertEqual((outcome, len(diagnostics)), ("assertion_failed", 1))
        self.assertEqual(self.run_root((block(1) + block(0) + tail.format("OK (1 test)")).encode()), ("passed", []))

    def test_visual_collector_still_rejects_root_fields(self):
        with self.assertRaisesRegex(collector.GateError, "UNAPPROVED_DIAGNOSTIC_FIELDS"):
            collector.execution(diagnosed_result(root_status().encode()))


def focus_block(case, code, current, extra="", count=5):
    klass, test = case
    return (f"INSTRUMENTATION_STATUS: class={klass}\nINSTRUMENTATION_STATUS: current={current}\n"
            f"INSTRUMENTATION_STATUS: id=AndroidJUnitRunner\nINSTRUMENTATION_STATUS: numtests={count}\n{extra}"
            f"INSTRUMENTATION_STATUS: stream=\n{klass}:\nINSTRUMENTATION_STATUS: test={test}\n"
            f"INSTRUMENTATION_STATUS_CODE: {code}\n")


def focus_result(codes=None, cases=None, status=None, summary=None, terminal=-1):
    cases = list(collector.FOCUS_CASES if cases is None else cases)
    codes = codes or {}
    out = []
    for index, case in enumerate(cases, 1):
        code = codes.get(case[1], 0)
        out.append(focus_block(case, 1, index))
        if case == collector.ROOT_CASE and status is not None:
            out.append(status)
        stack = "" if code == 0 else "INSTRUMENTATION_STATUS: stack=java.lang.SecretMessage: private-ui-text\n\tat x\n"
        out.append(focus_block(case, code, index, stack))
    if summary is None:
        summary = "OK (5 tests)" if not any(codes.values()) else "FAILURES!!!\nTests run: 5,  Failures: 1"
    out.append(f"INSTRUMENTATION_RESULT: stream=\n\nTime: 1\n\n{summary}\n\nINSTRUMENTATION_CODE: {terminal}\n")
    return "".join(out).encode()


class FocusProbeTests(unittest.TestCase):
    def run_focus(self, data):
        return collector.focus_execution(data, HEAD, TREE)

    def assert_rejected(self, data, label):
        with self.assertRaisesRegex(collector.GateError, label):
            self.run_focus(data)

    def test_exact_five_fixed_cases_and_selector(self):
        self.assertEqual(len(set(collector.FOCUS_CASES)), 5)
        self.assertEqual(collector.FOCUS_CASES[4], collector.ROOT_CASE)
        self.assertNotIn(collector.CASE, collector.FOCUS_CASES)
        self.assertEqual(collector.FOCUS_SELECTOR.count(","), 4)
        self.assertNotIn("syntheticContactWindowMatrix", collector.FOCUS_SELECTOR)

    def test_five_case_success(self):
        outcomes, diagnostics = self.run_focus(focus_result())
        self.assertEqual(set(outcomes.values()), {"passed"})
        self.assertEqual((len(outcomes), diagnostics), (5, []))

    def test_each_case_failure_stays_failure(self):
        for _, test in collector.FOCUS_CASES:
            for code, outcome in ((-2, "assertion_failed"), (-1, "execution_failed")):
                outcomes, _ = self.run_focus(focus_result(codes={test: code}))
                self.assertEqual(outcomes[next(c for c in collector.FOCUS_CASES if c[1] == test)], outcome)
                self.assertEqual(sum(o == "passed" for o in outcomes.values()), 4)

    def test_root_metadata_only_on_failing_contact_case(self):
        root = collector.ROOT_CASE[1]
        outcomes, diagnostics = self.run_focus(focus_result(codes={root: -2}, status=root_status()))
        self.assertEqual((outcomes[collector.ROOT_CASE], len(diagnostics)), ("assertion_failed", 1))
        self.assertFalse(any("Secret" in v or "private" in v for v in diagnostics[0].values()))
        self.assert_rejected(focus_result(status=root_status()), "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        self.assert_rejected(focus_result(codes={root: -2}, status=root_status() + root_status()),
                             "FOCUS_DIAGNOSTIC_COUNT_EXCEEDED")
        main = focus_block(collector.FOCUS_CASES[0], 1, 1)
        misplaced = focus_result(codes={root: -2}).replace(main.encode(), (main + root_status()).encode(), 1)
        self.assert_rejected(misplaced, "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_ROOT_CASE")
        self.assert_rejected(focus_result(codes={root: -2}, status=root_status(exception_message="x")),
                             "UNAPPROVED_ROOT_FIELDS")
        self.assert_rejected(focus_result(codes={root: -2}, status=root_status(head="c" * 40)),
                             "ROOT_SOURCE_RECEIPT_MISMATCH")

    def test_skip_extra_missing_duplicate_wrong_case(self):
        first = collector.FOCUS_CASES[0][1]
        self.assert_rejected(focus_result(codes={first: -3}), "FOCUS_CASE_SKIPPED")
        self.assert_rejected(focus_result(codes={first: -4}), "FOCUS_CASE_SKIPPED")
        self.assert_rejected(focus_result(cases=collector.FOCUS_CASES[:4]), "FOCUS_MISSING_CASE")
        self.assert_rejected(focus_result(cases=collector.FOCUS_CASES + (collector.CASE,)), "UNEXPECTED_OR_MISSING")
        extra = (collector.MAIN_CLASS, "callsNavigationItem_isDisplayed")
        self.assert_rejected(focus_result(cases=collector.FOCUS_CASES[:4] + (extra,)), "UNEXPECTED_OR_MISSING")
        dup = collector.FOCUS_CASES[:4] + (collector.FOCUS_CASES[0],)
        self.assert_rejected(focus_result(cases=dup), "FOCUS_DUPLICATE_CASE")
        self.assert_rejected(focus_result().replace(b"numtests=5", b"numtests=6"), "EXACTLY_FIVE")
        self.assert_rejected(b"INSTRUMENTATION_CODE: -1\n", "FOCUS_CASES_DID_NOT_START")

    def test_incomplete_order_and_terminal_rejected(self):
        ok = focus_result()
        self.assert_rejected(ok.replace(b"INSTRUMENTATION_CODE: -1", b"INSTRUMENTATION_CODE: 0"),
                             "INSTRUMENTATION_DID_NOT_COMPLETE")
        self.assert_rejected(ok.replace(b"INSTRUMENTATION_CODE: -1\n", b""), "INSTRUMENTATION_DID_NOT_COMPLETE")
        self.assert_rejected(ok + b"INSTRUMENTATION_CODE: -1\n", "FOCUS_MULTIPLE_TERMINAL_MARKERS")
        self.assert_rejected(ok + b"INSTRUMENTATION_STATUS: class=X\n", "FOCUS_STATUS_AFTER_TERMINAL")
        last = collector.FOCUS_CASES[4]
        unfinished = ok.replace(focus_block(last, 0, 5).encode(), b"")
        self.assert_rejected(unfinished, "FOCUS_CASE_ORDER_OR_INCOMPLETE")
        interleaved = ok.replace(focus_block(collector.FOCUS_CASES[0], 0, 1).encode(), b"", 1)
        self.assert_rejected(interleaved, "FOCUS_CASE_ORDER_OR_INCOMPLETE")
        self.assert_rejected(ok.replace(b"current=2", b"current=3", 1), "FOCUS_CASE_ORDER_OR_INCOMPLETE")
        pending = ok.replace(b"INSTRUMENTATION_CODE: -1\n", b"INSTRUMENTATION_STATUS: class=X\nINSTRUMENTATION_CODE: -1\n")
        self.assert_rejected(pending, "FOCUS_INCOMPLETE_STATUS_BLOCK")
        dup_field = ok.replace(b"INSTRUMENTATION_STATUS: numtests=5\n",
                               b"INSTRUMENTATION_STATUS: numtests=5\nINSTRUMENTATION_STATUS: numtests=5\n", 1)
        self.assert_rejected(dup_field, "FOCUS_DUPLICATE_STATUS_FIELD")

    def test_summary_must_match_outcomes(self):
        self.assert_rejected(focus_result(summary="OK (1 test)"), "SUCCESS_SUMMARY_MISSING")
        self.assert_rejected(focus_result(codes={collector.FOCUS_CASES[2][1]: -2}, summary="OK (5 tests)"),
                             "FALSE_SUCCESS_SUMMARY")

    def test_raw_payload_never_printed(self):
        import contextlib, io, os, tempfile
        root = collector.ROOT_CASE[1]
        data = focus_result(codes={root: -2, collector.FOCUS_CASES[0][1]: -1}, status=root_status())
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "result.txt"
            path.write_bytes(data)
            out = io.StringIO()
            env = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted"}
            saved = {k: os.environ.get(k) for k in env}
            os.environ.update(env)
            try:
                with contextlib.redirect_stdout(out):
                    code = collector.collect_focus(path, HEAD, TREE)
            finally:
                for k, v in saved.items():
                    os.environ.pop(k) if v is None else os.environ.__setitem__(k, v)
        text = out.getvalue()
        self.assertEqual(code, 1)
        self.assertNotRegex(text, r"Secret|private|stack|stream|\tat ")
        lines = text.splitlines()
        self.assertTrue(all(re.fullmatch(r"SC242_FOCUS_(SOURCE|CASE|RESULT):[A-Za-z0-9_#:=]+|SC241_ROOT_FIELD:[a-z0-9_]+=[A-Za-z0-9_-]+", l)
                            for l in lines), lines)
        self.assertEqual(sum(l.startswith("SC242_FOCUS_CASE:") for l in lines), 5)
        self.assertIn("SC242_FOCUS_RESULT:passed=3:failed=2", lines)

    def test_visual_and_root_collectors_reject_focus_protocol(self):
        with self.assertRaises(collector.GateError):
            collector.root_execution(focus_result(), HEAD, TREE)
        with self.assertRaises(collector.GateError):
            collector.execution(focus_result())


def main_status(method, phase="CHECK", exception="NoMatchingViewException", causes=(), drop=None, **override):
    fields = {"head": HEAD, "tree": TREE, "method": method, "phase": phase, "exception_class": exception,
              "cause_count": str(len(causes)), "cause_incomplete": "false"}
    fields.update({f"cause_{i}_class": c for i, c in enumerate(causes)})
    fields.update(override)
    fields.pop(drop, None)
    return "".join(f"INSTRUMENTATION_STATUS: sc242_main_{k}={v}\n" for k, v in fields.items()) + \
        "INSTRUMENTATION_STATUS_CODE: 2\n"


def failures_result(codes=None, cases=None, statuses=None, summary=None, terminal=-1):
    cases = list(collector.FAILURE_CASES if cases is None else cases)
    codes = {} if codes is None else codes
    statuses = statuses or {}
    out = []
    for index, case in enumerate(cases, 1):
        code = codes.get(case[1], -2)
        out.append(focus_block(case, 1, index, count=3))
        out.append(statuses.get(case[1], ""))
        stack = "" if code == 0 else "INSTRUMENTATION_STATUS: stack=java.lang.SecretMessage: private-ui-text\n\tat x\n"
        out.append(focus_block(case, code, index, stack, count=3))
    if summary is None:
        summary = "OK (3 tests)" if all(codes.get(c[1], -2) == 0 for c in cases) else "FAILURES!!!\nTests run: 3"
    out.append(f"INSTRUMENTATION_RESULT: stream=\n\nTime: 1\n\n{summary}\n\nINSTRUMENTATION_CODE: {terminal}\n")
    return "".join(out).encode()


class FocusFailuresOnlyTests(unittest.TestCase):
    VPN, SETTINGS, BOTTOM = (test for _, test in collector.FOCUS_CASES[:3])

    def run_failures(self, data):
        return collector.focus_execution(data, HEAD, TREE, collector.FAILURE_CASES)

    def assert_rejected(self, data, label):
        with self.assertRaisesRegex(collector.GateError, label):
            self.run_failures(data)

    def all_statuses(self, **phases):
        return {t: main_status(t, phases.get(t, "CHECK")) for t in (self.VPN, self.SETTINGS, self.BOTTOM)}

    def test_exact_three_fixed_failed_main_cases_no_toolbar_or_contact(self):
        self.assertEqual(collector.FAILURE_CASES, (
            (collector.MAIN_CLASS, "externalVpnIndicator_matchesCurrentTransport"),
            (collector.MAIN_CLASS, "settingsNavigationItem_click_doesNotCrash"),
            (collector.MAIN_CLASS, "bottomNavigation_isDisplayed")))
        self.assertNotIn("toolbar_isDisplayed", collector.FAILURE_SELECTOR)
        self.assertNotIn(collector.ROOT_CASE[1], collector.FAILURE_SELECTOR)
        self.assertEqual(collector.FAILURE_SELECTOR.count(","), 2)
        with self.assertRaisesRegex(collector.GateError, "UNAPPROVED_FOCUS_PROFILE"):
            collector.focus_execution(failures_result(), HEAD, TREE, collector.FOCUS_CASES[1:4])

    def test_three_failures_with_closed_metadata_stay_failures(self):
        outcomes, diagnostics = self.run_failures(failures_result(
            codes={self.VPN: -2, self.SETTINGS: -1, self.BOTTOM: -2},
            statuses=self.all_statuses(**{self.SETTINGS: "CLICK"})))
        self.assertEqual(sorted(outcomes.values()), ["assertion_failed", "assertion_failed", "execution_failed"])
        self.assertEqual({d["method"]: d["phase"] for d in diagnostics},
                         {self.VPN: "CHECK", self.SETTINGS: "CLICK", self.BOTTOM: "CHECK"})
        for phase in collector.MAIN_PHASES:
            self.run_failures(failures_result(statuses={self.VPN: main_status(self.VPN, phase)}))

    def test_cause_slots_and_unknown_class(self):
        causes = ("RuntimeException", "UNKNOWN", "IllegalStateException", "Error")
        _, diagnostics = self.run_failures(failures_result(statuses={
            self.BOTTOM: main_status(self.BOTTOM, causes=causes, cause_incomplete="true", exception_class="UNKNOWN")}))
        self.assertEqual(diagnostics[0]["cause_3_class"], "Error")
        five = main_status(self.BOTTOM, causes=causes + ("Extra",))
        self.assert_rejected(failures_result(statuses={self.BOTTOM: five}), "UNAPPROVED_MAIN_COUNT")

    def test_missing_metadata_is_unreported_failure_never_pass(self):
        outcomes, diagnostics = self.run_failures(failures_result())
        self.assertEqual((set(outcomes.values()), diagnostics), ({"assertion_failed"}, []))

    def test_pass_only_without_metadata(self):
        ok = {t: 0 for t in (self.VPN, self.SETTINGS, self.BOTTOM)}
        outcomes, diagnostics = self.run_failures(failures_result(codes=ok))
        self.assertEqual((set(outcomes.values()), diagnostics), ({"passed"}, []))
        self.assert_rejected(failures_result(codes=ok, statuses={self.VPN: main_status(self.VPN)}),
                             "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        self.assert_rejected(failures_result(codes=ok, summary="OK (5 tests)"), "SUCCESS_SUMMARY_MISSING")
        self.assert_rejected(failures_result(summary="OK (3 tests)"), "FALSE_SUCCESS_SUMMARY")

    def test_unknown_keys_raw_payloads_and_bad_values_rejected(self):
        bad = [
            (main_status(self.VPN, exception_message="private"), "UNAPPROVED_MAIN_FIELDS"),
            (main_status(self.VPN, stack="x"), "UNAPPROVED_MAIN_FIELDS"),
            (main_status(self.VPN, drop="phase"), "UNAPPROVED_MAIN_FIELDS"),
            (main_status(self.VPN, drop="cause_count"), "UNAPPROVED_MAIN_COUNT"),
            (main_status(self.VPN, phase="MENU"), "UNAPPROVED_MAIN_PHASE"),
            (main_status(self.VPN, exception_class="a.b.Secret"), "UNAPPROVED_MAIN_CLASS"),
            (main_status(self.VPN, exception_class="X" * 65), "UNAPPROVED_MAIN_CLASS"),
            (main_status(self.VPN, causes=("Bad Name",)), "UNAPPROVED_MAIN_CLASS"),
            (main_status(self.VPN, cause_incomplete="maybe"), "UNAPPROVED_MAIN_VALUE"),
            (main_status(self.VPN, head="c" * 40), "MAIN_SOURCE_RECEIPT_MISMATCH"),
            (main_status(self.VPN, tree="c" * 40), "MAIN_SOURCE_RECEIPT_MISMATCH"),
            (main_status(self.SETTINGS), "MAIN_METHOD_MISMATCH"),
            (main_status(self.VPN) .replace("CODE: 2", "CODE: 3"), "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE"),
            (root_status(), "UNAPPROVED_MAIN_FIELDS"),
            (main_status(self.VPN).replace("sc242_main_head", "sc242_main_head=x\nINSTRUMENTATION_STATUS: sc242_main_head", 1),
             "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE"),
        ]
        for status, label in bad:
            with self.subTest(label=label):
                self.assert_rejected(failures_result(statuses={self.VPN: status}), label)

    def test_one_metadata_event_per_failed_active_case_only(self):
        twice = main_status(self.VPN) + main_status(self.VPN)
        self.assert_rejected(failures_result(statuses={self.VPN: twice}), "FOCUS_DIAGNOSTIC_COUNT_EXCEEDED")
        first = focus_block(collector.FAILURE_CASES[0], 1, 1, count=3)
        before = failures_result().replace(first.encode(), (main_status(self.VPN) + first).encode(), 1)
        self.assert_rejected(before, "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE")
        after_terminal = failures_result() + main_status(self.VPN).encode()
        self.assert_rejected(after_terminal, "FOCUS_STATUS_AFTER_TERMINAL")
        second = focus_block(collector.FAILURE_CASES[1], 1, 2, count=3)
        between = failures_result().replace(second.encode(), (main_status(self.VPN) + second).encode(), 1)
        self.assert_rejected(between, "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE")
        inside_block = failures_result().replace(second.encode(), second.replace(
            "INSTRUMENTATION_STATUS: current=2\n", main_status(self.SETTINGS).rsplit("INSTRUMENTATION_STATUS_CODE", 1)[0]
            + "INSTRUMENTATION_STATUS: current=2\n").encode(), 1)
        self.assert_rejected(inside_block, "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE")

    def test_foreign_extra_duplicate_skipped_missing_cases_rejected(self):
        toolbar = (collector.MAIN_CLASS, "toolbar_isDisplayed")
        cases = collector.FAILURE_CASES
        self.assert_rejected(failures_result(cases=cases[:2] + (toolbar,)), "UNEXPECTED_OR_MISSING")
        self.assert_rejected(failures_result(cases=cases[:2] + (collector.ROOT_CASE,)), "UNEXPECTED_OR_MISSING")
        self.assert_rejected(failures_result(cases=cases + (toolbar,)), "UNEXPECTED_OR_MISSING")
        self.assert_rejected(failures_result(cases=cases[:2]), "FOCUS_MISSING_CASE")
        self.assert_rejected(failures_result(cases=cases[:2] + (cases[0],)), "FOCUS_DUPLICATE_CASE")
        self.assert_rejected(failures_result(codes={self.VPN: -3}), "FOCUS_CASE_SKIPPED")
        self.assert_rejected(failures_result(codes={self.VPN: -4}), "FOCUS_CASE_SKIPPED")
        self.assert_rejected(failures_result().replace(b"numtests=3", b"numtests=5"), "EXACTLY_THREE")
        self.assert_rejected(b"INSTRUMENTATION_CODE: -1\n", "FOCUS_CASES_DID_NOT_START")

    def test_order_incomplete_and_terminal_rejected(self):
        data = failures_result()
        self.assert_rejected(data.replace(b"INSTRUMENTATION_CODE: -1", b"INSTRUMENTATION_CODE: 0"),
                             "INSTRUMENTATION_DID_NOT_COMPLETE")
        self.assert_rejected(data + b"INSTRUMENTATION_CODE: -1\n", "FOCUS_MULTIPLE_TERMINAL_MARKERS")
        self.assert_rejected(data.replace(b"current=2", b"current=3", 1), "FOCUS_CASE_ORDER_OR_INCOMPLETE")
        last = collector.FAILURE_CASES[2]
        stack = "INSTRUMENTATION_STATUS: stack=java.lang.SecretMessage: private-ui-text\n\tat x\n"
        self.assert_rejected(data.replace(focus_block(last, -2, 3, stack, count=3).encode(), b""),
                             "FOCUS_CASE_ORDER_OR_INCOMPLETE")

    def test_mixed_modes_rejected(self):
        five_with_main = focus_result(codes={self.VPN: -2}).replace(
            focus_block(collector.FOCUS_CASES[0], 1, 1).encode(),
            (focus_block(collector.FOCUS_CASES[0], 1, 1) + main_status(self.VPN)).encode(), 1)
        with self.assertRaisesRegex(collector.GateError, "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_ROOT_CASE"):
            collector.focus_execution(five_with_main, HEAD, TREE)
        with self.assertRaises(collector.GateError):
            collector.focus_execution(focus_result(), HEAD, TREE, collector.FAILURE_CASES)
        with self.assertRaises(collector.GateError):
            self.run_failures(focus_result())
        with self.assertRaises(collector.GateError):
            collector.root_execution(failures_result(), HEAD, TREE)
        with self.assertRaises(collector.GateError):
            collector.execution(failures_result())

    def test_output_fixed_tokens_only_and_unreported_explicit(self):
        import contextlib, io, os, tempfile
        data = failures_result(codes={self.VPN: -2, self.SETTINGS: 0, self.BOTTOM: -1},
                               statuses={self.VPN: main_status(self.VPN, causes=("RuntimeException",))})
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "result.txt"
            path.write_bytes(data)
            out = io.StringIO()
            env = {"GITHUB_ACTIONS": "true", "RUNNER_ENVIRONMENT": "github-hosted"}
            saved = {k: os.environ.get(k) for k in env}
            os.environ.update(env)
            try:
                with contextlib.redirect_stdout(out):
                    code = collector.collect_focus(path, HEAD, TREE, collector.FAILURE_CASES)
            finally:
                for k, v in saved.items():
                    os.environ.pop(k) if v is None else os.environ.__setitem__(k, v)
        lines = out.getvalue().splitlines()
        self.assertEqual(code, 1)
        self.assertNotRegex(out.getvalue(), r"Secret|private|stack|stream|\tat |SC241_ROOT")
        self.assertTrue(all(re.fullmatch(r"SC242_FOCUS_(SOURCE|CASE|RESULT):[A-Za-z0-9_#:=]+|"
                                         r"SC242_FAILURE_FIELD:[A-Za-z_]+:(UNREPORTED|[a-z0-9_]+=[A-Za-z0-9_]+)", l)
                            for l in lines), lines)
        self.assertIn(f"SC242_FAILURE_FIELD:{self.BOTTOM}:UNREPORTED", lines)
        self.assertIn(f"SC242_FAILURE_FIELD:{self.VPN}:phase=CHECK", lines)
        self.assertIn(f"SC242_FAILURE_FIELD:{self.VPN}:cause_0_class=RuntimeException", lines)
        self.assertFalse(any(self.SETTINGS in l and "FAILURE_FIELD" in l for l in lines))
        self.assertEqual(sum(l.startswith("SC242_FOCUS_CASE:") for l in lines), 3)
        self.assertIn("SC242_FOCUS_RESULT:passed=1:failed=2", lines)

    def test_cli_rejects_failures_only_without_focus_probe(self):
        import subprocess
        script = Path(__file__).with_name("collect_contact_visual.py")
        for extra in (["--focus-failures-only"], ["--focus-failures-only", "--root-probe"]):
            done = subprocess.run([sys.executable, "-B", str(script), "--result", "/nonexistent",
                                   "--head", HEAD, "--tree", TREE, *extra], capture_output=True, text=True, timeout=10)
            self.assertEqual(done.returncode, 2)
            self.assertIn("MIXED_COLLECTOR_MODE", done.stderr)


if __name__ == "__main__":
    unittest.main()
