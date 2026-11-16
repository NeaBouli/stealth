// Temporary synthetic-debug qualification only. Never persist/export a private key.
import { generateKeyPairSync, randomBytes, sign, verify } from 'node:crypto';
import { appendFileSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';
import { spawnSync } from 'node:child_process';

const root = process.env.GITHUB_WORKSPACE;
const temp = process.env.RUNNER_TEMP;
if (!root || !temp || process.env.GITHUB_ACTIONS !== 'true') throw Error('Hosted CI only');
const evidence = join(temp, 'sc224-receipts');
mkdirSync(evidence, { recursive: true });
const checkpoints = [
  'empty_denied', 'invalid_denied', 'paid_store_effective',
  'invalid_replacement_denied', 'paid_back_cancel', 'synthetic_not_found_toast',
  'synthetic_security_toast', 'free_back_cancel', 'fixture_cleanup',
];

if (process.argv[2] === 'prepare') {
  if (process.env.GITHUB_RUN_ATTEMPT !== '1') throw Error('One admission; reruns forbidden');
  const { publicKey, privateKey } = generateKeyPairSync('ed25519');
  const jwk = publicKey.export({ format: 'jwk' });
  if (jwk.kty !== 'OKP' || jwk.crv !== 'Ed25519' || !/^[\w-]{43}$/.test(jwk.x)) {
    throw Error('Unexpected public verifier');
  }
  const now = Math.floor(Date.now() / 1000);
  const subject = `sc224-synthetic-${randomBytes(16).toString('hex')}`;
  const tokens = {};
  for (const [tier, price] of [['PRO', '1500'], ['PREMIUM', '2500']]) {
    const lower = tier.toLowerCase();
    const claims = {
      v: '2', iss: 'stealthx', aud: 'securecall', sub: subject, tier,
      product: `vlabs_securecall_${lower}_lifetime`, iat: now, exp: now + 3600,
      order: randomBytes(16).toString('hex'), catalog: 'stealthx-lifetime-v1',
      offer: `securecall-${lower}-eur-${price}-lifetime-v1`,
      release: 'securecall-android-1.0.50-vc78017-api36',
    };
    const payload = Buffer.from(Object.entries(claims).map(([k, v]) => `${k}=${v}`).join('\n'))
      .toString('base64url');
    const signature = sign(null, Buffer.from(payload, 'ascii'), privateKey);
    if (!verify(null, Buffer.from(payload, 'ascii'), publicKey, signature)) {
      throw Error('Synthetic signature self-check failed');
    }
    tokens[tier] = `${payload}.${signature.toString('base64url')}`;
  }
  const assets = join(root, 'client_android/app/src/sc224AndroidTest/assets');
  mkdirSync(assets, { recursive: true });
  writeFileSync(join(assets, 'sc224-synthetic.json'), JSON.stringify({
    syntheticDebugOnly: true, publicKey: jwk.x, subject, issuedAt: now, expiresAt: now + 3600, tokens,
  }), { mode: 0o600, flag: 'wx' });
  // Public verifier only; token bodies never enter logs, argv or artifacts.
  appendFileSync(process.env.GITHUB_ENV, `SC224_PUBLIC_KEY=${jwk.x}\n`);
  // Only this CI invocation adds the overlay; ordinary Free/API24 tests never discover it.
  writeFileSync(join(temp, 'sc224.init.gradle'), `
gradle.beforeProject { project ->
    project.plugins.withId('com.android.application') {
        project.android.sourceSets.named('androidTest') {
            java.srcDir(project.file('src/sc224AndroidTest/java'))
            manifest.srcFile(project.file('src/sc224AndroidTest/AndroidManifest.xml'))
            assets.srcDir(project.file('src/sc224AndroidTest/assets'))
        }
    }
}
`, { mode: 0o600, flag: 'wx' });
  writeFileSync(join(evidence, 'fixture.json'), JSON.stringify({
    syntheticDebugOnly: true, tiers: Object.keys(tokens), issuedAt: now, expiresAt: now + 3600,
    privateKeyExported: false, releaseOrServerProof: false,
  }, null, 2));
} else if (process.argv[2] === 'receipt') {
  const flavor = process.argv[3];
  if (!['pro', 'premium'].includes(flavor)) throw Error('Unsupported flavor');
  const output = readFileSync(join(temp, `sc224-${flavor}.txt`), 'utf8');
  const observed = [...output.matchAll(/^INSTRUMENTATION_STATUS: sc224_checkpoint=(\w+)\r?$/gm)]
    .map(match => match[1]);
  const codes = [...output.matchAll(/^INSTRUMENTATION_STATUS_CODE: (-?\d+)\r?$/gm)]
    .map(match => Number(match[1]));
  const pass = /^OK \(1 test\)\r?$/m.test(output) &&
    /^INSTRUMENTATION_CODE: -1\r?$/m.test(output) &&
    /^INSTRUMENTATION_STATUS: numtests=1\r?$/m.test(output) &&
    /^INSTRUMENTATION_STATUS: test=isolatedSyntheticDebugQualification\r?$/m.test(output) &&
    /^INSTRUMENTATION_STATUS: class=com.securecall.app.sc224.Sc224Qualification\r?$/m.test(output) &&
    codes.filter(code => code === 1).length === 1 && codes.filter(code => code === 0).length === 1 &&
    codes.every(code => [0, 1, 2].includes(code)) &&
    JSON.stringify(observed) === JSON.stringify(checkpoints) &&
    !/FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED|shortMsg=|Error in/.test(output);
  writeFileSync(join(evidence, `${flavor}.json`), JSON.stringify({
    flavor, syntheticDebugOnly: true, pass, testsRequired: 1, checkpoints: observed,
    realBrowserPage: 'NOT_TESTED', protectedSettingsVisuals: 'NOT_CAPTURED',
    releaseOrServerProof: false,
  }, null, 2));
  if (!pass) throw Error('Instrumentation failed, skipped, incomplete or zero tests; no retry');
} else if (process.argv[2] === 'run') {
  // Keep the emulator action's script to one command; execute this block in ONE Bash process.
  const result = spawnSync('bash', ['-euo', 'pipefail', '-c', String.raw`
    stage=root
    before=unavailable after=unavailable delta=unavailable
    ipv6_before=unavailable ipv6_after=unavailable
    trap 'printf "pre-app stage=%s status=%s ipv4_before=%s ipv4_after=%s delta=%s ipv6_before=%s ipv6_after=%s\n" "$stage" "$?" "$before" "$after" "$delta" "$ipv6_before" "$ipv6_after" > "$RUNNER_TEMP/sc224-receipts/pre-app.txt"' EXIT
    adb root
    adb wait-for-device
    test "$(adb shell id -u | tr -d '\r')" = 0
    stage=guest
    test "$(adb shell getprop ro.build.version.sdk | tr -d '\r')" = 36
    test "$(adb shell getprop ro.kernel.qemu | tr -d '\r')" = 1
    stage=fresh-package
    test -z "$(adb shell pm list packages com.securecall.app | tr -d '\r')"
    stage=policy
    # No app, DNS, FCM, backend, provider or loopback exceptions, IPv4 AND IPv6.
    adb shell iptables -w -F OUTPUT
    adb shell ip6tables -w -F OUTPUT
    adb shell iptables -w -P OUTPUT DROP
    adb shell ip6tables -w -P OUTPUT DROP
    verify_isolation() {
      test "$(adb shell iptables -w -S OUTPUT | tr -d '\r')" = '-P OUTPUT DROP'
      test "$(adb shell ip6tables -w -S OUTPUT | tr -d '\r')" = '-P OUTPUT DROP'
    }
    verify_isolation
    # SC224 counter-reader BEGIN
    read_drop_count() {
      local line count='' matches=0
      # Bound both counters to nine digits before any Bash arithmetic.
      local header='^Chain OUTPUT \(policy DROP ([0-9]{1,9}) packets, ([0-9]{1,9}) bytes\)'
      header+=$'\r?$'
      while IFS= read -r line || test -n "$line"; do
        if [[ "$line" == *'Chain OUTPUT'* ]]; then
          [[ "$line" =~ $header ]] || return 1
          read -r _ _ _ _ count _ <<< "$line"
          matches=$((matches + 1))
          test "$matches" = 1 || return 1
        fi
      done < "$1"
      test "$matches" = 1 || return 1
      printf '%s\n' "$((10#$count))"
    }
    # SC224 counter-reader END
    stage='before-counters'
    adb shell iptables -w -L OUTPUT -v -x -n > "$RUNNER_TEMP/sc224-receipts/ipv4-before.txt"
    adb shell ip6tables -w -L OUTPUT -v -x -n > "$RUNNER_TEMP/sc224-receipts/ipv6-before.txt"
    # SC224 counter-before BEGIN
    before=$(read_drop_count "$RUNNER_TEMP/sc224-receipts/ipv4-before.txt")
    ipv6_before=$(read_drop_count "$RUNNER_TEMP/sc224-receipts/ipv6-before.txt")
    # SC224 counter-before END
    stage=probe
    if timeout 5 adb shell ping -I lo -c 1 -W 1 127.0.0.1; then exit 1; fi
    stage='after-counters'
    adb shell iptables -w -L OUTPUT -v -x -n > "$RUNNER_TEMP/sc224-receipts/ipv4-after.txt"
    adb shell ip6tables -w -L OUTPUT -v -x -n > "$RUNNER_TEMP/sc224-receipts/ipv6-after.txt"
    # SC224 counter-after BEGIN
    after=$(read_drop_count "$RUNNER_TEMP/sc224-receipts/ipv4-after.txt")
    ipv6_after=$(read_drop_count "$RUNNER_TEMP/sc224-receipts/ipv6-after.txt")
    delta=$((after - before))
    test "$delta" -gt 0
    # SC224 counter-after END
    stage=verified
    verify_isolation
    echo 'API36 disposable AVD; IPv4/IPv6 OUTPUT DROP with zero exceptions verified BEFORE ANY target install/process' > "$RUNNER_TEMP/sc224-receipts/isolation.txt"
    printf 'pre-app stage=verified status=0 ipv4_before=%s ipv4_after=%s delta=%s ipv6_before=%s ipv6_after=%s\n' "$before" "$after" "$delta" "$ipv6_before" "$ipv6_after" > "$RUNNER_TEMP/sc224-receipts/pre-app.txt"
    trap - EXIT
    cleanup() {
      local failed=0
      for flavor in pro premium; do
        for suffix in .devtest.test .devtest; do
          local package="com.securecall.app.$flavor$suffix"
          if adb shell pm list packages "$package" | tr -d '\r' | grep -Fxq "package:$package"; then
            adb uninstall "$package" || failed=1
          fi
        done
      done
      return "$failed"
    }
    trap cleanup EXIT
    for flavor in pro premium; do
      verify_isolation
      app="com.securecall.app.$flavor.devtest"
      app_apk="client_android/app/build/outputs/apk/$flavor/debug/app-$flavor-universal-debug.apk"
      test_apk="client_android/app/build/outputs/apk/androidTest/$flavor/debug/app-$flavor-debug-androidTest.apk"
      test -s "$app_apk"
      test -s "$test_apk"
      timeout 60 adb install --no-streaming -t "$app_apk"
      timeout 60 adb install --no-streaming -t "$test_apk"
      test -z "$(adb shell pidof "$app" | tr -d '\r')"
      test -z "$(adb shell pidof "$app.test" | tr -d '\r')"
      adb shell pm set-app-links --package "$app.test" 2 stealthx.tech
      test "$(adb shell cmd package resolve-activity --brief -a android.intent.action.VIEW -d https://stealthx.tech/download.html | tr -d '\r' | tail -n 1)" = "$app.test/com.securecall.app.sc224.Sc224ReceiverActivity"
      verify_isolation
      result=0
      timeout 180 adb shell am instrument -w -r \
        -e class com.securecall.app.sc224.Sc224Qualification \
        -e sc224PreAppIsolation true \
        "$app.test/androidx.test.runner.AndroidJUnitRunner" > "$RUNNER_TEMP/sc224-$flavor.txt" 2>&1 || result=$?
      node .github/scripts/sc224-fixture.mjs receipt "$flavor"
      test "$result" = 0
      verify_isolation
      for tier in "$flavor" free; do
        for action in back cancel; do
          adb exec-out run-as "$app" cat "files/sc224-$tier-$action.png" > "$RUNNER_TEMP/sc224-receipts/$flavor-$tier-$action-synthetic.png"
          test -s "$RUNNER_TEMP/sc224-receipts/$flavor-$tier-$action-synthetic.png"
        done
      done
      adb uninstall "$app.test"
      adb uninstall "$app"
    done
    verify_isolation
    echo 'both flavors passed; IPv4/IPv6 OUTPUT DROP unchanged; own packages uninstalled' >> "$RUNNER_TEMP/sc224-receipts/isolation.txt"
  `], { stdio: 'inherit', timeout: 600_000 });
  if (result.error || result.status !== 0) throw Error('One isolated qualification attempt failed; no retry');
} else {
  throw Error('Expected prepare, receipt or run');
}
