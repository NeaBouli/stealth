# SecureCall Source Integration Approval Package
Read-only GitHub snapshot: 2026-10-10, main `f8c969b1436cb65c13a6c85e73abe179191245f1`. Source acceptance is not release or sales acceptance.

| Order | PR | Exact head | Green statuses | Remaining integration gates |
| --- | --- | --- | --- | --- |
|1|[138](https://github.com/NeaBouli/stealth/pull/138)|624b3fa7b0eff95d602bf5137ccca485316944c3|9/9|Required GitHub review; scoped security-gate reconciliation|
|2|[137](https://github.com/NeaBouli/stealth/pull/137)|bfd37cd489cc2738b2946972b3d9fdfaf5b91d27|9/9|Draft; required review; update/test exact head after138; scoped security gate|
|3|[134](https://github.com/NeaBouli/stealth/pull/134)|2b86b07c4dd7e78f549aeb4a4f3ab37982529511|11/11|Draft; required review; exact combined-candidate checks/security gate|
|4|[136](https://github.com/NeaBouli/stealth/pull/136)|7251998b5b4f8651452a59eb6b11fd9c034ea7e5|11/11|Draft; required review; scoped security gate; runtime evidence pending|

Each count includes CodeRabbit; bot success is not an independent approving review. All four are OPEN, conflict-free against the snapshot and REVIEW_REQUIRED/BLOCKED. These are technically accepted source candidates, not already merge-ready approvals.
- PR138's lockfile already exists identically in PR137: apply once, preserve PR137's independent source/tests and do not discard its test union. No second dependency implementation.
- PR134 lead source review found no blocker in its one-file CMake path mappings. Existing two-path byte-identity evidence is accepted; this does not prove identical outputs across every toolchain/machine. Rust engine unchanged; no repeated native build.
- PR136 API24 infrastructure triage and existing JVM/source checks accepted. Existing terminal Claude exclusively owns API35/36 startup grant/deny checks; incoming-call, physical/OEM fallback and historical Crashlytics attribution remain separate release checks.
- Codex Security is PLANNED/NOT RUN, not replaced by CI. Access, destination, exact transmitted scope, exclusions and cost authorization must precede scanning; no exception is granted by this package.

## One Bounded Owner Decision
Confirm this source-integration order as one package, subject to normal required reviews and exact-head/security gates. This is not an admin bypass, merge completion, deployment, signing/upload, paid entitlement issuance or sales authorization.
PR119/139 commercial lifecycle and PR120 identity integration retain separate review/migration gates. RC1.0.51 preparation is provisional and unbuilt. PRODUCT_READY and matching separate operator finance acceptance are not granted.
