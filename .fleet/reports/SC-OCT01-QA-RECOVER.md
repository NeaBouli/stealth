id: SC-OCT01-QA-RECOVER
status: partial
worker: claude
branch: agent/claude/SC-OCT01-QA-RECOVER
summary: Evidence recovery only (no ADB/build/test run; no code touched, so no MAP hop applies). SC-OCT01-DEVICE-QA worktree
  is on agent/claude/SC-OCT01-DEVICE-QA at HEAD ff5d99f3bca92849de042b15fb76a88c70f8c9bb (the brief's candidate), git tree clean,
  no report and no QA notes written. Completed outputs (2026-10-01 local mtimes): Free/Pro no-VPN guard text 00:44:19
  ("Free and Pro source sets contain no VpnService markers or WireGuard dependencies."); manifest merges free 00:45, premium 00:46;
  freeDebug APKs 01:08 + free androidTest APK 01:09; premiumDebug APKs 01:12 + premium androidTest APK 01:14
  (applicationId *.devtest, versionName 1.0.50-free/-premium, versionCodes 78017002/3/9 per ABI split).
  Nothing after 01:14. No Gradle test-results XML, no androidTest-results, no screenshots or device logs exist.
  Build outputs are assumed to come from ff5d99f because the tree is clean; this is not proven by a stamped revision.
files: .fleet/reports/SC-OCT01-QA-RECOVER.md
tests: Recovered aggregate: JVM unit 0 pass / 0 fail / 0 skip (no XML, UNVERIFIED/not run);
  emulator instrumentation: none (UNVERIFIED); physical instrumentation: none (UNVERIFIED); two-device matrix: 0 cases executed.
  Only proven: Free/Pro VPN source guard passed; free+premium debug and androidTest APKs assembled. A build is not a device pass.
  No tests run by this recovery task (read-only brief).
risks: The prior worker most likely timed out after the 01:14 premium androidTest assemble, before connectedAndroidTest or UI cases.
  Storage blocker: data volume at 100% (about 4.3 GiB free) during recovery. That can starve Gradle/ADB installs and screenshot
  capture. Premium runtime guard and final-artifact inspection have no output (not proven). No documented bug found in the evidence.
security: No finding. No device identifiers, user IDs, contacts or credentials seen or recorded.
next: Free disk space (>=15 GiB). Rerun physical QA with the existing APKs (same mtimes/HEAD ff5d99f), split per variant into short
  time-boxed blocks, and keep results under build/outputs/androidTest-results plus a sanitized report after each block.
