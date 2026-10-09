id: ASPIDA-WEBSITE-ACCEPTANCE-001
status: partial
worker: Codex documentation delegate (root evidence consolidated; no source or browser retest)
branch: agent/codex/ASPIDA-WEBSITE-001
candidate: 2282a8ef5d78215bec26b5e252ead3d70688e5fb
decision: PARTIAL/HOLD — code built; final visual acceptance and publication not approved.
summary: WEB.PLATFORM.ASPIDA hop1, index.html #platform/.product-grid -> landing.css .product-card is built; architecture states updated only. No source changes by this checkpoint.
source: Claude b93159dad5a364fa332c7419660ff515667c83dc; guard remediation 3e04ff35b1215af3fc3c7a786a7787d963f642d2 accepted by focused root review. Initial finding was negative-copy coverage, not a confirmed security vulnerability.
corrections: Contrast 55759c9422218ce92998f61f55e43cb3b8e7ad0a uses scoped --sx-muted; anchor 995944160d806f16d7f9068c633ce80dfc23a6e3 adds scoped scroll-margin-top: 10rem. Final anchor proof NOT RUN.
files: Source delta limited to website/index.html, website/css/landing.css, website/js/launch-copy.test.cjs; accepted296 CI wiring inherited. No backend/runtime/CI/Android/payment changes.
tests: Root at candidate: node --test website/js/ifr-checkout.test.cjs website/js/no-google-analytics.test.cjs website/js/launch-copy.test.cjs -> 15/15 PASS; node --check website/js/main.js; node --check website/js/ifr-checkout.js; node --check website/js/launch-copy.test.cjs -> PASS; git diff --check -> PASS. No source tests rerun here.
guards: Actual 17 negative fixtures, not the earlier worker report's 16; honest current copy passes. Existing other-product assertions retained.
build_typecheck: N/A (static site); no invented build/typecheck PASS.
evidence: .fleet/evidence/ASPIDA-WEBSITE-001/{diagnostic-contrast-before.png,1440x1000-normal.png,1440x1000-hover.png,diagnostic-anchor-before-1180x820.png,measurements.json}; root opened all 4 images and computed SHA256 checks.
provenance: All images are pre-final; desktop normal/hover reflect contrast 55759c94 before anchor 99594416. They cannot prove final acceptance at candidate 2282a8ef.
contrast: Root DOM RGB(82,96,113) on white/RGB(234,244,255) gave 6.42/5.77 normal/hover; prior badge 3.29. Pre-final contrast proof only.
inert_control: Root initial DOM/click proved SPAN, aria-disabled=true, no href, unchanged URL and 0 active ASPIDA controls. Not full final navigation coverage.
anchor: Pre-fix 1180x820 heading_y 80.8828 < navbar_bottom 126.5234 -> FAIL_OCCLUDED; scoped fix is built, final remeasurement NOT RUN.
final_matrix: 1440x1000, 1180x820, 820x1180, 390x844, 320x640 all NOT RUN; final mobile menu and anchor NOT RUN. No UI PASS.
blocker: Browser execution timeout 30s reset runtime; one supported bootstrap/list recovery returned [] (no browser). No different backend/browser, installation or bypass. Own QA server stopped cleanly.
pattern_check: Root git grep -Il -E credential-shape patterns over tracked 3 source files, 2 own briefs, 3 map docs -> 0 matches, expected exit 1 normalized PASS; filename-only, no values printed. Strictly scoped pattern check, NOT full secret scan/gitleaks/Codex Security.
doc_tests: git diff --check plus bounded local-reference, append-only, scope and status checks ->PASS; source tests/browser checks not repeated by this documentation task.
security: No confirmed new vulnerability in focused static delta review; review is NOT a scan. Full secret scanner and Codex Security NOT RUN; plugin not installed, explicit installation/destination/payload/exclusions/cost authorization absent; no exception.
risks: Final responsive/layout/menu/anchor visual gate is incomplete; static and pre-final evidence do not authorize final integration/publication. No PRODUCT_READY or release readiness granted; ASPIDA rollout stays 23%.
commercial_boundary: ASPIDA in development, one planned license, no plans/subscriptions; no price/term/device/coverage/Suite promise or purchase/download/activation. Other product/company/license details unchanged.
coordination: Finance coordination takes place separately through the VLABS operator; no private financial/operator/provider details recorded.
next: Root resumes supported exact-candidate final visual gate, opens all acceptance screenshots and repeats critical assertions after any separately approved deployment; required release authority remains separate. No push/main merge/deploy here.
