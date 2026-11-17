id: SC-JNI-ART-NATIVE-20261117
status: partial / NOT_RUN; BLOCKED native packaging lineage; bindings NOT_READY
worker: codex (exclusive source continuation; no other agents)
branch: agent/codex/SC-JNI-ART-NATIVE-20261117
base: a9e2977eb9dbf324848e7b0b4c3adfb300414549
summary: TEMP Hop A0 public preparation -> Hop B offline APK -> Hop C eight headless API24 JNI cases -> host-only DRAFT receipt; source implemented, never executed/admitted.
files: .github/scripts/sc-media-qualification.py
files: .github/scripts/sc-art-headless.sh (one directly related runner)
files: .github/workflows/android-instrumentation.yml; docs/architecture/MAP.md; this report
source: d1eefa872cf9995e840e5ed671f14df983f75c87 / tree2bf04aa294f24fe246ee8ae142b19699fd5025a9
source_table: independently counted799 code files incl. root export guard, excluding original provider; SHA25666e57da8cc2b24e4d6a56354b52ec870299332a3562149947358baa2df7f13f9
verification_xml: SHA25651e401f56ef9e68cf1fa279b731fc7b94d1404fbd4d1d7a31a5ec46b3965c4f5
guard: tools/verify_android_crypto_exports.sh SHA256a20d3740d7ef133e8ea9c4937440eed603468b6331e234908e53f85f6401d964
native_arm64: 2782617de7793cc0040bae2ffff3588350e78734dffe91aaa83edf1f461e4f9f
native_armv7: 01b0ec8a1fda6363baf30eda275cd142f4857743ced8b012798c47afd74d4280
native_x86_64: 4a932002caab2e585f56950f5ea35d587d4f13b588c62549f5ad513eaf207c59
provider: unchanged synthetic fixture SHA256a466488db1f666da3f1b941ad4c389a6b7940d5c93662adf25f77fb781fd63c7
public: controller-authored plugin-free project, pinned Gradle9.8.0/JDK17/SDK37/AGP9.4.1 metadata, four AndroidTest roots + prefab2.1.0-all/aapt2-9.4.1-15978811-linux; six root artifact SHA256 pins rechecked against exact XML. Prior28-module closure accepted, not re-executed.
metadata: read-only immutable public XML fetch/hash before private input; sanitized verified seed -> fresh tmpfs Gradle home -> strict resolve -> selected artifacts/metadata-2.107 -> host SHA256/path recheck. No repository wrapper/scripts with network, init bypass, rule change, blanket cache copy or new fixture.
profile: private network-none/--offline/no -Pinternal; nonroot/read-only/capdrop/NNP; 1CPU/4GiB/no swap/256pids/3GiB scratch/CPU840/wall900/file512MiB/output512KiB. Public bridge phase has same caps, no token/homes/private inputs; resolved single RO awk alias included in create/inspect/kernel checks.
export: NO RW HOST MOUNT. Owned container idles; bounded docker-cp tar from existing tmpfs, <=129MiB capture, exactly two <=64MiB APKs plus <=4KiB receipt; host compares APK SHA and mandatory raw x86_64 JNI SHA. Test APK may omit JNI, never contain a mismatching copy.
headless: token-free owned transient user scope (no service install/sudo/fallback), fresh HOME/AVD/dedicated ADB port; same resource ceilings, existing user KVM required; copied23718a78 isolation functions byte-identical, API24-only wrapper. Pre-install DROP/probe, held through shutdown; exact eight paired starts/successes, zero skips/errors and exact OK(8 tests)/terminal code.
retention: host token uploads <=16KiB synthetic JSON only to fresh pinned STILL-DRAFT; no APK/raw-log/Actions artifact upload. Exact owned container/image/cgroups/scope/guest/ADB/temp cleanup; kernel scope path retained through unit collection for empty/absent check.
checks: python3 -I - AST/source-contract/provider/path/XML checks PASS; 23 synthetic instrumentation parser fixtures PASS; seven synthetic ZIP parser fixtures PASS; nine mocked capture parser fixtures PASS (NO Docker call). Initial missing-JNI generic KeyError normalized to fixed refusal, fixtures rerun PASS.
checks: NOT_READY and unresolved-lineage pre-execution refusals PASS; accepted isolation function body byte comparison PASS; Ruby Psych YAML parse/disabled-ref gate PASS; bash -n, shellcheck, ruff check --no-cache and git diff --check PASS.
strip_evidence: installed public AGP9.4.1 JAR SHA2564021fa6ea76b6fac076dd0bdacbb3e1b76c31f33b6c392e1a49a2e42072196bf matches d1eefa87 verification metadata; javap -c -p only, no AGP classes initialized/executed.
strip_path: AbstractAppTaskManager registers StripDebugSymbolsTask$CreationAction; MERGED_NATIVE_LIBS -> STRIPPED_NATIVE_LIBS; keep set = doNotStrip union jniLibs.keepDebugSymbols; nonmatching input reaches StripDebugSymbolsRunnable --strip-unneeded -o output input. App has no keepDebugSymbols/doNotStrip/jniDebuggable declaration.
BLOCKER: raw APK JNI SHA equality is NOT_PROVEN (legitimate stripping may differ). Raw identity checks remain strict; dispatch_guard hard-refuses apk_native_strip_lineage_unresolved even after bindings change. No strip transform/tool/output digest guessed or executed, no app build configuration changed.
admission: parent must FIRST prove raw packaging identity or implement/adopt exact trusted pinned NDK strip-transform/tool/output receipt, then review exact diff; only then resolve that hard gate and pin controller SHA/ref/fresh draft/input SHA/emulator+image revisions and adopt user-scope/KVM runtime.
risks: restored cache/variant selection/repository-id compatibility/JDK CA reachability/scratch capacity/APK names+sizes/user-manager delegation and KVM/image revisions remain UNKNOWN until separately admitted runtime; no weaker launcher/cache/profile fallback.
NOT_RUN: network, Gradle/APK build, Docker, provisioning, emulator, all eight real JNI cases, CI/GitHub/push, native rebuild/prior tests, providers/servers/secrets/customer operations. UI241/focus/API36 remain stopped; no binding activated, no paid tools.
cleanup: pure-check scratch/ZIP/tar fixtures and own Python bytecode removed; no runtime resources created. Fresh worktree remains available for parent's exact diff review; sibling worktrees and app branch untouched.
