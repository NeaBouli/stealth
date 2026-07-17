const assert = require("assert");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { execFileSync, spawnSync } = require("child_process");

const signalingRoot = path.resolve(__dirname, "../..");
const repositoryRoot = path.resolve(signalingRoot, "../..");
const script = path.join(signalingRoot, "scripts", "generate-entitlement-keypair.mjs");

function run(privateFile, publicFile) {
  return execFileSync(process.execPath, [
    script,
    `--private-file=${privateFile}`,
    `--public-file=${publicFile}`,
    "--json",
  ], { encoding: "utf8" });
}

const root = fs.mkdtempSync(path.join(os.tmpdir(), "stealthx-entitlement-key-"));
const privateDirectory = path.join(root, "private");
const publicDirectory = path.join(root, "public");
fs.mkdirSync(privateDirectory, { mode: 0o700 });
fs.mkdirSync(publicDirectory, { mode: 0o755 });
fs.chmodSync(privateDirectory, 0o700);
fs.chmodSync(publicDirectory, 0o755);
const privateFile = path.join(privateDirectory, "entitlement-private.pem");
const publicFile = path.join(publicDirectory, "entitlement-public.txt");

try {
  const output = run(privateFile, publicFile);
  const result = JSON.parse(output);
  assert.strictEqual(result.ok, true);
  assert.strictEqual(result.privateFile, fs.realpathSync(privateFile));
  assert.strictEqual(result.publicFile, fs.realpathSync(publicFile));
  assert.match(result.publicKeyFingerprint, /^[a-f0-9]{64}$/);
  assert.strictEqual(output.includes("BEGIN PRIVATE KEY"), false, "private PEM must never be printed");
  assert.strictEqual(fs.statSync(privateFile).mode & 0o777, 0o600);
  assert.strictEqual(fs.statSync(publicFile).mode & 0o777, 0o644);

  const privatePem = fs.readFileSync(privateFile, "utf8");
  const publicBase64url = fs.readFileSync(publicFile, "utf8").trim();
  assert.match(privatePem, /^-----BEGIN PRIVATE KEY-----/);
  assert.match(publicBase64url, /^[A-Za-z0-9_-]{43}$/);
  const privateKey = crypto.createPrivateKey(privatePem);
  const derivedPublic = crypto.createPublicKey(privateKey).export({ format: "jwk" }).x;
  assert.strictEqual(derivedPublic, publicBase64url);
  const proof = Buffer.from("stealthx-entitlement-provisioning-proof", "utf8");
  assert.strictEqual(crypto.verify(null, proof, crypto.createPublicKey(privateKey), crypto.sign(null, proof, privateKey)), true);

  const privateBefore = fs.readFileSync(privateFile);
  const publicBefore = fs.readFileSync(publicFile);
  const rerun = spawnSync(process.execPath, [
    script,
    `--private-file=${privateFile}`,
    `--public-file=${publicFile}`,
    "--json",
  ], { encoding: "utf8" });
  assert.strictEqual(rerun.status, 1);
  assert.match(rerun.stderr, /key file already exists/);
  assert.deepStrictEqual(fs.readFileSync(privateFile), privateBefore, "existing private key must not change");
  assert.deepStrictEqual(fs.readFileSync(publicFile), publicBefore, "existing public key must not change");

  const unsafePrivate = path.join(repositoryRoot, "entitlement-private-test.pem");
  const unsafePublic = path.join(publicDirectory, "unsafe-public.txt");
  const inRepository = spawnSync(process.execPath, [
    script,
    `--private-file=${unsafePrivate}`,
    `--public-file=${unsafePublic}`,
  ], { encoding: "utf8" });
  assert.strictEqual(inRepository.status, 1);
  assert.match(inRepository.stderr, /outside the repository/);
  assert.strictEqual(fs.existsSync(unsafePrivate), false);
  assert.strictEqual(fs.existsSync(unsafePublic), false);

  const permissiveDirectory = path.join(root, "permissive");
  fs.mkdirSync(permissiveDirectory, { mode: 0o755 });
  fs.chmodSync(permissiveDirectory, 0o755);
  const permissive = spawnSync(process.execPath, [
    script,
    `--private-file=${path.join(permissiveDirectory, "private.pem")}`,
    `--public-file=${path.join(publicDirectory, "permissive-public.txt")}`,
  ], { encoding: "utf8" });
  assert.strictEqual(permissive.status, 1);
  assert.match(permissive.stderr, /must not grant group\/world access/);

  const cleanupPrivate = path.join(privateDirectory, "cleanup-private.pem");
  const overlongPublic = path.join(publicDirectory, `${"x".repeat(300)}.txt`);
  const partialFailure = spawnSync(process.execPath, [
    script,
    `--private-file=${cleanupPrivate}`,
    `--public-file=${overlongPublic}`,
  ], { encoding: "utf8" });
  assert.strictEqual(partialFailure.status, 1);
  assert.match(partialFailure.stderr, /Entitlement key generation failed/);
  assert.strictEqual(fs.existsSync(cleanupPrivate), false, "private key must be removed after public write failure");
} finally {
  fs.rmSync(root, { recursive: true, force: true });
}

console.log("entitlement_keypair_tool.test.js ok");
