import hashlib
import json
from pathlib import Path
import tempfile
import unittest

from export_tester_staging import export_draft, MANIFEST_NAME, REGISTRY_NAME
from prepare_tester_staging import prepare
from promote_tester_staging import ACTIVE_REGISTRY_NAME, PROMOTION_MANIFEST_NAME, promote


class PrivatePromotionTests(unittest.TestCase):
    def setUp(self) -> None:
        self.temp = tempfile.TemporaryDirectory(prefix="tester-promote-synthetic-")
        self.root = Path(self.temp.name).resolve()
        self.root.chmod(0o700)
        self.handoff = self.root / "handoff"
        self.handoff.mkdir(mode=0o700)
        self.output = self.root / "promoted"
        self.output.mkdir(mode=0o700)
        recipients = self.root / "recipients.csv"
        recipients.write_text("email,list\na@example.invalid,SecureCall \u03b2-test\n", encoding="utf-8")
        recipients.chmod(0o600)
        pepper = "synthetic-test-reference-pepper-not-production"
        inventory = self.root / "inventory.json"
        inventory.write_text(json.dumps({"schema": 1, "complete": True,
            "reference_key_fingerprint": hashlib.sha256(pepper.encode()).hexdigest(),
            "recipient_refs": []}), encoding="ascii")
        inventory.chmod(0o600)
        prepare(recipients, inventory, self.root, pepper)
        export_draft(self.root / "inactive-tester-staging.sqlite3", self.handoff)
        self.registry = self.handoff / REGISTRY_NAME
        self.manifest = self.handoff / MANIFEST_NAME
        self.approved = hashlib.sha256(self.manifest.read_bytes()).hexdigest()

    def tearDown(self) -> None:
        self.temp.cleanup()

    def test_exact_approval_creates_private_idempotent_bundle(self) -> None:
        expected = {"records": 1, "status": "approved_for_private_runtime_import_not_deployed"}
        self.assertEqual(promote(self.registry, self.manifest, self.output, self.approved), expected)
        before = {name: (self.output / name).read_bytes()
                  for name in (ACTIVE_REGISTRY_NAME, PROMOTION_MANIFEST_NAME)}
        self.assertEqual(promote(self.registry, self.manifest, self.output, self.approved), expected)
        self.assertEqual(before, {name: (self.output / name).read_bytes() for name in before})
        active = json.loads(before[ACTIVE_REGISTRY_NAME])
        self.assertEqual(active["grants"][0]["status"], "active")
        self.assertIsNone(active["grants"][0]["binding"])
        self.assertNotIn("example.invalid", json.dumps(active))
        self.assertNotIn("SC-PREM-", json.dumps(active))
        for name in before:
            self.assertEqual((self.output / name).stat().st_mode & 0o777, 0o600)

    def test_wrong_approval_and_tampered_registry_fail_closed(self) -> None:
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, "0" * 64)
        self.registry.write_text("{}", encoding="ascii")
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, self.approved)
        self.assertEqual(list(self.output.iterdir()), [])

    def test_active_input_is_rejected(self) -> None:
        value = json.loads(self.registry.read_text(encoding="ascii"))
        value["grants"][0]["status"] = "active"
        raw = (json.dumps(value, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")
        self.registry.write_bytes(raw)
        manifest = json.loads(self.manifest.read_text(encoding="ascii"))
        manifest["runtime_registry_sha256"] = hashlib.sha256(raw).hexdigest()
        self.manifest.write_text(json.dumps(manifest), encoding="ascii")
        approved = hashlib.sha256(self.manifest.read_bytes()).hexdigest()
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, approved)

    def test_grant_id_must_remain_bound_to_code_hash(self) -> None:
        value = json.loads(self.registry.read_text(encoding="ascii"))
        value["grants"][0]["id"] = "0" * 64
        raw = (json.dumps(value, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")
        self.registry.write_bytes(raw)
        manifest = json.loads(self.manifest.read_text(encoding="ascii"))
        manifest["runtime_registry_sha256"] = hashlib.sha256(raw).hexdigest()
        self.manifest.write_text(json.dumps(manifest), encoding="ascii")
        approved = hashlib.sha256(self.manifest.read_bytes()).hexdigest()
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, approved)

    def test_unsafe_and_partial_output_are_rejected(self) -> None:
        self.output.chmod(0o755)
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, self.approved)
        self.output.chmod(0o700)
        partial = self.output / ACTIVE_REGISTRY_NAME
        partial.write_text("synthetic conflict", encoding="ascii")
        partial.chmod(0o600)
        with self.assertRaises(ValueError):
            promote(self.registry, self.manifest, self.output, self.approved)


if __name__ == "__main__":
    unittest.main()
