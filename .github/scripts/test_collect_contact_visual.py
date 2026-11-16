"""Pure synthetic parser/PNG/cap tests. Never invokes adb or fabricates CI environment."""

import binascii
import importlib.util
import json
from pathlib import Path
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


if __name__ == "__main__":
    unittest.main()
