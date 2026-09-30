id: SC-OCT01-INTEGRATION-CHECK
goal: Verify the prepared PR102 candidate against current remote main and identify the precise remaining integration gates.
scope: read-only Git/CI metadata and mapped release/distribution contracts; report only.
instructions:
  - Read AGENTS.md, BRIDGE.md, .fleet/PLAN.md, .fleet/evidence/SC-PR102-UPDATE.md and distribution/architecture docs.
  - Verify current candidate ff5d99f ancestry, current origin/main, merge-tree compatibility and PR102 remote head.
  - Inspect workflows for automatic deployment/payment effects before the planned PR head fast-forward.
  - Read latest relevant GitHub CI, reviews and open release PR state. Do not trigger CI, push, edit PRs or merge.
  - Identify any new commits/changes since September 27 that conflict with the candidate. Avoid rediscovering completed audit findings.
  - Return precise safe push refspec, required check names, any release blockers and concise evidence.
  - No ADB, builds, production/provider/secret access or worker delegation; Claude owns devices.
report: .fleet/reports/SC-OCT01-INTEGRATION-CHECK.md
