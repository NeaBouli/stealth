#!/usr/bin/env python3
"""Private inactive gift staging only; never a runtime license-store writer."""

import argparse
import csv
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import sqlite3
import stat
import sys
import tempfile
from typing import Any


def private_path(path: Path, *, directory: bool = False, readable_source: bool = False) -> None:
    if not path.is_absolute():
        raise ValueError("Absolute private path required")
    for component in (path, *path.parents):
        if component.is_symlink() or (component / ".git").exists():
            raise ValueError("Symlink or Git checkout is not a private staging location")
    info = path.stat()
    allowed_modes = {0o700} if directory else ({0o600, 0o644} if readable_source else {0o600})
    kind_ok = stat.S_ISDIR(info.st_mode) if directory else stat.S_ISREG(info.st_mode)
    if not kind_ok or info.st_uid != os.getuid() or stat.S_IMODE(info.st_mode) not in allowed_modes:
        raise ValueError("Private path ownership or permissions invalid")
    if not directory and info.st_nlink != 1:
        raise ValueError("Hard-linked input or store rejected")


def reference(email: str, pepper: str) -> str:
    return hmac.new(pepper.encode(), email.encode(), hashlib.sha256).hexdigest()


def read_candidates(source: Path, *, readable_source: bool = False) -> list[str]:
    private_path(source, readable_source=readable_source)
    with source.open(newline="", encoding="utf-8-sig") as stream:
        reader = csv.DictReader(stream)
        if reader.fieldnames is None or not {"email", "list"}.issubset(reader.fieldnames):
            raise ValueError("Required CSV columns: email,list")
        selected: set[str] = set()
        for row in reader:
            if row.get("list", "").strip() != "SecureCall \u03b2-test":
                continue
            email = row.get("email", "").strip().lower()
            if email.count("@") != 1 or any(c.isspace() for c in email) or len(email) > 254:
                raise ValueError("Invalid candidate address")
            local, domain = email.split("@")
            if not local or "." not in domain or domain.startswith(".") or domain.endswith("."):
                raise ValueError("Invalid candidate address")
            selected.add(email)
    if not selected:
        raise ValueError("No exact-list candidates; refusing empty preparation")
    return sorted(selected)


def intake(source: Path, directory: Path) -> dict[str, int | str]:
    """Separate recipient intake: not an entitlement or an authorization to issue."""
    private_path(directory, directory=True)
    candidates = read_candidates(source, readable_source=True)
    payload = {"schema": 1, "status": "awaiting_lead_reconciliation",
               "codes_generated": 0, "recipients": candidates}
    target = directory / "recipient-intake.json"
    if target.exists() or target.is_symlink():
        private_path(target)
        if json.loads(target.read_text(encoding="utf-8")) != payload:
            raise ValueError("Existing intake differs; reconcile instead of overwriting")
        return {"selected": len(candidates), "codes_generated": 0, "status": "awaiting_lead_reconciliation"}
    descriptor, name = tempfile.mkstemp(prefix=".intake-", dir=directory)
    temporary = Path(name)
    try:
        with os.fdopen(descriptor, "w", encoding="utf-8") as stream:
            json.dump(payload, stream, ensure_ascii=True, sort_keys=True)
            stream.flush()
            os.fsync(stream.fileno())
        # Exclusive publish: concurrent imports must never overwrite another snapshot.
        try:
            os.link(temporary, target)
        except FileExistsError:
            raise ValueError("Concurrent intake exists; retry after it completes") from None
    finally:
        temporary.unlink(missing_ok=True)
    folder = os.open(directory, os.O_RDONLY)
    try:
        os.fsync(folder)
    finally:
        os.close(folder)
    private_path(target)
    return {"selected": len(candidates), "codes_generated": 0, "status": "awaiting_lead_reconciliation"}


def prepare(source: Path, inventory: Path, directory: Path, pepper: str) -> dict[str, int]:
    """Caller supplies operator-checked inventory; no inferred gift eligibility."""
    if len(pepper) < 32:
        raise ValueError("Dedicated private reference pepper required")
    private_path(directory, directory=True)
    private_path(inventory)
    candidates = read_candidates(source)
    snapshot: Any = json.loads(inventory.read_text(encoding="utf-8"))
    fingerprint = hashlib.sha256(pepper.encode()).hexdigest()
    if (not isinstance(snapshot, dict) or snapshot.get("schema") != 1
            or snapshot.get("complete") is not True
            or snapshot.get("reference_key_fingerprint") != fingerprint
            or not isinstance(snapshot.get("recipient_refs"), list)):
        raise ValueError("Complete matching operator inventory required")
    refs = snapshot["recipient_refs"]
    if any(not isinstance(r, str) or len(r) != 64
           or any(c not in "0123456789abcdef" for c in r) for r in refs):
        raise ValueError("Invalid inventory reference")
    existing = set(refs)
    database = directory / "inactive-tester-staging.sqlite3"
    # Caller-owned mode700 directory prevents other local users replacing entries.
    try:
        descriptor = os.open(database, os.O_CREAT | os.O_EXCL | os.O_WRONLY | os.O_NOFOLLOW, 0o600)
    except FileExistsError:
        private_path(database)
    else:
        os.close(descriptor)
    private_path(database)
    db = sqlite3.connect(database, timeout=10, isolation_level=None)
    try:
        db.execute("PRAGMA journal_mode=DELETE")
        db.execute("PRAGMA synchronous=FULL")
        db.execute("BEGIN IMMEDIATE")
        db.execute("CREATE TABLE IF NOT EXISTS metadata (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execute("CREATE TABLE IF NOT EXISTS gifts (ref TEXT PRIMARY KEY, email TEXT NOT NULL, "
                   "delivery_code TEXT NOT NULL UNIQUE, code_hash TEXT NOT NULL UNIQUE, "
                   "status TEXT NOT NULL CHECK(status='inactive'), "
                   "qualification TEXT NOT NULL CHECK(qualification='unconfirmed'))")
        recorded = db.execute("SELECT value FROM metadata WHERE key='reference_key'").fetchone()
        if recorded and recorded[0] != fingerprint:
            raise ValueError("Reference key changed; refusing duplicate preparation")
        db.execute("INSERT OR IGNORE INTO metadata VALUES ('reference_key', ?)", (fingerprint,))
        created = present = 0
        for email in candidates:
            ref = reference(email, pepper)
            if ref in existing or db.execute("SELECT 1 FROM gifts WHERE ref=?", (ref,)).fetchone():
                present += 1
                continue
            code = "SC-PREM-" + secrets.token_hex(24).upper()
            digest = hashlib.sha256(code.encode()).hexdigest()
            db.execute("INSERT INTO gifts VALUES (?, ?, ?, ?, 'inactive', 'unconfirmed')",
                       (ref, email, code, digest))
            created += 1
        db.execute("COMMIT")
        return {"selected": len(candidates), "new_inactive": created, "already_present": present}
    except Exception:
        if db.in_transaction:
            db.execute("ROLLBACK")
        raise
    finally:
        db.close()


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--recipients", type=Path, required=True)
    parser.add_argument("--inventory", type=Path)
    parser.add_argument("--intake-only", action="store_true",
                        help="Private recipient snapshot only; no inventory, pepper or codes")
    parser.add_argument("--private-directory", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.intake_only:
            if args.inventory is not None:
                raise ValueError("Intake does not accept a gift inventory")
            result: dict[str, int | str] = intake(args.recipients, args.private_directory)
        else:
            if args.inventory is None:
                raise ValueError("Gift preparation requires verified inventory")
            result = dict(prepare(args.recipients, args.inventory, args.private_directory,
                                 os.environ.get("TESTER_RECIPIENT_REF_PEPPER", "")))
    except Exception:
        # Input/SQLite exceptions can embed addresses or filesystem paths.
        print("Preparation rejected; inspect private inputs, inventory and storage locally.", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
