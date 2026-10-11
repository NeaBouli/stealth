"use strict";

const assert = require("assert/strict");
const fs = require("fs");
const os = require("os");
const path = require("path");
const crypto = require("crypto");
const { spawn } = require("child_process");
const { createTesterLicenseRegistry } = require("../services/tester_license_registry");
const { verifyTesterEntitlement, TTL_SECONDS } = require("../payments/tester_entitlement_tokens");
const dir = fs.mkdtempSync(path.join(fs.realpathSync(os.tmpdir()), "tester-registry-synthetic-"));
fs.chmodSync(dir, 0o700);
const file = path.join(dir, "registry.json");
const pair = crypto.generateKeyPairSync("ed25519");
const privateKey = pair.privateKey.export({ type: "pkcs8", format: "pem" });
const publicKey = pair.publicKey.export({ type: "spki", format: "pem" });
const hash = data => crypto.createHash("sha256").update(data).digest("hex");
function device() {
  const pair = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const der = pair.publicKey.export({ type: "spki", format: "der" });
  return { pair, hash: hash(der), publicKey: der.toString("base64url"),
    publicKeyPem: pair.publicKey.export({ type: "spki", format: "pem" }),
    package: "com.securecall.app.premium", assurance: "p256-key-possession-v1", status: "active" };
}
const a = device(), b = device(), unknown = device();
const code = `SC-PREM-${"A".repeat(48)}`;
const initial = { schema: 1, grants: [{ id: "b".repeat(64), codeHash: hash(code), status: "active", binding: null }],
  enrolledKeys: [] };
let now = 1700000000;
const save = data => fs.writeFileSync(file, JSON.stringify(data), { mode: 0o600 });
const load = () => JSON.parse(fs.readFileSync(file));
const registry = () => createTesterLicenseRegistry({ file, privateKey, now: () => now });
const begin = (service, key = a, subject = "synthetic-A") => service.begin({ code, subject,
  publicKey: key.publicKey, packageName: key.package });
const prove = (challenge, key = a, subject = "synthetic-A") => ({ challengeId: challenge.challengeId, subject,
  signature: crypto.sign("sha256", Buffer.from(challenge.challenge), key.pair.privateKey).toString("base64url") });
(async () => { try {
  save(initial);
  const service = registry();
  assert.throws(() => service.begin({code,subject:[],publicKey:a.publicKey,packageName:a.package}));
  const malformed = structuredClone(initial);
  malformed.enrolledKeys.push({hash:"0".repeat(64),publicKeyPem:a.publicKeyPem,package:a.package,
    assurance:a.assurance,status:"active"}); save(malformed);
  assert.throws(() => begin(service), {message:"tester_license_unavailable"});
  const dangling = structuredClone(initial);
  dangling.grants[0].binding = {subject:"synthetic-A",keyHash:a.hash}; save(dangling);
  assert.throws(() => begin(service), {message:"tester_license_unavailable"});
  fs.writeFileSync(file, "invalid synthetic JSON");
  assert.throws(() => begin(service), {message:"tester_license_unavailable"});
  save(initial);
  assert.doesNotThrow(() => begin(service, unknown));
  assert.throws(() => service.begin({ code: "bad", subject: "synthetic-A", publicKey: a.publicKey, packageName: a.package }));
  const rsa = crypto.generateKeyPairSync("rsa", {modulusLength:2048}).publicKey
    .export({type:"spki",format:"der"}).toString("base64url");
  assert.throws(() => service.begin({code,subject:"synthetic-A",publicKey:rsa,packageName:a.package}));
  assert.throws(() => service.begin({code,subject:"synthetic-A",publicKey:`${a.publicKey}=`,packageName:a.package}));
  let state = load(); state.grants[0].status = "inactive"; save(state);
  assert.throws(() => begin(service));
  save(initial);
  const serviceB = registry();
  const challengeA = begin(service), challengeB = begin(serviceB, b, "synthetic-B");
  const token = service.activate(prove(challengeA));
  assert.equal(verifyTesterEntitlement(token, { subject: "synthetic-A", deviceKeyHash: a.hash, publicKey, nowSeconds: now }).tier, "PREMIUM");
  assert.deepEqual(load().enrolledKeys, [{hash:a.hash,publicKeyPem:a.publicKeyPem,package:a.package,
    assurance:"p256-key-possession-v1",status:"active"}]);
  assert.throws(() => service.activate(prove(challengeA)));
  assert.throws(() => serviceB.activate(prove(challengeB, b, "synthetic-B")));
  assert.throws(() => begin(registry(), b, "synthetic-B"));
  assert.throws(() => begin(registry(), b, "synthetic-A"));
  const restarted = registry();
  assert.equal(restarted.activate(prove(begin(restarted))), token);
  assert.deepEqual(load().grants[0].binding, { subject: "synthetic-A", keyHash: a.hash });
  assert.equal(fs.statSync(file).mode & 0o777, 0o600);

  now += TTL_SECONDS;
  const renewal = restarted.beginRefresh({ token, subject: "synthetic-A", keyHash: a.hash });
  const renewed = restarted.refresh(prove(renewal));
  assert.equal(verifyTesterEntitlement(renewed, { subject: "synthetic-A", deviceKeyHash: a.hash, publicKey, nowSeconds: now }).grant, initial.grants[0].id);
  assert.throws(() => restarted.refresh(prove(renewal)));
  const forged = restarted.beginRefresh({ token: renewed, subject: "synthetic-A", keyHash: a.hash });
  assert.throws(() => restarted.refresh(prove(forged, b)));
  state = load(); state.grants[0].status = "revoked"; save(state);
  assert.throws(() => restarted.beginRefresh({token:renewed,subject:"synthetic-A",keyHash:a.hash}));
  state.grants[0].status = "active"; save(state);
  const revoked = restarted.beginRefresh({ token: renewed, subject: "synthetic-A", keyHash: a.hash });
  state.grants[0].status = "revoked"; save(state);
  assert.throws(() => restarted.refresh(prove(revoked)));
  state.grants[0].status = "active";
  state.enrolledKeys[0].status = "revoked"; save(state);
  assert.throws(() => begin(restarted));
  assert.throws(() => restarted.beginRefresh({token:renewed,subject:"synthetic-A",keyHash:a.hash}));
  state.enrolledKeys[0].status = "active"; save(state);
  fs.writeFileSync(file, "invalid synthetic JSON");
  assert.throws(() => restarted.beginRefresh({token:renewed,subject:"synthetic-A",keyHash:a.hash}));
  save(state);
  now += TTL_SECONDS + 7 * 86400 + 1;
  assert.throws(() => restarted.beginRefresh({token:renewed,subject:"synthetic-A",keyHash:a.hash}));

  save(initial);
  const blocked = begin(service);
  fs.writeFileSync(`${file}.lock`, "synthetic-other-writer", { mode: 0o600 });
  assert.throws(() => service.activate(prove(blocked)));
  assert.equal(load().grants[0].binding, null);
  fs.unlinkSync(`${file}.lock`);
  const failing = begin(service);
  const originalRename = fs.renameSync;
  fs.renameSync = () => { throw new Error("synthetic storage failure"); };
  try { assert.throws(() => service.activate(prove(failing))); }
  finally { fs.renameSync = originalRename; }
  assert.equal(load().grants[0].binding, null);
  const expired = begin(service); now += 301;
  assert.throws(() => service.activate(prove(expired)));
  fs.chmodSync(file, 0o644);
  assert.throws(() => registry());
  fs.chmodSync(file, 0o600);
  save(initial);
  const workerCode = `
    const crypto=require('crypto');
    const {createTesterLicenseRegistry}=require(${JSON.stringify(require.resolve("../services/tester_license_registry"))});
    let service, challenge, fixture;
    process.on('message', msg=>{
      if(msg.go) {
        let ok=false;
        try { service.activate({challengeId:challenge.challengeId,subject:fixture.subject,
          signature:crypto.sign('sha256',Buffer.from(challenge.challenge),fixture.devicePrivateKey).toString('base64url')});ok=true; } catch {}
        process.send({ok},()=>process.exit(0));
      } else {
        fixture=msg;
        service=createTesterLicenseRegistry({file:msg.file,privateKey:msg.privateKey,now:()=>msg.now});
        challenge=service.begin({code:msg.code,subject:msg.subject,publicKey:msg.publicKey,packageName:msg.packageName});
        process.send({ready:true});
      }
    });`;
  const children = [];
  try {
    const workers = [a, b].map((key, i) => {
      const child = spawn(process.execPath, ["-e", workerCode], { stdio: ["ignore", "ignore", "ignore", "ipc"] });
      children.push(child);
      let readyResolve, resultResolve, readyReject, resultReject;
      const ready = new Promise((resolve, reject) => { readyResolve=resolve; readyReject=reject; });
      const result = new Promise((resolve, reject) => { resultResolve=resolve; resultReject=reject; });
      // Register rejection consumers immediately while the other worker starts.
      result.catch(() => {});
      const timer = setTimeout(() => { readyReject(new Error("worker timeout")); resultReject(new Error("worker timeout")); child.kill(); }, 15000);
      child.on("message", msg => { if (msg.ready) readyResolve(); else resultResolve(msg.ok); });
      const exited = new Promise(resolve => child.on("exit", code => {
        clearTimeout(timer); if(code !== 0) { readyReject(new Error("worker failed")); resultReject(new Error("worker failed")); } resolve();
      }));
      child.on("error", () => { readyReject(new Error("worker error")); resultReject(new Error("worker error")); });
      child.send({file,privateKey,now,code,subject:`synthetic-${i}`,publicKey:key.publicKey,packageName:key.package,
        devicePrivateKey:key.pair.privateKey.export({type:"pkcs8",format:"pem"})});
      return { child, ready, result, exited };
    });
    await Promise.all(workers.map(w => w.ready));
    workers.forEach(w => w.child.send({go:true}));
    const results = await Promise.all(workers.map(w => w.result));
    await Promise.all(workers.map(w => w.exited));
    assert.equal(results.filter(Boolean).length, 1);
    assert.ok([a.hash,b.hash].includes(load().grants[0].binding.keyHash));
  } finally {
    children.forEach(child => { if(child.exitCode === null) child.kill(); });
  }
  console.log("tester_license_registry: first-bind/retry/restart/renewal/revoke/race/storage negatives PASS (synthetic keys)");
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
} })().catch(() => { console.error("tester_license_registry synthetic test FAILED"); process.exitCode=1; });
