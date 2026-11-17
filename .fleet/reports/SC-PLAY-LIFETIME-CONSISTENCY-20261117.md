id: SC-PLAY-LIFETIME-CONSISTENCY-20261117
status: partial; source-complete candidate, NOT an implementation/Android/release acceptance PASS
source_commit: e0f7a4caed437eb3442f0a65fe6fb46194a25439
source_tree: fe0a022b16c36eb8f69b071b05438215afcbe894
branch: agent/claude/SC-PLAY-LIFETIME-CONSISTENCY-20261117
base: a7d70ddd169ddd789be01212ba4c501f3dd155be; existing 10-file +737/-111 Claude work preserved and completed in place
summary: mapped BillingManager restore -> pending/verified authority -> WS/store/ack/status/RTDN, no second implementation
server: unknown tokens get durable unbound tombstones; failed persistence rolls back and RTDN retries; restart cannot grant
ack: synchronous authoritative binding/revocation recheck after provider await, including rebind; no stale-record success
client: pending receipts do not replace finite verified rights; stale/cross-request/finalized duplicate ACKs cannot consume them
restore: join SUBS+INAPP before registering all receipts and sending; incomplete query does not select a subset
coexistence: explicit COEXISTENCE_POLICY_REQUIRED, no rank/upgrade decision; unanswered receipts are not silently timed out
storage/http: preference failures restore prior memory and remain retryable; HTTP snapshots use token/product/generation
policy: read-only GOOGLE_PLAY_LIFETIME_POLICY_FILE, strict required shape, bounded file/expiry, no defaults; Direct packages rejected
privacy/boundaries: generic aggregate lifetime success event; Direct verifier/auth/VPN/monthly/gift contracts and CLOSED flags preserved
test_cwd: backend/signaling under the owned worktree; all commands used env -i PATH="$PATH" NODE_ENV=test
focused: npm run test:play-source -> EXIT 0; 2 legacy runners + RTDN retry 1/1 + lifetime 17/17
additional: node src/__tests__/data_dir.test.js -> PASS after explicit read-only input classification; initial guard failure fixed
full_command: env -i PATH="$PATH" NODE_ENV=test NODE_OPTIONS="--require=$PWD/../../.fleet/checks/SC-PLAY-LIFETIME-CONSISTENCY-20261117-offline.cjs" npm test
full_result: exactly one full invocation, EXIT 0; pretest/test/posttest completed, 54 Node entrypoints + 1 Coturn renderer check
full_counts: TAP lifetime 17/17 + RTDN retry 1/1, zero fail/skip/cancel; no fabricated aggregate assertion count for legacy runners
full_evidence: .fleet/evidence/SC-PLAY-LIFETIME-CONSISTENCY-20261117/npm-test.log (synthetic fixtures only)
full_sha256: 7ab1b33dd93aed6169da8600725651498485a467ffc1babf16e036afb72c956a
red_command: env -i PATH="$PATH" NODE_ENV=test node --require ../../.fleet/checks/SC-PLAY-LIFETIME-CONSISTENCY-20261117-red.cjs --test --test-name-pattern='unknown-token revoke|revoke while a stale provider|revoke during provider acknowledgement|rebind during acknowledgement|success log' src/__tests__/google_play_lifetime.test.js
red_result: baseline modules loaded read-only from a7d70ddd, EXIT 1, 5/5 expected failures; all five pass in current lifetime suite
offline_guard: synthetic external socket rejected before connect; guarded node src/__tests__/server_startup.test.js PASS (localhost only)
jvm_sources: SubscriptionManagerTest 26, BillingManagerTest 4, UpgradeActivityTest 8 methods; NOT_RUN, NOT compiled
jvm_coverage: ACK orders/duplicates, pending finite rights, SUBS+INAPP query orders/failure, register-before-send, storage retry, HTTP/reset races
files_backend: src/{subscriptions.js,server.js,ws/handlers/subscription.js,payments/google_play_billing.js,payments/google_play_rtdn.js}
files_backend_tests: src/__tests__/{google_play_lifetime.test.js,data_dir.test.js} (all backend paths below backend/signaling)
files_android: billing/SubscriptionManager.kt, net/WebSocketService.kt (main); billing/{BillingManager.kt,UpgradeActivity.kt} (free)
files_jvm: billing/SubscriptionManagerTest.kt (test), billing/{BillingManagerTest.kt,UpgradeActivityTest.kt} (testFree)
files_evidence: docs/architecture/MAP.md, .fleet/checks/*CONSISTENCY-20261117-{red,offline}.cjs, full log, this report
checks: git diff --check PASS; source commit worktree clean; no JNI controller, private finance/customer data or credentials touched
remaining: owner-approved non-secret policy values and coexistence selection input absent; missing/invalid inputs remain unavailable
remaining: current (not superseded) HTTP failure retains pre-existing fail-closed semantics; no new offline/rebind policy selected
parent_gates: Android compilation/JVM/CI, responsive UI/device/provider runtime, integration and security scan NOT_RUN here
restrictions: no Gradle/emulator/Docker/remote CI, agents, global changes, push/main merge, signing or deployment; no external provider requests
next: parent integrates/reviews the frozen source commit; no production/commerce activation implied; keep worktree/evidence for parent
