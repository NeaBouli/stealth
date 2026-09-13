# Inactive tester staging

This tool is preparation only, not a license issuer, activation service or delivery
command. No Android feature flag is changed. Existing Play and commercial direct
licenses are unaffected. Do not use the retired local tester candidate.

## Required private inputs

- Operator-approved, dedicated, user-owned directory with mode700, outside Git,
  with no symlink components. Files must be owner-only mode600, single hard link.
- Private CSV with `email,list` headers. Exact `SecureCall beta-test` list uses the
  Greek beta character in the source data. Other lists are ignored. Duplicate
  normalized addresses are selected once. Selection never confirms eligibility.
- Complete operator-checked inventory JSON, schema1, `complete: true`,
  `recipient_refs` containing HMAC-SHA256 references for all existing gifts, and
  `reference_key_fingerprint` containing SHA256 of the dedicated reference pepper.
  The tool validates structure, NOT operator authority. Do not fabricate an empty
  inventory to bypass duplicate checks. An unavailable inventory is a blocker.
- `TESTER_RECIPIENT_REF_PEPPER` supplied privately, at least32 characters. Never
  put its value in command arguments, Git, Bridge or a report; retain it securely
  between runs. Provisioning this value remains an operator action.

The CLI takes `--recipients`, `--inventory`, `--private-directory`; all are absolute
private paths. It prints aggregate counts only and sanitizes error output.
Directory permissions are checked, not silently altered. No dependency download.

## Storage and lifecycle

SQLite transactions with an immediate write lock, uniqueness constraints and FULL
synchronous durability preserve all prior delivery codes on retries and failures.
Raw codes exist only in the private staging database for later controlled delivery;
they are not printed/exported or copied into a runtime store. They are randomly
generated, unrelated to addresses; separate SHA256 digests support future import.
All records are constrained to inactive/unconfirmed; no activation switch exists.
SQLite is not encrypted at rest: use approved protected local storage and backup
policy. Permissions do not defend against the same OS account or privileged users.

No physical-device guarantee, signed entitlement, ownership recovery, expiration
policy, commercial product binding or license import is implemented by staging.
Those require the current verified runtime contract and separately reviewed tests.
Before any production action: exact APK/signature/checksum, approved inventory and
recipient count, two-device tests, rollback and a bounded owner approval.
No email or live license-store connection is made here.

## Verification

From repository root:

```sh
python3 -B -m unittest discover -s backend/signaling/scripts -p test_prepare_tester_staging.py -v
mypy --strict backend/signaling/scripts/prepare_tester_staging.py backend/signaling/scripts/test_prepare_tester_staging.py
```

Tests use only synthetic addresses and ephemeral secrets. Temporary fixture
directories are private and removed by the test runner. No production inputs.
