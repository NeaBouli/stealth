id: SC-JNI-ART-NATIVE-20261117
status: partial / SOURCE_ONLY; new PUBLIC DIAG NOT_RUN; ART qualification NOT_READY; parent bindings preserved
worker: codex (exclusive source continuation; no other agents)
branch: agent/codex/SC-JNI-ART-NATIVE-20261117
base: a9e2977eb9dbf324848e7b0b4c3adfb300414549
continuation_base: b276d321 (parent cdc18284 bindings + b276d321 scoped KVM ACL preserved); only controller/workflow/MAP/report changed
summary: one PUBLIC-ONLY diagnostic: diagnose_public -> public_preparation(diagnose=True) -> public container failure -> owned cleanup -> redacted exact DRAFT asset; ends before private input/strip/app/VM on every outcome.
files: .github/scripts/sc-media-qualification.py
unchanged: .github/scripts/sc-art-headless.sh; qualified app/native/security/secret delta d1eefa87; no duplicate implementation/tests
files: .github/workflows/android-instrumentation.yml; docs/architecture/MAP.md; this report
source: d1eefa872cf9995e840e5ed671f14df983f75c87 / tree2bf04aa294f24fe246ee8ae142b19699fd5025a9
source_table: independently counted799 code files incl. root export guard, excluding original provider; SHA25666e57da8cc2b24e4d6a56354b52ec870299332a3562149947358baa2df7f13f9
verification_xml: SHA25651e401f56ef9e68cf1fa279b731fc7b94d1404fbd4d1d7a31a5ec46b3965c4f5
guard: tools/verify_android_crypto_exports.sh SHA256a20d3740d7ef133e8ea9c4937440eed603468b6331e234908e53f85f6401d964
native_arm64: 2782617de7793cc0040bae2ffff3588350e78734dffe91aaa83edf1f461e4f9f
native_armv7: 01b0ec8a1fda6363baf30eda275cd142f4857743ced8b012798c47afd74d4280
native_x86_64: 4a932002caab2e585f56950f5ea35d587d4f13b588c62549f5ad513eaf207c59 / 618624 bytes
permitted_stripped_x86_64: abc5e77e3f17c7137ef94700266d7969643dc6f8f4a48f6097ca53befcd8d0ff / 477088 bytes; parent-provided Darwin NDK27 metadata-only receipt, seven JNI exports unchanged per parent, not rerun here
provider: unchanged synthetic fixture SHA256a466488db1f666da3f1b941ad4c389a6b7940d5c93662adf25f77fb781fd63c7
public: controller-authored plugin-free project, pinned Gradle9.8.0/JDK17/SDK37/AGP9.4.1 metadata, four AndroidTest roots + prefab2.1.0-all/aapt2-9.4.1-15978811-linux; six root artifact SHA256 pins rechecked against exact XML. Prior28-module closure accepted, not re-executed.
metadata: read-only immutable public XML fetch/hash before private input; sanitized verified seed -> fresh tmpfs Gradle home -> strict resolve -> selected artifacts/metadata-2.107 -> host SHA256/path recheck. No repository wrapper/scripts with network, init bypass, rule change, blanket cache copy or new fixture.
profile: private network-none/--offline/no -Pinternal; nonroot/read-only/capdrop/NNP; 1CPU/4GiB/no swap/256pids/3GiB scratch/CPU840/wall900/file512MiB/output512KiB. Public bridge phase has same caps, no token/homes/private inputs; resolved single RO awk alias included in create/inspect/kernel checks.
capture: NO NEW MOUNT or privilege. Reuse existing <=512KiB attached public output only; retain redacted last<=8192 bytes plus fixed/numeric exit/OOM/caps+export counts/output bytes/bounds in canonical JSON total<=16KiB. Private diagnose flag refuses before host commands; no private output publication.
workflow: PUBLIC DIAG / NO APP TESTS invokes diagnose-public only; success ends there too, actual public failure stays nonzero. No emulator/system-image download; existing NDK/CMake/tool/host requirements and scoped KVM ACL setup/always-restore unchanged.
retention: exact public-preparation-diagnostic.json on bound still-DRAFT448471483/sc-jni-art-input-20261117, ID/tag/draft rechecked/no overwrite. Tokenized API use ONLY after successful owned container/image/cgroup cleanup; never container. No raw diagnostic on Actions logs, Actions artifacts, APK or customer data retention.
checks: python3 -I .github/scripts/sc-media-qualification.py public-diagnostic-checks -> public_diagnostic=17 host_target_network_execution=false PASS; pure JSON/redaction/escaped-size/private-mode/cleanup-order/public-success+failure stop fixtures. No real GH/Docker/target call.
checks: AST/source preservation and workflow YAML assertions, Ruff --no-cache and git diff --check PASS; protected bindings/profile/verification/private guards and parent KVM ACL blocks unchanged. Prior lineage/protocol/native/JVM/app/security/secret tests not repeated.
parent_run1:44066116615 stopped at first host KVM admission; no app execution.
parent_run2:44067214109 KVM/setup/cache restore/SDK/host admission PASS; JDK17SDK37/toolchain true, sanitized public_cache1118 files, public container_result after about12s. No private input fetched, strip/app/JNI/VM NOT_RUN; KVM ACL restore + owned cleanup PASS per parent.
unchanged_gates: exact source table/provider/dependency SHA checks, strict metadata, trusted NDK strip proof before private code, two approved JNI identities, private sandbox/export bounds and headless isolation retained. None invoked by diagnose-public.
dns: public hosts: files dns; private files-only/empty resolver/network-none unchanged; no TLS disablement, domain exception, cap or writable host export mount.
admission_STOP: parent reviews exact diff and admits exactly ONE PUBLIC-ONLY third diagnostic. After that admission, affected ART series STOPS regardless of result; no fourth/blind retry without explicit new resumption/corrective evidence. No qualification PASS.
risks: actual public-container root cause UNKNOWN until bounded diagnostic; new artifact capture/upload NOT_RUN here. Public dependency/cache completeness, Linux strip reproduction and all downstream ART/device gates remain unproven; do not infer absence or acceptance.
NOT_RUN_BY_WORKER: network/GitHub/upload/Docker/public Gradle/provisioning/strip/app/JNI/VM/CI/push or further agents; no paid tools. Existing parent bindings retained, no new diagnostic execution admitted by this worker; UI241/focus/API36 remain stopped.
cleanup: pure-check scratch/ZIP/tar fixtures and own Python bytecode removed; no runtime resources created. Fresh worktree remains available for parent's exact diff review; sibling worktrees and app branch untouched.
baseline_correction (append-only): old Claude R1 read stale local main e9a36ad4. Parent reverified remote main b59f9742: AGP9.4.1/Kotlin2.4.20, ci-basic setup-gradle + Free Debug/Release APK+AAB, instrumentation AndroidTest API24/36 already exist. Old "main never used setup-gradle/AndroidTest, AGP8.11.1" statements are false for current main; worker did not query remote.
cache_disposition: hosted AndroidTest cache contents STILL UNKNOWN, never proven absent from that stale inspection. Deterministic public pre-input strict preparation remains valid; no design redo, scope or acceptance change.
