#!/usr/bin/env python3
"""Export a private, inactive tester-code handoff bundle from reviewed staging."""

import argparse
import csv
import hashlib
import io
import json
import os
from pathlib import Path
import re
import sqlite3
import sys
import tempfile
from typing import Any
from urllib.parse import quote

from prepare_tester_staging import private_path


HEX = re.compile(r"^[a-f0-9]{64}$")
CODE = re.compile(r"^SC-PREM-[A-F0-9]{48}$")
DELIVERY_NAME = "tester-delivery-draft.csv"
REGISTRY_NAME = "tester-runtime-registry-inactive.json"
MANIFEST_NAME = "tester-handoff-manifest.json"


def digest(value: bytes) -> str:
    return hashlib.sha256(value).hexdigest()


def grant_id(code_hash: str) -> str:
    return digest(f"securecall-tester-grant-v1\n{code_hash}".encode("ascii"))


def load_staged(database: Path) -> list[dict[str, str]]:
    private_path(database)
    connection = sqlite3.connect(f"file:{quote(str(database))}?mode=ro", uri=True)
    try:
        connection.execute("PRAGMA query_only=ON")
        columns = [row[1] for row in connection.execute("PRAGMA table_info(gifts)")]
        if columns != ["ref", "email", "delivery_code", "code_hash", "status", "qualification"]:
            raise ValueError("Unexpected private staging schema")
        rows = connection.execute(
            "SELECT ref,email,delivery_code,code_hash,status,qualification FROM gifts ORDER BY ref"
        ).fetchall()
    finally:
        connection.close()
    if not rows:
        raise ValueError("No inactive tester records to export")

    seen_codes: set[str] = set()
    seen_refs: set[str] = set()
    staged: list[dict[str, str]] = []
    for ref, email, code, code_hash, status, qualification in rows:
        if (not isinstance(ref, str) or not HEX.fullmatch(ref) or ref in seen_refs
                or not isinstance(email, str) or email != email.strip().lower()
                or email.count("@") != 1 or len(email) > 254 or any(char.isspace() for char in email)
                or not isinstance(code, str) or not CODE.fullmatch(code) or code in seen_codes
                or not isinstance(code_hash, str) or not HEX.fullmatch(code_hash)
                or digest(code.encode("ascii")) != code_hash
                or status != "inactive" or qualification != "unconfirmed"):
            raise ValueError("Invalid private staging record")
        seen_refs.add(ref)
        seen_codes.add(code)
        staged.append({"ref": ref, "email": email, "code": code, "code_hash": code_hash,
                       "grant_id": grant_id(code_hash)})
    return staged


def delivery_payload(staged: list[dict[str, str]]) -> bytes:
    stream = io.StringIO(newline="")
    writer = csv.DictWriter(stream, fieldnames=[
        "email", "activation_code", "recipient_ref", "grant_id", "delivery_status"
    ], lineterminator="\n")
    writer.writeheader()
    for record in staged:
        writer.writerow({"email": record["email"], "activation_code": record["code"],
                         "recipient_ref": record["ref"], "grant_id": record["grant_id"],
                         "delivery_status": "draft_do_not_send"})
    return stream.getvalue().encode("utf-8")


def registry_payload(staged: list[dict[str, str]]) -> bytes:
    payload: dict[str, Any] = {
        "schema": 1,
        "grants": [{"id": record["grant_id"], "codeHash": record["code_hash"],
                    "status": "inactive", "binding": None} for record in staged],
        "enrolledKeys": [],
    }
    return (json.dumps(payload, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")


def publish_bundle(directory: Path, payloads: dict[str, bytes]) -> None:
    private_path(directory, directory=True)
    lock = directory / ".tester-handoff-export.lock"
    try:
        lock_descriptor = os.open(lock, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
    except FileExistsError as error:
        raise ValueError("Private export is already running or requires operator recovery") from error
    os.close(lock_descriptor)
    temporary: list[Path] = []
    published: list[Path] = []
    try:
        existing = []
        for name, content in payloads.items():
            target = directory / name
            if target.exists() or target.is_symlink():
                private_path(target)
                existing.append(target.read_bytes() == content)
            else:
                existing.append(False)
        if all(existing):
            return
        if any(existing) or any((directory / name).exists() or (directory / name).is_symlink()
                                for name in payloads):
            raise ValueError("Existing private handoff differs or is incomplete")

        for name, content in payloads.items():
            descriptor, temporary_name = tempfile.mkstemp(prefix=f".{name}-", dir=directory)
            temp = Path(temporary_name)
            temporary.append(temp)
            os.fchmod(descriptor, 0o600)
            with os.fdopen(descriptor, "wb") as stream:
                stream.write(content)
                stream.flush()
                os.fsync(stream.fileno())
        for (name, _content), temp in zip(payloads.items(), temporary, strict=True):
            target = directory / name
            os.link(temp, target)
            published.append(target)
        folder = os.open(directory, os.O_RDONLY)
        try:
            os.fsync(folder)
        finally:
            os.close(folder)
    except Exception:
        for target in published:
            target.unlink(missing_ok=True)
        raise
    finally:
        for temp in temporary:
            temp.unlink(missing_ok=True)
        lock.unlink(missing_ok=True)


def export_draft(database: Path, directory: Path) -> dict[str, int | str]:
    staged = load_staged(database)
    delivery = delivery_payload(staged)
    registry = registry_payload(staged)
    manifest = {
        "schema": 1,
        "status": "draft_do_not_send_or_activate",
        "record_count": len(staged),
        "delivery_sha256": digest(delivery),
        "runtime_registry_sha256": digest(registry),
        "requirements": [
            "exact_signed_direct_premium_artifact",
            "two_device_acceptance",
            "owner_delivery_approval",
            "separate_production_registry_import_approval",
        ],
    }
    manifest_bytes = (json.dumps(manifest, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")
    publish_bundle(directory, {
        DELIVERY_NAME: delivery,
        REGISTRY_NAME: registry,
        MANIFEST_NAME: manifest_bytes,
    })
    return {"records": len(staged), "status": "draft_do_not_send_or_activate"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--staging-database", type=Path, required=True)
    parser.add_argument("--private-output-directory", type=Path, required=True)
    args = parser.parse_args()
    try:
        result = export_draft(args.staging_database, args.private_output_directory)
    except Exception:
        print("Export rejected; inspect private staging and output storage locally.", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
