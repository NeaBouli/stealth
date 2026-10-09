id: ASPIDA-WEBSITE-GUARDS-001
status: ok
worker: claude
branch: agent/claude/ASPIDA-WEBSITE-GUARDS-001
summary: Architecture-map node: website/index.html ASPIDA card -> website/js/launch-copy.test.cjs (test-only hop; docs/architecture/MAP.md ASPIDA website boundary). Added local helper flatCopy (strips tags, nbsp, collapses whitespace) and bounded ASPIDA_PROMISES regexes for duration, device count, Core/Shield variant coverage and shared crypto core. Test 1 injects 16 fixtures (e.g. "valid for12months", "covers3devices", "Core+Shield coverage", "sharedcryptographiccore", multi-line/nbsp forms) into the card and requires rejection. Test 2 requires the current honest card and "Planned: one license" / "no subscription" wording to pass. Existing assertions untouched. Candidate commit: 3e04ff35b1215af3fc3c7a786a7787d963f642d2 (parent c188b46c; source b93159da).
files: website/js/launch-copy.test.cjs (+ this report)
tests: node --test website/js/*.test.cjs = 15 pass / 0 fail (3 files: ifr-checkout, launch-copy, no-google-analytics); node --check on all 3 OK; git diff --check clean; git diff b93159da..HEAD -- website/index.html website/css/landing.css empty (byte-unchanged). Mutation check: temporarily injecting "valid for 12 months, covers 3 devices" into the card made launch-copy fail (1 fail), reverted. No browser/visual work; no visual PASS claimed.
risks: Regexes are deliberately narrow (any "<n> month/year/device(s)" or Core/Shield/crypto-core wording in the ASPIDA card fails); legitimate future copy with such terms needs an intentional test update. Other products unaffected.
security: none. Codex Security NOT RUN; product and finance readiness unchanged.
next: Root verifies fix and performs visual acceptance; no push/merge/deploy done.
