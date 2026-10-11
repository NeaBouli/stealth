#!/usr/bin/env python3
"""Promote an exactly approved private tester handoff without deploying it."""

import argparse
import json
from pathlib import Path
import re
import sys
from typing import Any

from export_tester_staging import digest, grant_id, private_path, publish_bundle


HEX = re.compile(r"^[a-f0-9]{64}$")
ACTIVE_REGISTRY_NAME = "tester-runtime-registry-active.json"
PROMOTION_MANIFEST_NAME = "tester-promotion-manifest.json"
REQUIRED_GATES = {
    "exact_signed_direct_premium_artifact",
    "two_device_acceptance",
    "owner_delivery_approval",
    "separate_production_registry_import_approval",
}
MANIFEST_KEYS = {
    "schema", "status", "record_count", "delivery_sha256",
    "runtime_registry_sha256", "requirements",
}


def read_json(path: Path) -> tuple[bytes, Any]:
    private_path(path)
    if path.stat().st_size > 5 * 1024 * 1024:
        raise ValueError("Private handoff JSON is too large")
    raw = path.read_bytes()
    try:
        return raw, json.loads(raw.decode("ascii"))
    except (UnicodeDecodeError, json.JSONDecodeError) as error:
        raise ValueError("Invalid private handoff JSON") from error


def validate_inactive_registry(value: Any, expected_count: int) -> list[dict[str, Any]]:
    if not isinstance(value, dict) or set(value) != {"schema", "grants", "enrolledKeys"}:
        raise ValueError("Unexpected inactive registry schema")
    grants = value.get("grants")
    if value.get("schema") != 1 or value.get("enrolledKeys") != [] or not isinstance(grants, list):
        raise ValueError("Unexpected inactive registry state")
    if not grants or len(grants) != expected_count:
        raise ValueError("Inactive registry count mismatch")
    ids: set[str] = set()
    hashes: set[str] = set()
    output: list[dict[str, Any]] = []
    for grant in grants:
        if (not isinstance(grant, dict) or set(grant) != {"id", "codeHash", "status", "binding"}
                or not isinstance(grant.get("id"), str) or not HEX.fullmatch(grant["id"])
                or not isinstance(grant.get("codeHash"), str) or not HEX.fullmatch(grant["codeHash"])
                or grant["id"] != grant_id(grant["codeHash"])
                or grant["id"] in ids or grant["codeHash"] in hashes
                or grant.get("status") != "inactive" or grant.get("binding") is not None):
            raise ValueError("Invalid inactive grant")
        ids.add(grant["id"])
        hashes.add(grant["codeHash"])
        output.append({"id": grant["id"], "codeHash": grant["codeHash"],
                       "status": "active", "binding": None})
    return output


def promote(inactive_registry: Path, handoff_manifest: Path, directory: Path,
            approved_manifest_sha256: str) -> dict[str, int | str]:
    if not HEX.fullmatch(approved_manifest_sha256):
        raise ValueError("Exact approved manifest SHA-256 required")
    manifest_raw, manifest = read_json(handoff_manifest)
    if digest(manifest_raw) != approved_manifest_sha256:
        raise ValueError("Approved manifest does not match")
    if (not isinstance(manifest, dict) or set(manifest) != MANIFEST_KEYS
            or manifest.get("schema") != 1
            or manifest.get("status") != "draft_do_not_send_or_activate"
            or not isinstance(manifest.get("record_count"), int)
            or manifest["record_count"] < 1
            or not isinstance(manifest.get("runtime_registry_sha256"), str)
            or not HEX.fullmatch(manifest["runtime_registry_sha256"])
            or set(manifest.get("requirements", [])) != REQUIRED_GATES):
        raise ValueError("Invalid private handoff manifest")
    registry_raw, registry = read_json(inactive_registry)
    if digest(registry_raw) != manifest["runtime_registry_sha256"]:
        raise ValueError("Inactive registry does not match manifest")
    grants = validate_inactive_registry(registry, manifest["record_count"])
    active = (json.dumps({"schema": 1, "grants": grants, "enrolledKeys": []},
                         ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")
    promotion = (json.dumps({
        "schema": 1,
        "status": "approved_for_private_runtime_import_not_deployed",
        "record_count": len(grants),
        "source_handoff_manifest_sha256": approved_manifest_sha256,
        "active_registry_sha256": digest(active),
        "remaining_gates": ["exact_signed_direct_premium_artifact", "two_device_acceptance",
                            "separate_production_registry_import_approval"],
    }, ensure_ascii=True, sort_keys=True, indent=2) + "\n").encode("ascii")
    publish_bundle(directory, {
        ACTIVE_REGISTRY_NAME: active,
        PROMOTION_MANIFEST_NAME: promotion,
    })
    return {"records": len(grants), "status": "approved_for_private_runtime_import_not_deployed"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--inactive-registry", type=Path, required=True)
    parser.add_argument("--handoff-manifest", type=Path, required=True)
    parser.add_argument("--private-output-directory", type=Path, required=True)
    parser.add_argument("--approved-manifest-sha256", required=True)
    args = parser.parse_args()
    try:
        result = promote(args.inactive_registry, args.handoff_manifest,
                         args.private_output_directory, args.approved_manifest_sha256)
    except Exception:
        print("Promotion rejected; inspect the private approved handoff locally.", file=sys.stderr)
        return 1
    print(json.dumps(result, sort_keys=True))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
