id: SC-OCT01-PHYSICAL-SMOKE
status: partial
worker: claude
branch: agent/claude/SC-OCT01-PHYSICAL-SMOKE
summary: Physical smoke with existing isolated devtest APKs; no code touched, so no MAP hop applies (QA hop: devtest APK -> device).
  No Gradle/rebuild/uninstall; original SecureCall packages and data untouched; no production calls or real entitlements.
  Verified metadata: com.securecall.app.{free,premium}.devtest 1.0.50-{free,premium} vc 78017001, arm64-v8a, min 24/target 36;
  test APKs .devtest.test target the matching app via androidx.test.runner.AndroidJUnitRunner. install -r -t 4x Success
  (Free -> Galaxy A21s API 31, Premium -> Tab S4 API 29). SHA-256 prefixes in .fleet/evidence/SC-OCT01-PHYSICAL-SMOKE.md.
  Done in about 5 minutes (01:25-01:30).
files: .fleet/reports/SC-OCT01-PHYSICAL-SMOKE.md, .fleet/evidence/SC-OCT01-PHYSICAL-SMOKE.md
tests: am instrument -w -r: Free/A21s 29 run, 28 pass, 0 fail, 1 skip (OK); Premium/Tab S4 30 run, 29 pass, 0 fail, 1 skip (OK).
  Skip on both devices: CertificatePinInstrumentedTest.productionChainMatchesConfiguredPins (assumption, needs network/prod chain).
  UI hierarchy (Tab S4 premium only): dialer 20/20 clickables labelled, none <48dp, none off-screen; bottom nav fine;
  calls tab ok. Settings: 10 section header rows 711x37 dp (FAIL: under the 48dp/44dp touch-target minimum) and mixed
  DE/EN labels (Konto/Sicherheit/Darstellung/Über vs Calls/Network/Diagnostics). Call-log CardViews have no own label.
  Screenshots: none (FLAG_SECURE left on, as required). A21s keyguard showing, not unlocked -> no Free UI measurements.
risks: Not tested: Free UI on A21s, real calls/SIP/TURN, two-device call matrix, incoming call/ringing, license/entitlement
  purchase and restore, Premium VPN runtime connect, anti-recording on a real capture attempt, cert pinning against the prod chain,
  landscape/split-screen, font scaling, TalkBack. Instrumentation green is not full feature/call/license QA.
  APK revision assumed ff5d99f (clean tree), not stamped. The devtest packages were already installed before (replaced with -r).
security: No finding. No device identifiers, contacts, call-log contents or credentials recorded; pinning test skipped, not failed.
next: Fix Settings section header height (minHeight 48dp) and unify labels to one locale; unlock A21s by the owner and repeat the
  Free hierarchy check; run the two-device call matrix and the pinning test with network in a separate time-boxed brief.
