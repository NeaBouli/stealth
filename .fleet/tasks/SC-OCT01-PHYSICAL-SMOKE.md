id: SC-OCT01-PHYSICAL-SMOKE
goal: Execute a short physical SecureCall smoke using already-built isolated test APKs; no rebuild.
scope: Exclusive ADB ownership of connected Galaxy A21s and Tab S4, existing devtest APKs from /Users/gio/Desktop/repos/.worktrees/stealth-ui86-pr102-wt/claude-SC-OCT01-DEVICE-QA/client_android/app/build/outputs/.
instructions:
  - Read AGENTS.md and SC-OCT01-QA-RECOVER report. The original QA process and its fallback have ended; you now exclusively own these two devices.
  - Do not run Gradle, rebuild, uninstall, modify original SecureCall packages/data, access real entitlements, start production calls or delegate.
  - Verify APK package/ABI/test runner metadata, then install only the existing *.devtest app and its matching test APK: Free on Galaxy A21s, Premium on Tab S4.
  - Run existing instrumentation with adb shell am instrument, capturing sanitized aggregate results immediately after each device. No raw device identifiers or user data in Git/reports.
  - If time remains, launch isolated UI and use hierarchy measurements for dialpad/bottom navigation/settings clipping, labels and touch targets. Do not disable FLAG_SECURE; report absent screenshots explicitly.
  - No claim of complete feature/call/license QA: list remaining untested cases. Stop at 12 minutes with a valid report even if partial; no repeated build or long network waits.
  - Record exact pass/fail/skip counts, installed version/package and tested APK hash as appropriate. Commit sanitized report/evidence only, no pushes.
report: .fleet/reports/SC-OCT01-PHYSICAL-SMOKE.md
