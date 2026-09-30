id: SC-OCT01-DEVICE-QA
goal: Execute available physical SecureCall QA on the reviewed ff5d99f candidate.
scope: Android test/build lane and existing QA matrix; no production or entitlement mutations.
instructions:
  - Read AGENTS.md, BRIDGE.md, docs/DISTRIBUTION_MATRIX.md, docs/architecture/MAP.md and existing QA/test files.
  - Discover exactly the two connected physical devices locally; never put serials in reports or Git.
  - Own all physical-device/ADB interactions exclusively for this block; avoid unrelated apps.
  - Inspect installed SecureCall package/version/signature and preserve user data; use install -r when compatible.
  - Build candidate test artifacts as needed; use synthetic test state, never real credentials or recipients.
  - Run existing physical instrumentation and measurable narrow-phone/tablet UI tests. Cover Free and Direct Premium distribution paths where technically possible.
  - Run the available two-device call/UI/settings/background/reconnect cases from the existing matrix; mark unexecutable cases with exact blockers, never infer PASS.
  - Do not disable FLAG_SECURE in shipping code. Collect synthetic layout evidence using supported existing mechanisms.
  - Use frontend-visual-release-gate for visible UI verification. Record artifacts and assertions; do not leak personal/device data.
  - Only report/evidence changes permitted; report defects for a separate bounded fix. No app uninstallation, production deployment, real license activation, push, merge or worker delegation.
report: .fleet/reports/SC-OCT01-DEVICE-QA.md
