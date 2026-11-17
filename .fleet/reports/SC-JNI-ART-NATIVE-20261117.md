id: SC-JNI-ART-NATIVE-20261117
status: partial / NOT_RUN; exact transform design authorized, Linux proof remains mandatory; bindings NOT_READY
worker: codex (exclusive source continuation; no other agents)
branch: agent/codex/SC-JNI-ART-NATIVE-20261117
base: a9e2977eb9dbf324848e7b0b4c3adfb300414549
continuation_base: dc56369bbab98a1b913e7a4fbe6081d293f1ca48; only controller/MAP/report changed in this continuation
summary: TEMP public preparation -> verified input -> trusted host strip proof -> offline APK -> eight headless API24 JNI cases -> host-only DRAFT receipt; source only, runtime not admitted.
files: .github/scripts/sc-media-qualification.py
files: .github/scripts/sc-art-headless.sh (one directly related runner)
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
export: NO RW HOST MOUNT. Owned container idles; bounded docker-cp tar from existing tmpfs, <=129MiB capture, exactly two <=64MiB APKs plus <=4KiB receipt; both APK parsers accept ONLY the two exact JNI hash+size pairs, recording raw/ndk27-strip-unneeded encoding. Test APK may omit JNI, never contain a mismatching copy.
headless: token-free owned transient user scope (no service install/sudo/fallback), fresh HOME/AVD/dedicated ADB port; same resource ceilings, existing user KVM required; copied23718a78 isolation functions byte-identical, API24-only wrapper. Pre-install DROP/probe, held through shutdown; exact eight paired starts/successes, zero skips/errors and exact OK(8 tests)/terminal code.
retention: host token uploads <=16KiB synthetic JSON only to fresh pinned STILL-DRAFT; no APK/raw-log/Actions artifact upload. Exact owned container/image/cgroups/scope/guest/ADB/temp cleanup; kernel scope path retained through unit collection for empty/absent check.
checks: python3 -I .github/scripts/sc-media-qualification.py lineage-checks -> lineage_zip=32 generated_profiles=2 target_execution=false PASS. Durable pure synthetic raw/stripped ZIP, mismatch/size/duplicate/absence, receipt revision/tool/flags/identity/bounds and missing-proof refusal fixtures; no strip or Docker call.
checks: AST comparison against dc56369b PASS: all other dispatch/source/cache/tool/kernel/export/headless guards and Docker CREATE/INSPECT limits unchanged; proof after verified extraction/before private build; clean environment/timeout. Ruff --no-cache and git diff --check PASS. Prior protocol/native/JVM tests not repeated.
prior_strip_evidence: dc56369b verified public AGP9.4.1 JAR SHA2564021fa6ea76b6fac076dd0bdacbb3e1b76c31f33b6c392e1a49a2e42072196bf against exact metadata; javap static inspection only, no AGP classes initialized/executed.
strip_path: AbstractAppTaskManager registers StripDebugSymbolsTask$CreationAction; MERGED_NATIVE_LIBS -> STRIPPED_NATIVE_LIBS; keep set = doNotStrip union jniLibs.keepDebugSymbols; nonmatching input reaches StripDebugSymbolsRunnable --strip-unneeded -o output input. App has no keepDebugSymbols/doNotStrip/jniDebuggable declaration.
lineage_gate: replaced ONLY resolved strip refusal with mandatory host proof prerequisite in private run_container. After verified input, trusted existing Linux NDK27.0.12077973 llvm-strip operates on a bounded raw copy with exactly --strip-unneeded (-o output input), token-free clean env, CPU30/wall30/file512KiB/output512KiB/nofile64/core0; must reproduce exact parent hash+size before app code.
lineage_receipt: final capped synthetic receipt includes actual Linux tool SHA256/size/resolved public path (observed before+after, never guessed), NDK revision, flags/argv, unchanged raw input, exact output, exit and bounds. Temporary copies/diagnostic deleted; no app/Gradle keepDebugSymbols change, native rebuild or identity bypass.
dns_fix: ONLY public generated nsswitch hosts policy becomes files dns; private byte-identical files-only, empty resolver, network-none. Tiny pure generator assertions PASS; no mount/cap/domain bypass or TLS disablement.
admission: parent reviews exact continuation first, then pins controller SHA/ref/fresh draft/input SHA/emulator+image revisions and adopts user-scope/KVM runtime. Existing guards stay closed by default. Linux proof MUST succeed during any admitted run before private app code; no Android/JNI acceptance inferred.
risks: actual Linux strip reproduction/tool SHA, restored cache/variant selection/repository-id compatibility/DNS+JDK CA reachability/scratch capacity/APK names+sizes/user-manager delegation and KVM/image revisions remain UNKNOWN until admitted runtime; no weaker fallback.
NOT_RUN: strip/tool invocation, network, Gradle/APK build, Docker, provisioning, emulator, all eight real JNI cases, CI/GitHub/push, native rebuild/prior tests, providers/servers/secrets/customer operations. UI241/focus/API36 remain stopped; no binding activated, no paid tools.
cleanup: pure-check scratch/ZIP/tar fixtures and own Python bytecode removed; no runtime resources created. Fresh worktree remains available for parent's exact diff review; sibling worktrees and app branch untouched.
baseline_correction (append-only): old Claude R1 read stale local main e9a36ad4. Parent reverified remote main b59f9742: AGP9.4.1/Kotlin2.4.20, ci-basic setup-gradle + Free Debug/Release APK+AAB, instrumentation AndroidTest API24/36 already exist. Old "main never used setup-gradle/AndroidTest, AGP8.11.1" statements are false for current main; worker did not query remote.
cache_disposition: hosted AndroidTest cache contents STILL UNKNOWN, never proven absent from that stale inspection. Deterministic public pre-input strict preparation remains valid; no design redo, scope or acceptance change.
