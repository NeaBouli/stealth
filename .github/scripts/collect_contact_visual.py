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
PHASES = {
    "ADMIT_OPT_IN", "ADMIT_BUILD", "ADMIT_API", "ADMIT_HARDWARE", "ADMIT_QEMU", "ADMIT_FREE", "SOURCE_RECEIPT",
    "FIXTURE_SETUP", "FIXTURE_CLEANUP", "INITIAL_STORE", "CAPTURE_DIRECTORY", "DISPLAY_SNAPSHOT",
    "DISPLAY_OVERRIDE", "HOST_LAUNCH", "STORAGE_SEED", "RENDER_BEFORE", "PARTIAL_SNAPSHOT", "SAVED_DATA",
    "LIST_READY", "ADAPTER_STATE", "TOUCH_SCROLL", "ROW_LAYOUT", "CONTROL_LAYOUT", "WINDOW_SECURE",
    "WINDOW_FOCUS", "WINDOW_FREE", "WINDOW_METRICS", "WINDOW_FONT", "WINDOW_BOUNDS", "SCREENSHOT",
    "SCREENSHOT_SIZE", "SCREENSHOT_PIXELS", "PNG_ENCODE", "PNG_BUDGET", "PNG_WRITE", "API_BUDGET",
    "MATRIX_LOOP", "HOST_CLOSE", "DISPLAY_RESTORE", "CAPTURE_CLEANUP", "METADATA", "CAPTURE_COUNT", "LAYOUT_ASSERTION",
}
DIAGNOSTIC_KEYS = {"sc241_phase", "sc241_failure"}


class GateError(Exception):
    def __init__(self, label: str, code: int = 3):
        super().__init__(label)
        self.code = code


def require(condition: bool, label: str) -> None:
    if not condition:
        raise GateError(label)


def diagnostic_fields(value: dict) -> tuple[str, str]:
    require(set(value) == DIAGNOSTIC_KEYS, "UNAPPROVED_DIAGNOSTIC_FIELDS")
    phase, failure = value["sc241_phase"], value["sc241_failure"]
    require(type(phase) is str and phase in PHASES, "UNAPPROVED_DIAGNOSTIC_PHASE")
    require(type(failure) is str and failure in {"ASSERTION", "EXECUTION"}, "UNAPPROVED_DIAGNOSTIC_FAILURE")
    return phase, failure


def status_code(line: str) -> int:
    value = line.split(": ", 1)[1]
    if re.fullmatch(r"-?[0-9]{1,3}", value) is None:
        raise GateError("INVALID_INSTRUMENTATION_STATUS_CODE", 2)
    return int(value)


def execution(data: bytes) -> tuple[str, list[dict]]:
    require(len(data) <= 256 * 1024, "PROTOCOL_TOO_LARGE")
    text = data.decode("utf-8", errors="strict")
    block: dict[str, str] = {}
    events: list[int] = []
    terminal: list[int] = []
    diagnostics: list[dict] = []
    duplicate = False
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator:
                duplicate = duplicate or key in block
                block[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = status_code(line)
            if code == 2 or any(key.startswith("sc241_") for key in block):
                require(code == 2 and events == [1] and not terminal and not duplicate,
                        "DIAGNOSTIC_OUTSIDE_ACTIVE_CASE")
                diagnostic_fields(block)
                require(len(diagnostics) < 4, "DIAGNOSTIC_COUNT_EXCEEDED")
                diagnostics.append(block)
                block = {}
                duplicate = False
                continue
            require((block.get("class"), block.get("test")) == CASE, "UNEXPECTED_OR_MISSING_CASE")
            require(block.get("numtests") == "1", "VISUAL_REQUIRES_EXACTLY_ONE_TEST")
            events.append(code)
            block = {}
            duplicate = False
        elif line.startswith("INSTRUMENTATION_CODE: "):
            terminal.append(status_code(line))
    require(not any(key.startswith("sc241_") for key in block), "UNTERMINATED_DIAGNOSTIC")
    if terminal != [-1]:
        raise GateError("INSTRUMENTATION_DID_NOT_COMPLETE", 2)
    if events == [1, 0]:
        require(not diagnostics, "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        require(re.search(r"^OK \(1 test\)$", text, re.MULTILINE) is not None, "SUCCESS_SUMMARY_MISSING")
        return "passed", diagnostics
    if events == [1, -2]:
        return "assertion_failed", diagnostics
    if events == [1, -1]:
        return "execution_failed", diagnostics
    if not events:
        raise GateError("VISUAL_CASE_DID_NOT_START", 2)
    raise GateError("VISUAL_CASE_SKIPPED_MISSING_OR_FAILED_TO_START")


def protocol(data: bytes) -> str:
    outcome, _ = execution(data)
    if outcome == "execution_failed":
        raise GateError("VISUAL_EXECUTION_FAILURE", 2)
    return outcome


def failure_labels(outcome: str, diagnostics: list[dict]) -> list[str]:
    require(outcome in {"passed", "assertion_failed", "execution_failed"}, "UNAPPROVED_EXECUTION_OUTCOME")
    require(len(diagnostics) <= 4, "DIAGNOSTIC_COUNT_EXCEEDED")
    if outcome == "passed":
        require(not diagnostics, "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        return []
    if not diagnostics:
        kind = "ASSERTION" if outcome == "assertion_failed" else "EXECUTION"
        return [f"CONTACT_VISUAL_FAILURE:{kind}:UNREPORTED"]
    return [f"CONTACT_VISUAL_FAILURE:{failure}:{phase}"
            for phase, failure in map(diagnostic_fields, diagnostics)]


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
    outcome, diagnostics = execution(result.read_bytes())
    if outcome != "passed":
        print(f"CONTACT_VISUAL_DIAGNOSTIC_SOURCE:{head}:{tree}", file=sys.stderr)
    for label in failure_labels(outcome, diagnostics):
        print(label, file=sys.stderr)
    if outcome == "assertion_failed":
        print("CONTACT_VISUAL_CASE_ASSERTION_FAILURE", file=sys.stderr)
    if outcome == "execution_failed":
        raise GateError("VISUAL_EXECUTION_FAILURE", 2)
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


ROOT_CASE = ("com.securecall.app.ContactLookupPreservationInstrumentedTest",
             "explicitUserDeleteStillRequiresConfirmationAndRemovesOnlyChosenContact")
ROOT_PREFIX = "sc241_root_"
ROOT_SLOTS = 4
ROOT_CANDIDATES = 8
ROOT_TRI = {"true", "false", "UNKNOWN"}
ROOT_BOOL = {"true", "false"}
ROOT_CLASS = re.compile(r"[A-Za-z_][A-Za-z0-9_]{0,63}")
ROOT_FIXED = (
    {"head", "tree", "phase", "selected", "candidate_count", "candidates_incomplete", "exception_class",
     "cause_count", "cause_incomplete", "suppressed_count", "suppressed_incomplete", "host_started",
     "host_resumed", "host_destroyed", "host_finishing", "host_focus", "interactive", "keyguard_locked",
     "device_locked"}
    | {f"candidate_{i}_{k}" for i in range(ROOT_CANDIDATES)
       for k in ("type", "attached", "focus", "synthetic_title", "host_context")}
)


def root_fields(block: dict, head: str, tree: str) -> dict:
    """Closed sc241_root_* whitelist: fixed tokens, counts and simple class names only."""
    require(all(key.startswith(ROOT_PREFIX) for key in block), "UNAPPROVED_ROOT_FIELDS")
    value = {key.removeprefix(ROOT_PREFIX): item for key, item in block.items()}
    for key in ("cause_count", "suppressed_count"):
        require(re.fullmatch(r"-?[0-9]", value.get(key, "")) is not None, "UNAPPROVED_ROOT_COUNT")
    causes, suppressed = int(value["cause_count"]), int(value["suppressed_count"])
    require(0 <= causes <= ROOT_SLOTS and -1 <= suppressed <= ROOT_SLOTS, "UNAPPROVED_ROOT_COUNT")
    allowed = (ROOT_FIXED | {f"cause_{i}_class" for i in range(causes)}
               | {f"suppressed_{i}_class" for i in range(max(suppressed, 0))})
    require(set(value) == allowed, "UNAPPROVED_ROOT_FIELDS")
    require(value["head"] == head and value["tree"] == tree, "ROOT_SOURCE_RECEIPT_MISMATCH")
    require(value["phase"] == "MENU" and value["selected"] == "UNKNOWN", "UNAPPROVED_ROOT_PHASE")
    require(re.fullmatch(r"[0-8]", value["candidate_count"]) is not None, "UNAPPROVED_ROOT_COUNT")
    for key in ("candidates_incomplete", "cause_incomplete", "suppressed_incomplete"):
        require(value[key] in ROOT_BOOL, "UNAPPROVED_ROOT_VALUE")
    for key, item in value.items():
        if key.endswith("_class"):
            require(item == "UNKNOWN" or ROOT_CLASS.fullmatch(item) is not None, "UNAPPROVED_ROOT_CLASS")
        elif key.endswith("_type"):
            require(re.fullmatch(r"-1|[1-9][0-9]{0,3}", item) is not None
                    and (item == "-1" or int(item) <= 2999), "UNAPPROVED_ROOT_VALUE")
        elif key not in {"head", "tree", "phase", "selected", "candidate_count", "cause_count", "suppressed_count",
                         "candidates_incomplete", "cause_incomplete", "suppressed_incomplete"}:
            require(item in ROOT_TRI, "UNAPPROVED_ROOT_VALUE")
    return value


def root_execution(data: bytes, head: str, tree: str) -> tuple[str, list[dict]]:
    """Exactly one executed ROOT_CASE; at most one root status inside it. Stack/message never read."""
    require(len(data) <= 256 * 1024, "PROTOCOL_TOO_LARGE")
    text = data.decode("utf-8", errors="strict")
    block: dict[str, str] = {}
    events: list[int] = []
    terminal: list[int] = []
    diagnostics: list[dict] = []
    duplicate = False
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS"):
            require(not terminal, "ROOT_STATUS_AFTER_TERMINAL")
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator:
                duplicate = duplicate or key in block
                block[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = status_code(line)
            if code == 2 or any(key.startswith("sc241_") for key in block):
                require(code == 2 and events == [1] and not terminal and not duplicate,
                        "ROOT_DIAGNOSTIC_OUTSIDE_ACTIVE_CASE")
                require(not diagnostics, "ROOT_DIAGNOSTIC_COUNT_EXCEEDED")
                diagnostics.append(root_fields(block, head, tree))
            else:
                require(not duplicate, "ROOT_DUPLICATE_STATUS_FIELD")
                require((block.get("class"), block.get("test")) == ROOT_CASE, "UNEXPECTED_OR_MISSING_ROOT_CASE")
                require(block.get("numtests") == "1", "ROOT_REQUIRES_EXACTLY_ONE_TEST")
                require(len(events) < 2, "ROOT_REQUIRES_EXACTLY_ONE_TEST")
                events.append(code)
            block = {}
            duplicate = False
        elif line.startswith("INSTRUMENTATION_CODE: "):
            require(not terminal, "ROOT_MULTIPLE_TERMINAL_MARKERS")
            require(not any(key.startswith("sc241_") for key in block), "UNTERMINATED_DIAGNOSTIC")
            require(not block, "ROOT_INCOMPLETE_STATUS_BLOCK")
            terminal.append(status_code(line))
    require(not any(key.startswith("sc241_") for key in block), "UNTERMINATED_DIAGNOSTIC")
    require(not block, "ROOT_INCOMPLETE_STATUS_BLOCK")
    if terminal != [-1]:
        raise GateError("INSTRUMENTATION_DID_NOT_COMPLETE", 2)
    if not events:
        raise GateError("ROOT_CASE_DID_NOT_START", 2)
    if events == [1, 0]:
        require(not diagnostics, "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        require(re.search(r"^OK \(1 test\)$", text, re.MULTILINE) is not None, "SUCCESS_SUMMARY_MISSING")
        return "passed", diagnostics
    if events == [1, -2]:
        return "assertion_failed", diagnostics
    if events == [1, -1]:
        return "execution_failed", diagnostics
    raise GateError("ROOT_CASE_SKIPPED_MISSING_OR_FAILED_TO_START")


def collect_root(result: Path, head: str, tree: str) -> int:
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted",
            "PUBLIC_HOSTED_CI_REQUIRED")
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None and re.fullmatch(r"[0-9a-f]{40}", tree) is not None,
            "INVALID_SOURCE_RECEIPT")
    require(result.stat().st_size <= 256 * 1024, "PROTOCOL_TOO_LARGE")
    outcome, diagnostics = root_execution(result.read_bytes(), head, tree)
    print(f"SC241_ROOT_CASE:{ROOT_CASE[1]}:{outcome}:{head}:{tree}")
    for value in diagnostics:
        for key in sorted(value):
            if key not in {"head", "tree"}:
                print(f"SC241_ROOT_FIELD:{key}={value[key]}")
    if outcome != "passed" and not diagnostics:
        print("SC241_ROOT_FIELD:UNREPORTED")
    return 0 if outcome == "passed" else 1


MAIN_CLASS = "com.securecall.app.MainActivityInstrumentedTest"
FOCUS_CASES = (
    (MAIN_CLASS, "externalVpnIndicator_matchesCurrentTransport"),
    (MAIN_CLASS, "settingsNavigationItem_click_doesNotCrash"),
    (MAIN_CLASS, "bottomNavigation_isDisplayed"),
    (MAIN_CLASS, "toolbar_isDisplayed"),
    ROOT_CASE,
)
FOCUS_SELECTOR = ",".join(f"{klass}#{test}" for klass, test in FOCUS_CASES)
FOCUS_FINISH = {0: "passed", -2: "assertion_failed", -1: "execution_failed"}
# SC242 failures-only subset: the three previously failed MainActivity cases, fixed order.
FAILURE_CASES = FOCUS_CASES[:3]
FAILURE_SELECTOR = ",".join(f"{klass}#{test}" for klass, test in FAILURE_CASES)
MAIN_PREFIX = "sc242_main_"
MAIN_PHASES = {"SETUP", "LAUNCH", "CLICK", "CHECK", "CLOSE"}
MAIN_FIXED = {"head", "tree", "method", "phase", "exception_class", "cause_count", "cause_incomplete"}


def main_fields(block: dict, head: str, tree: str, method: str) -> dict:
    """Closed sc242_main_* whitelist bound to the active failed case. Never message/stack."""
    require(all(key.startswith(MAIN_PREFIX) for key in block), "UNAPPROVED_MAIN_FIELDS")
    value = {key.removeprefix(MAIN_PREFIX): item for key, item in block.items()}
    require(re.fullmatch(r"[0-4]", value.get("cause_count", "")) is not None, "UNAPPROVED_MAIN_COUNT")
    causes = int(value["cause_count"])
    require(set(value) == MAIN_FIXED | {f"cause_{i}_class" for i in range(causes)}, "UNAPPROVED_MAIN_FIELDS")
    require(value["head"] == head and value["tree"] == tree, "MAIN_SOURCE_RECEIPT_MISMATCH")
    require(value["method"] == method, "MAIN_METHOD_MISMATCH")
    require(value["phase"] in MAIN_PHASES, "UNAPPROVED_MAIN_PHASE")
    require(value["cause_incomplete"] in ROOT_BOOL, "UNAPPROVED_MAIN_VALUE")
    for key, item in value.items():
        if key.endswith("_class"):
            require(item == "UNKNOWN" or ROOT_CLASS.fullmatch(item) is not None, "UNAPPROVED_MAIN_CLASS")
    return value


def focus_execution(data: bytes, head: str, tree: str,
                    cases: tuple = FOCUS_CASES) -> tuple[dict, list[dict]]:
    """Exactly the fixed cases, each started once and finished once, sequentially.

    Five-case mode: only the contact root case may carry one closed sc241_root_* status.
    Failures-only mode: each failed Main case may carry one closed sc242_main_* status.
    Stack/message never read.
    """
    require(cases in (FOCUS_CASES, FAILURE_CASES), "UNAPPROVED_FOCUS_PROFILE")
    failures_only = cases == FAILURE_CASES
    prefixes = ("sc241_", MAIN_PREFIX) if failures_only else ("sc241_",)
    require(len(data) <= 512 * 1024, "PROTOCOL_TOO_LARGE")
    text = data.decode("utf-8", errors="strict")
    block: dict[str, str] = {}
    outcomes: dict[tuple[str, str], str] = {}
    started: list[tuple[str, str]] = []
    active: tuple[str, str] | None = None
    terminal: list[int] = []
    diagnostics: list[dict] = []
    duplicate = False
    for line in text.splitlines():
        if line.startswith("INSTRUMENTATION_STATUS"):
            require(not terminal, "FOCUS_STATUS_AFTER_TERMINAL")
        if line.startswith("INSTRUMENTATION_STATUS: "):
            key, separator, value = line.removeprefix("INSTRUMENTATION_STATUS: ").partition("=")
            if separator:
                duplicate = duplicate or key in block
                block[key] = value
        elif line.startswith("INSTRUMENTATION_STATUS_CODE: "):
            code = status_code(line)
            if code == 2 or any(key.startswith(prefixes) for key in block):
                if failures_only:
                    require(code == 2 and active in cases and not duplicate,
                            "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_MAIN_CASE")
                    require(all(item["method"] != active[1] for item in diagnostics),
                            "FOCUS_DIAGNOSTIC_COUNT_EXCEEDED")
                    diagnostics.append(main_fields(block, head, tree, active[1]))
                else:
                    require(code == 2 and active == ROOT_CASE and not duplicate,
                            "FOCUS_DIAGNOSTIC_OUTSIDE_ACTIVE_ROOT_CASE")
                    require(not diagnostics, "FOCUS_DIAGNOSTIC_COUNT_EXCEEDED")
                    diagnostics.append(root_fields(block, head, tree))
            else:
                require(not duplicate, "FOCUS_DUPLICATE_STATUS_FIELD")
                case = (block.get("class"), block.get("test"))
                require(case in cases, "UNEXPECTED_OR_MISSING_FOCUS_CASE")
                require(block.get("numtests") == str(len(cases)),
                        "FOCUS_REQUIRES_EXACTLY_THREE_TESTS" if failures_only else "FOCUS_REQUIRES_EXACTLY_FIVE_TESTS")
                if code == 1:
                    require(active is None, "FOCUS_CASE_ORDER_OR_INCOMPLETE")
                    require(case not in started, "FOCUS_DUPLICATE_CASE")
                    started.append(case)
                    require(block.get("current") == str(len(started)), "FOCUS_CASE_ORDER_OR_INCOMPLETE")
                    active = case
                else:
                    require(active == case and block.get("current") == str(len(started)),
                            "FOCUS_CASE_ORDER_OR_INCOMPLETE")
                    require(code in FOCUS_FINISH, "FOCUS_CASE_SKIPPED_OR_UNAPPROVED_STATUS")
                    outcomes[case] = FOCUS_FINISH[code]
                    active = None
            block = {}
            duplicate = False
        elif line.startswith("INSTRUMENTATION_CODE: "):
            require(not terminal, "FOCUS_MULTIPLE_TERMINAL_MARKERS")
            require(not block, "FOCUS_INCOMPLETE_STATUS_BLOCK")
            terminal.append(status_code(line))
    require(not block, "FOCUS_INCOMPLETE_STATUS_BLOCK")
    if terminal != [-1]:
        raise GateError("INSTRUMENTATION_DID_NOT_COMPLETE", 2)
    if not started:
        raise GateError("FOCUS_CASES_DID_NOT_START", 2)
    require(active is None, "FOCUS_CASE_ORDER_OR_INCOMPLETE")
    require(set(started) == set(cases) and set(outcomes) == set(cases), "FOCUS_MISSING_CASE")
    if all(outcome == "passed" for outcome in outcomes.values()):
        require(not diagnostics, "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
        require(re.search(rf"^OK \({len(cases)} tests\)$", text, re.MULTILINE) is not None,
                "SUCCESS_SUMMARY_MISSING")
    else:
        require(re.search(r"^OK \(", text, re.MULTILINE) is None, "FALSE_SUCCESS_SUMMARY")
    if diagnostics and failures_only:
        require(all(outcomes[(MAIN_CLASS, item["method"])] != "passed" for item in diagnostics),
                "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
    elif diagnostics:
        require(outcomes[ROOT_CASE] != "passed", "FAILURE_DIAGNOSTIC_WITH_SUCCESS")
    return outcomes, diagnostics


def collect_focus(result: Path, head: str, tree: str, cases: tuple = FOCUS_CASES) -> int:
    require(os.environ.get("GITHUB_ACTIONS") == "true" and os.environ.get("RUNNER_ENVIRONMENT") == "github-hosted",
            "PUBLIC_HOSTED_CI_REQUIRED")
    require(re.fullmatch(r"[0-9a-f]{40}", head) is not None and re.fullmatch(r"[0-9a-f]{40}", tree) is not None,
            "INVALID_SOURCE_RECEIPT")
    require(result.stat().st_size <= 512 * 1024, "PROTOCOL_TOO_LARGE")
    outcomes, diagnostics = focus_execution(result.read_bytes(), head, tree, cases)
    print(f"SC242_FOCUS_SOURCE:{head}:{tree}")
    for klass, test in cases:
        print(f"SC242_FOCUS_CASE:{klass.rsplit('.', 1)[1]}#{test}:{outcomes[(klass, test)]}")
    if cases == FAILURE_CASES:
        reported = {item["method"]: item for item in diagnostics}
        for _, test in cases:
            if outcomes[(MAIN_CLASS, test)] == "passed":
                continue
            if test not in reported:
                print(f"SC242_FAILURE_FIELD:{test}:UNREPORTED")
                continue
            for key in ("phase", "exception_class", "cause_count", "cause_incomplete"):
                print(f"SC242_FAILURE_FIELD:{test}:{key}={reported[test][key]}")
            for i in range(int(reported[test]["cause_count"])):
                print(f"SC242_FAILURE_FIELD:{test}:cause_{i}_class={reported[test][f'cause_{i}_class']}")
        passed = sum(outcome == "passed" for outcome in outcomes.values())
        print(f"SC242_FOCUS_RESULT:passed={passed}:failed={len(cases) - passed}")
        return 0 if passed == len(cases) else 1
    for value in diagnostics:
        for key in sorted(value):
            if key not in {"head", "tree"}:
                print(f"SC241_ROOT_FIELD:{key}={value[key]}")
    if outcomes[ROOT_CASE] != "passed" and not diagnostics:
        print("SC241_ROOT_FIELD:UNREPORTED")
    passed = sum(outcome == "passed" for outcome in outcomes.values())
    print(f"SC242_FOCUS_RESULT:passed={passed}:failed={len(FOCUS_CASES) - passed}")
    return 0 if passed == len(FOCUS_CASES) else 1


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--result", type=Path, required=True)
    parser.add_argument("--ordinary-result", type=Path)
    parser.add_argument("--output", type=Path)
    parser.add_argument("--head", required=True)
    parser.add_argument("--tree", required=True)
    parser.add_argument("--root-probe", action="store_true")
    parser.add_argument("--focus-probe", action="store_true")
    parser.add_argument("--focus-failures-only", action="store_true")
    args = parser.parse_args()
    if args.focus_failures_only and not args.focus_probe:
        print("SC242_FOCUS_REJECTED:MIXED_COLLECTOR_MODE", file=sys.stderr)
        return 2
    if args.focus_probe:
        if args.root_probe or args.ordinary_result is not None or args.output is not None:
            print("SC242_FOCUS_REJECTED:MIXED_COLLECTOR_MODE", file=sys.stderr)
            return 2
        try:
            cases = FAILURE_CASES if args.focus_failures_only else FOCUS_CASES
            return collect_focus(args.result, args.head, args.tree, cases)
        except GateError as error:
            print(f"SC242_FOCUS_REJECTED:{error}", file=sys.stderr)
            return error.code
        except (OSError, ValueError, UnicodeError):
            print("SC242_FOCUS_REJECTED:INPUT_OR_TOOL_FAILURE", file=sys.stderr)
            return 2
    if args.root_probe:
        if args.ordinary_result is not None or args.output is not None:
            print("SC241_ROOT_REJECTED:MIXED_COLLECTOR_MODE", file=sys.stderr)
            return 2
        try:
            return collect_root(args.result, args.head, args.tree)
        except GateError as error:
            print(f"SC241_ROOT_REJECTED:{error}", file=sys.stderr)
            return error.code
        except (OSError, ValueError, UnicodeError):
            print("SC241_ROOT_REJECTED:INPUT_OR_TOOL_FAILURE", file=sys.stderr)
            return 2
    if args.ordinary_result is None or args.output is None:
        print("CONTACT_VISUAL_REJECTED:MISSING_VISUAL_ARGUMENTS", file=sys.stderr)
        return 2
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
