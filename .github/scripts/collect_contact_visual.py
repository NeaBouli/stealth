#!/usr/bin/env python3
"""Fixed synthetic-window export. No arbitrary guest paths or raw log artifacts."""

import argparse
import binascii
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import struct
import subprocess
import sys
import tempfile
import time
import zlib

CASE = ("com.securecall.app.ContactLookupPreservationInstrumentedTest", "syntheticContactWindowMatrix")
ORIGINAL_CASES = {
    "nonemptyLookupPreservesUnrelatedDirectIdContact",
    "nonemptyLookupPreservesSecureIdMetadataAbsentFromResults",
    "nonemptyPartialCoverageDoesNotRevokeUnreturnedIdentities",
    "emptyFailureShapedResultAfterSuccessPreservesSavedAssociations",
    "positivePhoneAssociationStillPersistsWithoutChangingOtherFields",
    "positiveDuplicateMergeStillRemovesOnlyTheMatchedDirectIdEntry",
    "explicitUserDeleteStillRequiresConfirmationAndRemovesOnlyChosenContact",
}
VIEWPORTS = ((320, 676), (390, 844), (820, 1180), (1180, 820))
EXPECTED = {f"sc241-{w}x{h}-f{font}-{state}.png": (w, h, font, state)
            for w, h in VIEWPORTS for font in (1, 2) for state in ("before", "after")}
PACKAGE = "com.securecall.app.free"
DIRECTORY = "files/sc241-contact-visual"
PNG_LIMIT = 4 * 1024 * 1024
METADATA_LIMIT = 128 * 1024
API_LIMIT = 8 * 1024 * 1024
FRAME_KEYS = {"name", "width", "height", "fontScale", "state", "densityDpi", "rowCount",
              "rowsChecked", "minTargetPx", "minRowHeightPx", "maxTextLines", "violations",
              "scrollReachedEnd", "touchScroll", "adapterSame", "savedDataPreserved", "windowFocused", "pixelColors", "errors"}
MATRIX_KEYS = {"schema", "captureMode", "head", "tree", "api", "status", "restored", "violations", "captures"}
LAYOUT_ERRORS = {"missing_row", "scroll_end", "touch_scroll", "control_bounds", "target_min", "row_horizontal", "row_height",
                 "row_not_fully_reachable", "row_target", "call_bounds", "floating_control_overlap", "child_bounds",
                 "text_layout", "text_height", "text_ellipsis", "text_width", "text_call_overlap",
                 "row_identity", "name_binding", "identity_binding"}


class GateError(Exception):
    def __init__(self, label: str, code: int = 3):
        super().__init__(label)
        self.code = code


def require(condition: bool, label: str) -> None:
    if not condition:
        raise GateError(label)


def protocol(data: bytes) -> str:
    require(len(data) <= 256 * 1024, "PROTOCOL_TOO_LARGE")
    text = data.decode("utf-8", errors="strict")
    block: dict[str, str] = {}
    events: list[int] = []
    terminal: list[int] = []
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator and key in {"class", "test", "numtests"}:
                block[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = int(line.split(": ", 1)[1])
            require((block.get("class"), block.get("test")) == CASE, "UNEXPECTED_OR_MISSING_CASE")
            require(block.get("numtests") == "1", "VISUAL_REQUIRES_EXACTLY_ONE_TEST")
            events.append(code)
            block = {}
        elif line.startswith("INSTRUMENTATION_CODE: "):
            terminal.append(int(line.split(": ", 1)[1]))
    require(terminal == [-1], "INSTRUMENTATION_DID_NOT_COMPLETE")
    if events == [1, 0]:
        require(re.search(r"^OK \(1 test\)$", text, re.MULTILINE) is not None, "SUCCESS_SUMMARY_MISSING")
        return "passed"
    if events == [1, -2]:
        return "assertion_failed"
    raise GateError("VISUAL_CASE_SKIPPED_MISSING_OR_FAILED_TO_START")


def original_cases(data: bytes) -> None:
    require(len(data) <= 1024 * 1024, "SMOKE_PROTOCOL_TOO_LARGE")
    events = {name: [] for name in ORIGINAL_CASES}
    block: dict[str, str] = {}
    for line in data.decode("utf-8", errors="strict").splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator and key in {"class", "test"}:
                block[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            if block.get("class") == CASE[0]:
                name = block.get("test")
                require(name != CASE[1], "VISUAL_CASE_MUST_BE_SEPARATELY_ADMITTED")
                if name in events:
                    events[name].append(int(line.split(": ", 1)[1]))
            block = {}
    require(all(codes == [1, 0] for codes in events.values()), "ORIGINAL_SEVEN_CASES_MUST_PASS")


def unique_object(pairs: list[tuple[str, object]]) -> dict:
    result: dict = {}
    for key, value in pairs:
        require(key not in result, "DUPLICATE_JSON_KEY")
        result[key] = value
    return result


def integer(value: object, lower: int, upper: int) -> bool:
    return type(value) is int and lower <= value <= upper


def matrix(data: bytes, head: str, tree: str, api: int, outcome: str) -> dict:
    require(len(data) <= METADATA_LIMIT, "METADATA_TOO_LARGE")
    value = json.loads(data, object_pairs_hook=unique_object)
    require(type(value) is dict and set(value) == MATRIX_KEYS, "UNAPPROVED_MATRIX_FIELDS")
    require(value["schema"] == 1 and type(value["schema"]) is int, "MATRIX_SCHEMA")
    require(value["captureMode"] == "uiAutomation-window", "OFFSCREEN_OR_UNKNOWN_CAPTURE")
    require(value["head"] == head and value["tree"] == tree and value["api"] == api, "SOURCE_API_MISMATCH")
    require(type(value["api"]) is int and api in (24, 36), "UNAPPROVED_API")
    require(value["restored"] is True, "GUEST_RESTORATION_UNPROVEN")
    require(integer(value["violations"], 0, 100000), "INVALID_VIOLATION_COUNT")
    require(value["status"] == ("passed" if outcome == "passed" else "failed"), "CASE_MATRIX_STATUS_MISMATCH")
    require((value["violations"] == 0) == (outcome == "passed"), "FALSE_VISUAL_SUCCESS")
    frames = value["captures"]
    require(type(frames) is list and len(frames) == 16, "MISSING_MATRIX_STATES")
    names: set[str] = set()
    total_violations = 0
    for frame in frames:
        require(type(frame) is dict and set(frame) == FRAME_KEYS, "UNAPPROVED_FRAME_FIELDS")
        name = frame["name"]
        require(type(name) is str and name in EXPECTED and name not in names, "UNAPPROVED_DUPLICATE_FILENAME")
        names.add(name)
        w, h, font, state = EXPECTED[name]
        require((frame["width"], frame["height"], frame["fontScale"], frame["state"]) == (w, h, font, state),
                "VIEWPORT_FONT_STATE_MISMATCH")
        require(all(type(frame[key]) is int for key in ("width", "height", "fontScale", "densityDpi")), "NONINTEGER_GEOMETRY")
        require(frame["densityDpi"] == 160 and frame["rowCount"] == 20 and type(frame["rowCount"]) is int,
                "UNEXPECTED_DENSITY_OR_FIXTURE")
        require(integer(frame["rowsChecked"], 0, 20), "INVALID_ROW_MEASUREMENT")
        require(integer(frame["minTargetPx"], 1, 1180) and integer(frame["minRowHeightPx"], 1, 4096),
                "INVALID_CONTROL_MEASUREMENT")
        require(integer(frame["maxTextLines"], 0, 128) and integer(frame["pixelColors"], 8, 20000), "INVALID_TEXT_PIXEL_MEASUREMENT")
        require(integer(frame["violations"], 0, 10000), "INVALID_FRAME_VIOLATIONS")
        errors = frame["errors"]
        require(type(errors) is dict and set(errors) <= LAYOUT_ERRORS and
                all(integer(count, 1, 10000) for count in errors.values()) and
                sum(errors.values()) == frame["violations"], "UNAPPROVED_LAYOUT_ERROR_METADATA")
        for key in ("adapterSame", "savedDataPreserved", "windowFocused"):
            require(frame[key] is True, "STATE_OR_WINDOW_INVARIANT_FAILED")
        require(type(frame["scrollReachedEnd"]) is bool, "INVALID_SCROLL_MEASUREMENT")
        require(type(frame["touchScroll"]) is bool, "INVALID_TOUCH_MEASUREMENT")
        if outcome == "passed":
            require(frame["rowsChecked"] == 20 and frame["minTargetPx"] >= 48 and frame["minRowHeightPx"] >= 48
                    and frame["scrollReachedEnd"] and frame["touchScroll"] and frame["violations"] == 0,
                    "LAYOUT_DID_NOT_PASS")
        total_violations += frame["violations"]
    require(names == set(EXPECTED) and total_violations == value["violations"], "INCOMPLETE_OR_INCONSISTENT_MATRIX")
    return value


def png(data: bytes, expected: tuple[int, int]) -> None:
    require(0 < len(data) <= PNG_LIMIT and data[:8] == b"\x89PNG\r\n\x1a\n", "INVALID_OR_OVERSIZE_PNG")
    offset = 8
    chunks: list[tuple[bytes, bytes]] = []
    while offset < len(data):
        require(offset + 12 <= len(data), "TRUNCATED_PNG")
        length, kind = struct.unpack_from(">I4s", data, offset)
        require(length <= PNG_LIMIT and offset + 12 + length <= len(data), "INVALID_PNG_CHUNK_SIZE")
        payload = data[offset + 8:offset + 8 + length]
        crc = struct.unpack_from(">I", data, offset + 8 + length)[0]
        require(binascii.crc32(kind + payload) & 0xffffffff == crc, "PNG_CRC_MISMATCH")
        require(kind in {b"IHDR", b"IDAT", b"IEND", b"sRGB", b"sBIT", b"gAMA", b"cHRM", b"pHYs"},
                "UNAPPROVED_PNG_METADATA")
        chunks.append((kind, payload))
        require(len(chunks) <= 2048, "TOO_MANY_PNG_CHUNKS")
        offset += 12 + length
    require(chunks and chunks[0][0] == b"IHDR" and chunks[-1] == (b"IEND", b""), "PNG_STRUCTURE")
    require(sum(kind == b"IHDR" for kind, _ in chunks) == 1 and sum(kind == b"IEND" for kind, _ in chunks) == 1,
            "DUPLICATE_PNG_BOUNDARY")
    header = chunks[0][1]
    require(len(header) == 13, "PNG_HEADER")
    w, h, depth, color, compression, filtering, interlace = struct.unpack(">IIBBBBB", header)
    require((w, h) == expected, "PNG_DIMENSION_MISMATCH")
    require(depth == 8 and color in (2, 6) and (compression, filtering, interlace) == (0, 0, 0), "PNG_ENCODING")
    allowed_lengths = {b"sRGB": {1}, b"sBIT": {3, 4}, b"gAMA": {4}, b"cHRM": {32}, b"pHYs": {9}}
    for kind, payload in chunks:
        if kind in allowed_lengths:
            require(len(payload) in allowed_lengths[kind], "PNG_COLOR_METADATA_SIZE")
    compressed = b"".join(payload for kind, payload in chunks if kind == b"IDAT")
    expected_size = h * (1 + w * (3 if color == 2 else 4))
    decoder = zlib.decompressobj()
    raw = decoder.decompress(compressed, expected_size + 1)
    require(len(raw) == expected_size and decoder.eof and not decoder.unused_data and not decoder.unconsumed_tail,
            "PNG_PIXEL_STREAM")
    stride = expected_size // h
    require(all(raw[index] <= 4 for index in range(0, len(raw), stride)), "PNG_FILTER")


def budget(files: dict[str, bytes]) -> None:
    require(sum(map(len, files.values())) <= API_LIMIT, "API_EXPORT_EXCEEDS_8_MIB")


def bounded_command(arguments: list[str], limit: int, failure_code: int = 2) -> bytes:
    """Only callers supply fixed commands/paths. Bound stdout before retaining it."""
    try:
        with subprocess.Popen(arguments, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL) as process:
            try:
                data = bytearray()
                deadline = time.monotonic() + 30
                with selectors.DefaultSelector() as selector:
                    selector.register(process.stdout, selectors.EVENT_READ)
                    while True:
                        remaining = deadline - time.monotonic()
                        if remaining <= 0 or not selector.select(remaining):
                            raise GateError("TOOL_TIMEOUT", failure_code)
                        block = os.read(process.stdout.fileno(), min(65536, limit + 1 - len(data)))
                        if not block:
                            break
                        data.extend(block)
                        require(len(data) <= limit, "GUEST_EXPORT_TOO_LARGE")
                try:
                    process.wait(timeout=max(0.01, deadline - time.monotonic()))
                except subprocess.TimeoutExpired:
                    raise GateError("TOOL_TIMEOUT", failure_code) from None
                if process.returncode != 0:
                    raise GateError("GUEST_TOOL_OR_FIXED_FILE_UNAVAILABLE", failure_code)
                return bytes(data)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait()
    except FileNotFoundError:
        raise GateError("REQUIRED_TOOL_MISSING", 2) from None


def collect(result: Path, ordinary_result: Path, output: Path, head: str, tree: str) -> int:
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted",
            "PUBLIC_HOSTED_CI_REQUIRED")
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None and re.fullmatch(r"[0-9a-f]{40}", tree) is not None,
            "INVALID_SOURCE_RECEIPT")
    require(result.stat().st_size <= 256 * 1024, "PROTOCOL_TOO_LARGE")
    require(ordinary_result.stat().st_size <= 1024 * 1024, "SMOKE_PROTOCOL_TOO_LARGE")
    original_cases(ordinary_result.read_bytes())
    outcome = protocol(result.read_bytes())
    if outcome == "assertion_failed":
        print("CONTACT_VISUAL_CASE_ASSERTION_FAILURE", file=sys.stderr)
    require(bounded_command(["adb", "shell", "getprop", "ro.kernel.qemu"], 64).strip() == b"1", "QEMU_REQUIRED")
    sdk = bounded_command(["adb", "shell", "getprop", "ro.build.version.sdk"], 64).strip()
    require(sdk in (b"24", b"36"), "UNAPPROVED_API")
    api = int(sdk)
    require(output.name == f"contact-visual-api{api}" and not output.exists(), "REFUSE_STALE_EXPORT")
    prefix = ["adb", "exec-out", "run-as", PACKAGE]
    listing = bounded_command(prefix + ["ls", "-1", DIRECTORY], 4096, 3).decode("ascii").splitlines()
    require(len(listing) == 17 and set(listing) == set(EXPECTED) | {"matrix.json"}, "GUEST_FILE_ALLOWLIST")
    metadata = bounded_command(prefix + ["cat", f"{DIRECTORY}/matrix.json"], METADATA_LIMIT, 3)
    matrix(metadata, head, tree, api, outcome)
    files = {"matrix.json": metadata}
    for name, (w, h, _, _) in EXPECTED.items():
        data = bounded_command(prefix + ["cat", f"{DIRECTORY}/{name}"], PNG_LIMIT, 3)
        png(data, (w, h))
        files[name] = data
        budget(files)
    receipt = {"schema": 1, "head": head, "tree": tree, "api": api, "captureMode": "uiAutomation-window",
               "qualification": "passed" if outcome == "passed" else "failed", "caseCode": 0 if outcome == "passed" else -2,
               "preservationCasesPassed": 7,
               "files": {name: {"bytes": len(data), "sha256": hashlib.sha256(data).hexdigest()}
                         for name, data in files.items()}}
    files["source-receipt.json"] = json.dumps(receipt, sort_keys=True, separators=(",", ":")).encode()
    budget(files)
    output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=".contact-visual-", dir=output.parent) as temporary:
        staging = Path(temporary) / "validated"
        staging.mkdir()
        for name, data in files.items():
            with (staging / name).open("xb") as target:
                target.write(data)
        staging.rename(output)
    return 0 if outcome == "passed" else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--result", type=Path, required=True)
    parser.add_argument("--ordinary-result", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--head", required=True)
    parser.add_argument("--tree", required=True)
    args = parser.parse_args()
    try:
        code = collect(args.result, args.ordinary_result, args.output, args.head, args.tree)
        print("CONTACT_VISUAL_PASS" if code == 0 else "CONTACT_VISUAL_ASSERTION_FAILURE")
        return code
    except GateError as error:
        print(f"CONTACT_VISUAL_REJECTED:{error}", file=sys.stderr)
        return error.code
    except (OSError, ValueError, UnicodeError, zlib.error, struct.error):
        print("CONTACT_VISUAL_REJECTED:INPUT_OR_TOOL_FAILURE", file=sys.stderr)
        return 2


if __name__ == "__main__":
    sys.exit(main())
