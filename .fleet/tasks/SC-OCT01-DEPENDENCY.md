id: SC-OCT01-DEPENDENCY
goal: Resolve the newly failing PR102 backend dependency audit with a minimal supported patch.
scope: backend/signaling/package-lock.json and package.json only if necessary; focused test evidence.
instructions:
  - Read AGENTS.md and existing architecture map; identify the backend dependency node before editing.
  - CI run 36781026340 reports GHSA-m9gg-hp2v-232j and GHSA-f596-whhp-79r4 affecting @grpc/grpc-js 1.14.0 through 1.14.4.
  - Verify the actual lock dependency path and select a supported patched version, without broad upgrades or npm audit fix --force.
  - Run npm ci, npm audit --audit-level=high, full signaling tests and git diff --check.
  - Commit on your isolated worker branch; report exact changes, tests, remaining risks and commit.
  - No deployment, production access, secrets, ADB, push/main merge or additional agents. Other workers own device QA and integration metadata.
report: .fleet/reports/SC-OCT01-DEPENDENCY.md
