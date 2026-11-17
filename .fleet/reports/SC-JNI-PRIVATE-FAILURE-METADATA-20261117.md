# SC-JNI-PRIVATE-FAILURE-METADATA-20261117
status: SOURCE_ONLY_CANDIDATE; runtime NOT_RUN / NOT_ACCEPTED
owner: Codex exclusive takeover; no additional agents
base: 04b13479f054486d0b671d4d266ddea4c0a0de41
branch: agent/claude/SC-JNI-PRIVATE-FAILURE-METADATA-20261117
scope: .github/scripts/sc-media-qualification.py; append-only docs/architecture/MAP.md; this report
hop: hosted -> run_container(private) -> fixed receipt -> cleanup -> exact still-DRAFT host publication
receipt: private-container-failure.json; canonical fixed schema <=16384 bytes; no log tail/free text
identity: bound run/controller/source/tree/table/input SHA; missing_artifact always UNKNOWN
failure: original exception/nonzero retained even if receipt/publication/cleanup fails; no retry/continuation
success: hosted/headless/success receipt and existing public-only diagnostics unchanged
tests: python3 -I .github/scripts/sc-media-qualification.py private-failure-checks -> 122 checks
tests: python3 -I .github/scripts/sc-media-qualification.py public-diagnostic-checks -> 17 checks
tests: all 139 final checks synthetic/pure stdlib; external operations fully mocked
tests: AST baseline comparison -> 48 existing symbols, 42 constants, 8 container guards/8 commands unchanged
tests: Python compile/AST, ruff check --no-cache, git diff --check -> clean
fixture-history: initial synthetic cgroup-record collision fixed by per-scenario directories, not weaker guards
self-review: no unresolved source finding in schema, leak, identity, failure, cleanup or success control flow
risks: fixed categories are observations, not proven causes; all artifact identities deliberately withheld
risks: unavailable metadata or failed cleanup/DRAFT/upload retains failure but can leave no remote receipt
not-run: app/Gradle/JVM/Docker/emulator/KVM/remote/network; no runtime receipt or APK/JNI/ART acceptance
boundaries: app/workflow/bindings/resources/credentials/cache/checksums/exports untouched; no push/merge
next: parent reviews exact local SHA/tree and separately owns any runtime admission; no broad PASS
