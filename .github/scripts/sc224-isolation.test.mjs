import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import test from 'node:test';

// Read trusted source only: importing the fixture would enter its hosted/signing/ADB paths.
const source = readFileSync(new URL('./sc224-fixture.mjs', import.meta.url), 'utf8');
const workflow = readFileSync(new URL('../workflows/sc224-isolated-qualification.yml', import.meta.url), 'utf8');
const start = source.indexOf('String.raw`\n');
const end = source.indexOf("`], { stdio: 'inherit', timeout: 600_000 });", start);
assert.ok(start >= 0 && end > start);
const bash = source.slice(start + 'String.raw`\n'.length, end);
assert.ok(!bash.includes('${'), 'extracted Bash must not contain JS interpolation');
function fragment(name) {
  const begin = `# SC224 ${name} BEGIN\n`;
  const finish = `# SC224 ${name} END`;
  assert.equal(bash.split(begin).length, 2);
  assert.equal(bash.split(finish).length, 2);
  return bash.slice(bash.indexOf(begin) + begin.length, bash.indexOf(finish));
}
const reader = fragment('counter-reader');
const comparison = fragment('counter-before') + fragment('counter-after');
const header = (packets, bytes = '0', policy = 'DROP') =>
  `Chain OUTPUT (policy ${policy} ${packets} packets, ${bytes} bytes)\n pkts bytes target prot opt in out source destination\n`;

function check(before, after, expected, ipv6 = header('0'), omit = '') {
  const dir = mkdtempSync(join(tmpdir(), 'sc224-source-'));
  try {
    const receipts = join(dir, 'sc224-receipts');
    mkdirSync(receipts);
    for (const [name, content] of Object.entries({
      'ipv4-before': before, 'ipv4-after': after,
      'ipv6-before': ipv6, 'ipv6-after': ipv6,
    })) {
      if (name !== omit) writeFileSync(join(receipts, `${name}.txt`), content);
    }
    const result = spawnSync('bash', ['-euo', 'pipefail', '-c',
      reader + comparison + '\nprintf "%s %s %s\\n" "$before" "$after" "$delta"'], {
      env: { PATH: process.env.PATH, RUNNER_TEMP: dir }, encoding: 'utf8', timeout: 2000,
    });
    assert.ifError(result.error);
    if (expected === null) assert.notEqual(result.status, 0, 'must refuse invalid/non-increasing counters');
    else {
      assert.equal(result.status, 0, result.stderr);
      assert.equal(result.stdout.trim(), expected);
    }
  } finally {
    rmSync(dir, { recursive: true, force: true });
  }
}

test('actual Bash reader/comparison: positive delta, CRLF, leading zeros, upper bound', () => {
  check(header('0'), header('1'), '0 1 1');
  check(header('7').replaceAll('\n', '\r\n'), header('9'), '7 9 2');
  check(header('000000008'), header('000000009'), '8 9 1');
  check(header('999999998'), header('999999999'), '999999998 999999999 1');
});

test('actual comparison refuses zero, pre-existing nonzero and decreasing counters', () => {
  for (const [before, after] of [['0', '0'], ['9', '9'], ['9', '8']]) {
    check(header(before), header(after), null);
  }
});

test('actual reader refuses missing, malformed, duplicate, negative, oversized and non-DROP headers', () => {
  const invalid = [
    '', 'unrelated output\n', 'Chain OUTPUT\n', header('x'), header('-1'), header('+1'),
    header('1.5'), header('1000000000'), header('99999999999999999999'),
    header('1', '-1'), header('1', '1000000000'), header('1', '0', 'ACCEPT'),
    header('1', '0', 'REJECT'), header('1') + header('2'),
    header('1') + 'Chain OUTPUT malformed\n', header('1').replace(' packets,', ' packets'),
    header('1') + ' ' + header('2'), ' ' + header('1'), header('1', 'bad'),
  ];
  for (const content of invalid) {
    check(content, header('2'), null);
    check(header('0'), content, null);
    check(header('0'), header('1'), null, content);
  }
  for (const missing of ['ipv4-before', 'ipv4-after', 'ipv6-before', 'ipv6-after']) {
    check(header('0'), header('1'), null, header('0'), missing);
  }
});

test('whole embedded Bash syntax and exact pre-app witness ordering', () => {
  const syntax = spawnSync('bash', ['-n'], { input: bash, encoding: 'utf8', timeout: 2000 });
  assert.equal(syntax.status, 0, syntax.stderr);
  const probe = 'if timeout 5 adb shell ping -I lo -c 1 -W 1 127.0.0.1; then exit 1; fi';
  assert.equal(bash.split('adb shell ping').length, 2, 'one probe only');
  const ordered = ['verify_isolation\n', '# SC224 counter-before END', probe,
    '# SC224 counter-after BEGIN', '# SC224 counter-after END',
    'stage=verified\n    verify_isolation', 'timeout 60 adb install'];
  let position = -1;
  for (const text of ordered) {
    position = bash.indexOf(text, position + 1);
    assert.ok(position >= 0, text);
  }
  assert.ok(bash.includes('> "$RUNNER_TEMP/sc224-receipts/pre-app.txt"'));
  assert.ok(bash.includes('trap - EXIT\n    cleanup()'));
});

test('strict isolation, admission, timeout, validator and privacy guards remain present', () => {
  for (const text of [
    "process.env.GITHUB_ACTIONS !== 'true'", "process.env.GITHUB_RUN_ATTEMPT !== '1'",
    'adb root', 'test "$(adb shell id -u | tr -d \'\\r\')" = 0',
    'test "$(adb shell getprop ro.build.version.sdk | tr -d \'\\r\')" = 36',
    'test "$(adb shell getprop ro.kernel.qemu | tr -d \'\\r\')" = 1',
    'test -z "$(adb shell pm list packages com.securecall.app | tr -d \'\\r\')"',
    "JSON.stringify(observed) === JSON.stringify(checkpoints)",
    'codes.every(code => [0, 1, 2].includes(code))', 'privateKeyExported: false',
    "realBrowserPage: 'NOT_TESTED'", 'releaseOrServerProof: false',
    'timeout: 600_000', 'timeout 180 adb shell am instrument', 'trap cleanup EXIT',
    'adb uninstall "$app.test"', 'adb uninstall "$app"',
  ]) assert.ok(source.includes(text), text);
  for (const tool of ['iptables', 'ip6tables']) {
    assert.ok(bash.includes(`adb shell ${tool} -w -F OUTPUT`));
    assert.ok(bash.includes(`adb shell ${tool} -w -P OUTPUT DROP`));
    assert.ok(bash.includes(`test "$(adb shell ${tool} -w -S OUTPUT | tr -d '\\r')" = '-P OUTPUT DROP'`));
  }
  assert.doesNotMatch(bash, /\b(?:iptables|ip6tables)\s+-w\s+-(?:A|I)\b/);
  assert.equal(bash.split('timeout 60 adb install').length, 3);
  assert.ok(workflow.includes('test "$HEAD_BRANCH" = agent/codex/SC224-ISOLATION-WITNESS-20261116'));
  assert.ok(workflow.includes('SC224_BASE: 0dafd8135b8a6c675a6a3be870d18aa09c555909'));
  assert.ok(workflow.includes("':(exclude).github/scripts/sc224-isolation.test.mjs'"));
  const pureTest = workflow.indexOf('node --test .github/scripts/sc224-isolation.test.mjs');
  assert.ok(pureTest >= 0 && pureTest < workflow.indexOf('node .github/scripts/sc224-fixture.mjs prepare'));
});
