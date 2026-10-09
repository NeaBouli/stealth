id: ASPIDA-WEBSITE-GUARDS-001
worker: claude
goal: Close the confirmed ASPIDA copy-test coverage gap without changing rendered source.
scope: website/js/launch-copy.test.cjs only; owned Fleet report.+instructions:
  - Read current project rules and the existing ASPIDA-WEBSITE-001 brief/map; preserve all other agents' changes. You are not alone.
  - Work on your dispatcher-created branch. Parent source b93159dad5a364fa332c7419660ff515667c83dc; report-only head9c77a37478379ab859928aacda3c949d45cf7f65. No website HTML/CSS or runtime changes.
  - The triggered specialist review found a test gap, not a security vulnerability. Existing negative assertions fail to reject concrete duration, device count, variant coverage and shared crypto/core promises within the ASPIDA card.
  - Add meaningful bounded guards and regression fixtures for examples valid for12months, covers3devices, Core+Shield coverage and shared cryptographic core, including whitespace-normalized forms. Preserve all existing assertions; do not invent broad natural-language completeness claims.
  - Ensure planned one-license/no-subscription wording still passes. Target only ASPIDA markup; other products' terms remain unchanged. Keep the test helper minimal and local to the existing file.
  - Run all existing three Node test files, syntax checks and git diff --check. Report exact results and prove index.html/landing.css unchanged from source candidate.
  - No browser/device work, dependencies, backend/payment/CI changes, scans, transfers, installation, spending, push, main merge or deployment. Codex Security NOT RUN; product and finance readiness unchanged.
  - Commit only the owned test and report. Root owns fix verification and actual visual acceptance. Valid <=40line Fleet report with exact candidate commit and no visual PASS claim.
acceptance: Confirmed unsupported-promise examples are rejected, current honest card passes, existing tests intact, rendered source byte-unchanged.
report: .fleet/reports/ASPIDA-WEBSITE-GUARDS-001.md
