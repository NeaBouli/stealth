import concurrent.futures
import hashlib
import json
import os
from pathlib import Path
import sqlite3
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

from prepare_tester_staging import prepare, reference


class PrivateStagingTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="tester-synthetic-")
        self.root = Path(self.temp.name).resolve()
        self.root.chmod(0o700)
        self.csv = self.root / "recipients.csv"
        self.inventory = self.root / "inventory.json"
        self.pepper = "synthetic-test-reference-pepper-not-production"
        self.csv.write_text('email,list\na@example.invalid,SecureCall \u03b2-test\n'
                            'a@example.invalid,SecureCall \u03b2-test\n'
                            'b@example.invalid,Ekklesia\n', encoding="utf-8")
        self.csv.chmod(0o600)
        self.write_inventory([])

    def tearDown(self) -> None:
        self.temp.cleanup()

    def write_inventory(self, refs: list[str], complete: bool = True) -> None:
        self.inventory.write_text(json.dumps({"schema": 1, "complete": complete,
            "reference_key_fingerprint": hashlib.sha256(self.pepper.encode()).hexdigest(),
            "recipient_refs": refs}), encoding="utf-8")
        self.inventory.chmod(0o600)

    def run_prepare(self) -> dict[str, int]:
        return prepare(self.csv, self.inventory, self.root, self.pepper)

    def stored(self) -> list[tuple[str, ...]]:
        with sqlite3.connect(self.root / "inactive-tester-staging.sqlite3") as db:
            return db.execute("SELECT * FROM gifts ORDER BY ref").fetchall()

    def test_duplicate_and_rerun_preserve_delivery(self) -> None:
        self.assertEqual(self.run_prepare(), {"selected": 1, "new_inactive": 1, "already_present": 0})
        before = self.stored()
        self.assertEqual(before[0][-2:], ("inactive", "unconfirmed"))
        self.assertEqual(self.run_prepare()["new_inactive"], 0)
        self.assertEqual(self.stored(), before)

    def test_inventory_prevents_existing_gift(self) -> None:
        self.write_inventory([reference("a@example.invalid", self.pepper)])
        self.assertEqual(self.run_prepare()["new_inactive"], 0)
        self.assertEqual(self.stored(), [])

    def test_parallel_runs_and_restart(self) -> None:
        self.run_prepare()
        with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
            results = list(pool.map(lambda _: self.run_prepare(), range(8)))
        self.assertEqual(sum(r["new_inactive"] for r in results), 0)
        self.assertEqual(len(self.stored()), 1)

    def test_concurrent_first_preparation(self) -> None:
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            results = list(pool.map(lambda _: self.run_prepare(), range(2)))
        self.assertEqual(sum(r["new_inactive"] for r in results), 1)

    def test_failed_transaction_rolls_back(self) -> None:
        self.csv.write_text('email,list\na@example.invalid,SecureCall \u03b2-test\n'
                            'c@example.invalid,SecureCall \u03b2-test\n', encoding="utf-8")
        self.run_prepare()
        before = self.stored()
        self.csv.write_text('email,list\nd@example.invalid,SecureCall \u03b2-test\n'
                            'e@example.invalid,SecureCall \u03b2-test\n', encoding="utf-8")
        with patch("prepare_tester_staging.secrets.token_hex", side_effect=["a" * 48, OSError("synthetic")]):
            with self.assertRaises(OSError):
                self.run_prepare()
        self.assertEqual(self.stored(), before)

    def test_private_permissions_survive_repeated_saves(self) -> None:
        mask = os.umask(0o022)
        try:
            self.run_prepare()
            self.run_prepare()
        finally:
            os.umask(mask)
        self.assertEqual((self.root / "inactive-tester-staging.sqlite3").stat().st_mode & 0o777, 0o600)

    def test_incomplete_inventory_rejected(self) -> None:
        self.write_inventory([], complete=False)
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_database_symlink_and_hardlink_rejected(self) -> None:
        store = self.root / "inactive-tester-staging.sqlite3"
        store.symlink_to(self.csv)
        with self.assertRaises(ValueError):
            self.run_prepare()
        store.unlink()
        os.link(self.csv, store)
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_unprotected_directory_rejected(self) -> None:
        self.root.chmod(0o755)
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_wrong_group_is_not_selected(self) -> None:
        self.csv.write_text('email,list\na@example.invalid,Other SecureCall test\n', encoding="utf-8")
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_cli_errors_do_not_disclose_input(self) -> None:
        self.csv.write_text('email,list\nprivate-marker,SecureCall \u03b2-test\n', encoding="utf-8")
        result = subprocess.run([sys.executable, "-B", str(Path(__file__).with_name("prepare_tester_staging.py")),
            "--recipients", str(self.csv), "--inventory", str(self.inventory),
            "--private-directory", str(self.root)], capture_output=True, text=True,
            env={"TESTER_RECIPIENT_REF_PEPPER": self.pepper})
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("private-marker", result.stdout + result.stderr)
        self.assertNotIn(self.pepper, result.stdout + result.stderr)
        self.assertNotIn(str(self.root), result.stdout + result.stderr)

    def test_changed_reference_key_rejected(self) -> None:
        self.run_prepare()
        self.pepper += "changed"
        self.write_inventory([])
        with self.assertRaises(ValueError):
            self.run_prepare()

    def test_public_and_symlink_paths_rejected(self) -> None:
        (self.root / ".git").mkdir()
        with self.assertRaises(ValueError):
            self.run_prepare()
        (self.root / ".git").rmdir()
        target = self.root / "linked.csv"
        target.symlink_to(self.csv)
        with self.assertRaises(ValueError):
            prepare(target, self.inventory, self.root, self.pepper)
        self.csv.chmod(0o644)
        with self.assertRaises(ValueError):
            self.run_prepare()


if __name__ == "__main__":
    unittest.main()
