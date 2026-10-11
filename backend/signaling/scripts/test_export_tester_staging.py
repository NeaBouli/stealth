import csv
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest

from export_tester_staging import export_draft, MANIFEST_NAME, REGISTRY_NAME, DELIVERY_NAME
from prepare_tester_staging import prepare


class PrivateExportTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="tester-export-synthetic-")
        self.root = Path(self.temp.name).resolve()
        self.root.chmod(0o700)
        self.output = self.root / "handoff"
        self.output.mkdir(mode=0o700)
        self.csv = self.root / "recipients.csv"
        self.inventory = self.root / "inventory.json"
        self.pepper = "synthetic-test-reference-pepper-not-production"
        self.csv.write_text(
            "email,list\nb@example.invalid,SecureCall \u03b2-test\na@example.invalid,SecureCall \u03b2-test\n",
            encoding="utf-8")
        self.csv.chmod(0o600)
        self.inventory.write_text(json.dumps({
            "schema": 1, "complete": True,
            "reference_key_fingerprint": hashlib.sha256(self.pepper.encode()).hexdigest(),
            "recipient_refs": [],
        }), encoding="utf-8")
        self.inventory.chmod(0o600)
        prepare(self.csv, self.inventory, self.root, self.pepper)
        self.database = self.root / "inactive-tester-staging.sqlite3"

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_private_draft_bundle_is_inactive_and_idempotent(self) -> None:
        expected = {"records": 2, "status": "draft_do_not_send_or_activate"}
        self.assertEqual(export_draft(self.database, self.output), expected)
        before = {name: (self.output / name).read_bytes()
                  for name in (DELIVERY_NAME, REGISTRY_NAME, MANIFEST_NAME)}
        self.assertEqual(export_draft(self.database, self.output), expected)
        self.assertEqual(before, {name: (self.output / name).read_bytes() for name in before})
        for name in before:
            self.assertEqual((self.output / name).stat().st_mode & 0o777, 0o600)

        with (self.output / DELIVERY_NAME).open(newline="", encoding="utf-8") as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual([row["email"] for row in rows], ["a@example.invalid", "b@example.invalid"])
        self.assertTrue(all(row["delivery_status"] == "draft_do_not_send" for row in rows))
        runtime = json.loads((self.output / REGISTRY_NAME).read_text(encoding="ascii"))
        self.assertEqual(len(runtime["grants"]), 2)
        self.assertTrue(all(grant["status"] == "inactive" and grant["binding"] is None
                            for grant in runtime["grants"]))
        runtime_text = json.dumps(runtime)
        self.assertNotIn("example.invalid", runtime_text)
        self.assertNotIn("SC-PREM-", runtime_text)

    def test_changed_or_partial_output_is_rejected_without_overwrite(self) -> None:
        target = self.output / DELIVERY_NAME
        target.write_text("synthetic-conflict", encoding="ascii")
        target.chmod(0o600)
        before = target.read_bytes()
        with self.assertRaises(ValueError):
            export_draft(self.database, self.output)
        self.assertEqual(target.read_bytes(), before)
        self.assertFalse((self.output / REGISTRY_NAME).exists())

    def test_invalid_staging_record_is_rejected(self) -> None:
        with sqlite3.connect(self.database) as database:
            database.execute("UPDATE gifts SET code_hash=? WHERE rowid=(SELECT MIN(rowid) FROM gifts)",
                             ("0" * 64,))
        with self.assertRaises(ValueError):
            export_draft(self.database, self.output)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_empty_staging_and_unsafe_output_are_rejected(self) -> None:
        with sqlite3.connect(self.database) as database:
            database.execute("DELETE FROM gifts")
        with self.assertRaises(ValueError):
            export_draft(self.database, self.output)
        self.output.chmod(0o755)
        with self.assertRaises(ValueError):
            export_draft(self.database, self.output)

    def test_cli_redacts_private_paths_and_data(self) -> None:
        with sqlite3.connect(self.database) as database:
            database.execute("UPDATE gifts SET email='private-marker'")
        result = subprocess.run([
            sys.executable, "-B", str(Path(__file__).with_name("export_tester_staging.py")),
            "--staging-database", str(self.database),
            "--private-output-directory", str(self.output),
        ], capture_output=True, text=True, env={})
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("private-marker", result.stdout + result.stderr)
        self.assertNotIn(str(self.root), result.stdout + result.stderr)

    def test_symlink_target_is_rejected(self) -> None:
        (self.output / DELIVERY_NAME).symlink_to(self.csv)
        with self.assertRaises(ValueError):
            export_draft(self.database, self.output)


if __name__ == "__main__":
    unittest.main()
