id: SC-PR102-AUDIT-REVIEW-20260926
status: partial
worker: claude (brief assigned Kimi; executed by Worker A)
branch: agent/claude/SC-PR102-AUDIT-REVIEW-20260926
summary: Map: signaling (backend/signaling/src: ws/handlers register+call, security/identity_protocol, routes/status, middleware/admin+ip) -> Android WebSocketService/IdentityProtocol/SessionKeyBinding -> core_crypto ffi; plus website/docs product-truth. No MAP.md exists; only this trace was mapped.
  PR #102 integrity: head 5f70b16, base = main e06d0184 (= #84 baseline), 41 commits, strictly linear (no merges or multi-parent commits), 0 behind main, +15472/-2650, 224 files, GitHub reports MERGEABLE. core_crypto is untouched.
  #83/#85/#88 are docs-only evidence PRs on e06d0184, 0 behind. #83 and #85 merge-tree clean vs #102. #88 CONFLICTS with #102 in BRIDGE.md.
  STX register at #102 head: 11 FIXED, 13 PARTIAL, 34 OPEN, 4 DECISION.
  FIXED: 01 f49f395(+9f9e7db/f6f5d01 API24); 02 b819c05; 03 876b9c6 (host check pending); 08 8931e19; 10 d686e5c; 14 dcc5bca; 21 f49f395; 22 77c88a4; 23 a2a8675; 29 2e6c315; 37 77c88a4 (live GitHub wiki publish pending).
  PARTIAL: 07 ip.js now reads the rightmost XFF under TRUST_PROXY, but reportRoute.js:129 still trusts XFF[0]. 16 FCM callerName=clientId. 17 pkd limiter bounded only. 25 Kotlin fill(0), ffi unchanged. 26 some mock ghostnet removed; GhostNetCryptoManager kept. 32 qrcodejs SRI; esm.sh walletconnect without SRI. 38 EUR49 gone; USD custom-ID prices in terms.html:276. 39 SECURITY.md:39 still claims an audit. 41, 45, 46, 52, 54/56.
  OPEN (unchanged at head): 04 phone.js; 05 GIFT randomBytes(4) server.js:509; 06 custom_ids.js; 09 data/activation_codes.json, wallets.json tracked; 11 backend/payments; 12 SecurityEnforcer; 13 WebRtcManager.kt:147-149 OpenRelay creds; 15 WS origin only checked if ALLOWED_ORIGINS set (server.js:353); 18 /tmp fallback server.js:19; 19; 20; 24 no AAD/replay on media; 27; 28; 30; 31; 33; 35; 40; 42 index.html:392; 43; 44; 47-51; 53; 57-62.
  DECISION: 34 linear price escalation (gated closed by LEGACY_STRIPE_CHECKOUT_ENABLED); 36 verified-harmless, close as info; 38 USD/EUR price policy; 55 license vs auditability.
files: .fleet/reports/SC-PR102-AUDIT-REVIEW-20260926.md (only file written; GitHub not modified)
tests: temp detached worktree of #102 head in /tmp (removed afterwards):
  backend/signaling: npm ci --ignore-scripts, then npm test -> exit 0 (all suites incl. identity_*, admin_auth, status_live, pkd_bounds, turn_secret)
  npm run test:tester -> PASS
  python3 -m unittest test_export_tester_staging test_prepare_tester_staging -> 25 OK
  deploy/coturn/test-render-config.sh -> PASS; deployment/check_deploy_secret_output.sh -> Guard PASS
  website/js/*.test.cjs (4) -> ok
  Not run: Android Gradle/instrumentation, hosted CI (forbidden by the brief), live pin/endpoint probes.
risks: STX-21 is fixed at the app layer. Invite and accept are ECDSA P-256 signed over the transcript, the identityId is a self-certifying hash of the key, the signed ephemeral key is the one fed to deriveSessionKey, and the SessionKeyBinding transcript bind compensates for HKDF salt=None (ffi/mod.rs:267, unchanged).
  STX-21 residual: trust in the dialed ID's provenance (there is no safety-number or out-of-band verification UX). The coupled STX-01/21 migration mode (identityProtocolMode warn/enforce) is a deploy decision.
  STX-23: pin-set expires 2028-03-01 and has 4 pins with an isrg_root_x1 anchor. Pins were not matched against the live chain here; that relies on the ddc8dd9 gate.
  security.html:169-170 still lists "Double Ratchet" for the SecureChat/Chameleon rows (other products): needs a product-truth decision.
  network_security_config.xml (src/main) permits cleartext to localhost in release builds (low).
  STX-29..62 web/docs mapping comes from a read-only sub-agent sweep with file:line evidence. The commit for the STX-38 EUR49 removal is inferred.
security: Open findings still present at #102 head, none newly introduced. High: none remain open in code, except STX-03/37 live verification.
  Medium, still open: STX-13 hardcoded public TURN creds (WebRtcManager.kt:147-149); STX-24 no media AAD/replay protection; STX-05 32-bit gift codes; STX-04 phone enumeration; STX-06 custom-ID password model; STX-07 residual XFF[0] in reportRoute.js:129; STX-09 tracked data JSON; STX-11 legacy unsigned webhook; STX-33 intent forwarder; STX-31 ?link= deep link.
  Low: STX-15 WS origin default.
next: Orchestrator: rebase or resolve the #88 BRIDGE.md conflict before merging it after #102. Queue fix waves: backend (05/07-residual/09/13/15/18/20), crypto (24/25/27/28), web (31/32/33/35/57-62).
  Update the #84 checkboxes for the 11 FIXED items after merge. Get owner decisions on 34/36/38/55 and on the security.html Double Ratchet rows.
