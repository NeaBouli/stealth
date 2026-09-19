# Inactive tester staging

This tool is preparation only, not a license issuer, activation service or delivery
command. No Android feature flag is changed. Existing Play and commercial direct
licenses are unaffected. Do not use the retired local tester candidate.

## Community intake (first, independent stage)

The community coordinator does NOT provide a runtime signer, invent a complete
gift inventory or implement licenses. Use `--intake-only` for a private snapshot
of the exact recipient list. No `--inventory`, environment secret or key is needed.
No code or gift entitlement is generated. Output status is always
`awaiting_lead_reconciliation`; selected recipients are not approved recipients.

```sh
python3 -B backend/signaling/scripts/prepare_tester_staging.py \
  --intake-only --recipients "$PRIVATE_SOURCE_CSV" \
  --private-directory "$PRIVATE_INTAKE_DIRECTORY"
```

The destination must already exist with mode700 and be outside Git. This stage
can read owner-owned source CSVs with mode600 or644; it does not change original
files or permissions. It writes only `recipient-intake.json` with mode600, rejects
symlink/hardlink paths and different existing snapshots, and preserves identical
snapshots on retry. Counts only are printed; email addresses stay local.
This mode does not attest to encrypted storage or backup protection. It creates
no redeemable material and does not relax the stricter code-staging prerequisites.

The lead remains responsible for inventory reconciliation, device assurance,
current signed-license integration, tested APK and delivery authorization. The
coordinator reports intake completion separately from overall feature readiness.

## Required private inputs

The following requirements apply to CODE preparation, not recipient intake.

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

After review, create an explicitly inactive private handoff draft:

```sh
python3 -B backend/signaling/scripts/export_tester_staging.py \
  --staging-database "$PRIVATE_STAGING_DATABASE" \
  --private-output-directory "$PRIVATE_HANDOFF_DIRECTORY"
```

Both paths must be absolute, owner-owned and outside Git. The output directory
must already be mode700. The exporter atomically creates three mode600 files:

- `tester-delivery-draft.csv` contains the private email/code mapping and is the
  only delivery input. Every row is marked `draft_do_not_send`.
- `tester-runtime-registry-inactive.json` contains only code hashes, opaque grant
  IDs and inactive bindings. It contains no address or raw code.
- `tester-handoff-manifest.json` pins both payloads and lists the remaining gates.

The bundle is deterministic and idempotent. A conflicting or partial prior bundle,
unsafe path, invalid staging record or concurrent export fails without replacing
data. This step does not confirm recipients, activate grants, import production
state, build an APK or authorize email delivery.

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
python3 -B -m unittest discover -s backend/signaling/scripts -p 'test_*tester_staging.py' -v
mypy --strict backend/signaling/scripts/prepare_tester_staging.py \
  backend/signaling/scripts/export_tester_staging.py \
  backend/signaling/scripts/test_prepare_tester_staging.py \
  backend/signaling/scripts/test_export_tester_staging.py
```

Tests use only synthetic addresses and ephemeral secrets. Temporary fixture
directories are private and removed by the test runner. No production inputs.
