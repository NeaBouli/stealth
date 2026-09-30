id: SC-OCT01-QA-RECOVER
goal: Recover existing device-QA evidence after the prior worker timed out without a report; do not repeat any tests.
scope: read-only evidence in /Users/gio/Desktop/repos/.worktrees/stealth-ui86-pr102-wt/claude-SC-OCT01-DEVICE-QA and a concise aggregate report.
instructions:
  - Read project AGENTS.md and the original SC-OCT01-DEVICE-QA brief, then inspect only its build/test/evidence outputs and local QA notes.
  - No ADB, builds, test execution, source changes, production access, network calls or additional agents. This is evidence recovery only.
  - Determine what actually completed from timestamped Gradle XML/results/artifacts and existing QA notes. Distinguish JVM, emulator and physical tests; do not infer a device pass from a build.
  - Report exact aggregate pass/fail/skip counts and source revision where provable; otherwise explicitly unverified. Never print device identifiers, user IDs, contacts or credentials.
  - Identify timeout/storage/test blockers and any documented bug. Do not reconstruct claims from intended commands without completed outputs.
  - Finish within five minutes; partial is correct if physical evidence is absent. Commit only the sanitized report on your worker branch.
report: .fleet/reports/SC-OCT01-QA-RECOVER.md
