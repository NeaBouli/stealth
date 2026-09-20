"use strict";

const assert = require("assert/strict");
const crypto = require("crypto");
const fs = require("fs");
const os = require("os");
const path = require("path");
const handlers = require("../ws/handlers/tester_license");
const { createTesterLicenseRegistry } = require("../services/tester_license_registry");
const { verifyTesterEntitlement } = require("../payments/tester_entitlement_tokens");
const { loadTesterLicenseRuntime } = require("../services/tester_license_runtime");
const dir = fs.mkdtempSync(path.join(fs.realpathSync(os.tmpdir()), "tester-transport-synthetic-"));
fs.chmodSync(dir, 0o700);
try {
  const file = path.join(dir, "registry.json");
  const signer = crypto.generateKeyPairSync("ed25519");
  const device = crypto.generateKeyPairSync("ec", { namedCurve: "prime256v1" });
  const hash = value => crypto.createHash("sha256").update(value).digest("hex");
  const publicKeyDer = device.publicKey.export({type:"spki",format:"der"});
  const publicKey = publicKeyDer.toString("base64url");
  const keyHash = hash(publicKeyDer);
  const code = `SC-PREM-${"D".repeat(48)}`;
  const state = {schema:1, grants:[{id:"a".repeat(64),codeHash:hash(code),status:"active",binding:null}],
    enrolledKeys:[]};
  fs.writeFileSync(file, JSON.stringify(state), {mode:0o600});
  assert.equal(loadTesterLicenseRuntime({}),null);
  assert.equal(loadTesterLicenseRuntime({SECURECALL_TESTER_LICENSE_ENABLED:"true"}),null);
  const signerFile = path.join(dir,"synthetic-signer.pem");
  fs.writeFileSync(signerFile,signer.privateKey.export({type:"pkcs8",format:"pem"}),{mode:0o600});
  const env = {SECURECALL_TESTER_LICENSE_ENABLED:"true",SECURECALL_TESTER_SIGNER_FILE:signerFile,SECURECALL_TESTER_REGISTRY_FILE:file};
  assert.ok(loadTesterLicenseRuntime(env));
  fs.chmodSync(signerFile,0o644);
  assert.equal(loadTesterLicenseRuntime(env),null);
  fs.chmodSync(signerFile,0o600);
  const registry = createTesterLicenseRegistry({file,privateKey:signer.privateKey.export({type:"pkcs8",format:"pem"})});
  const legacySubject = "synthetic-session-A";
  const canonicalSubject = "synthetic-session-v2";
  let subject = legacySubject;
  const identityRegistry = {
    aliasesFor: identityId => identityId === canonicalSubject ? [legacySubject] : [],
  };
  const active = handlers({getClientId:()=>subject,testerLicenseRegistry:registry,identityRegistry});
  const invoke = (map, type, fields={}) => {
    const requestId = crypto.randomUUID();
    const replies = [];
    map[type]({send:value=>replies.push(JSON.parse(value))}, "synthetic-connection", {requestId,...fields});
    assert.equal(replies.length,1);
    assert.equal(replies[0].requestId,requestId);
    return replies[0];
  };
  const start = () => invoke(active,"TESTER_ACTIVATION_BEGIN",{code,publicKey,
    packageName:"com.securecall.app.premium",subject:"forged",keyHash:"0".repeat(64)});
  const prove = challenge => ({challengeId:challenge.challengeId,
    signature:crypto.sign("sha256",Buffer.from(challenge.challenge),device.privateKey).toString("base64url")});
  const challenge = start(); assert.equal(challenge.success,true);
  assert.equal(challenge.type,"TESTER_ACTIVATION_CHALLENGE");
  const activation = invoke(active,"TESTER_ACTIVATION_COMPLETE",prove(challenge));
  assert.equal(activation.success,true);
  const verification = {subject,deviceKeyHash:keyHash,publicKey:signer.publicKey.export({type:"spki",format:"pem"})};
  assert.equal(verifyTesterEntitlement(activation.entitlementToken, verification).sub,subject);
  assert.equal(invoke(active,"TESTER_ACTIVATION_COMPLETE",prove(challenge)).success,false);
  const renewal = invoke(active,"TESTER_RENEWAL_BEGIN",{entitlementToken:activation.entitlementToken,keyHash});
  const renewed = invoke(active,"TESTER_RENEWAL_COMPLETE",prove(renewal));
  assert.equal(renewed.success,true);
  verifyTesterEntitlement(renewed.entitlementToken,verification);
  subject=canonicalSubject;
  const migration = invoke(active,"TESTER_RENEWAL_BEGIN",{entitlementToken:renewed.entitlementToken,keyHash});
  const migrated = invoke(active,"TESTER_RENEWAL_COMPLETE",prove(migration));
  assert.equal(migrated.success,true);
  verifyTesterEntitlement(migrated.entitlementToken,{...verification,subject:canonicalSubject});
  assert.equal(JSON.parse(fs.readFileSync(file,"utf8")).grants[0].binding.subject,canonicalSubject);
  const foreign = crypto.generateKeyPairSync("ec", {namedCurve:"prime256v1"});
  const wrongProof = challenge => ({challengeId:challenge.challengeId,
    signature:crypto.sign("sha256",Buffer.from(challenge.challenge),foreign.privateKey).toString("base64url")});
  const retry = start();
  assert.equal(invoke(active,"TESTER_ACTIVATION_COMPLETE",wrongProof(retry)).success,false);
  const tokenParts = activation.entitlementToken.split(".");
  const replacement = tokenParts[1].endsWith("A") ? "B" : "A";
  const manipulated = `${tokenParts[0]}.${tokenParts[1].slice(0,-1)}${replacement}.${tokenParts[2]}`;
  assert.equal(invoke(active,"TESTER_RENEWAL_BEGIN",{entitlementToken:manipulated,keyHash}).success,false);
  const other = start(); subject="synthetic-session-B";
  assert.equal(invoke(active,"TESTER_ACTIVATION_COMPLETE",prove(other)).success,false);
  subject=null;
  assert.equal(start().error,"invalid_tester_request");
  const disabled = handlers({getClientId:()=>"synthetic"});
  assert.equal(invoke(disabled,"TESTER_ACTIVATION_BEGIN").error,"tester_license_unavailable");
  const broken = handlers({getClientId:()=>"synthetic",testerLicenseRegistry:{begin:()=>{throw new Error("synthetic-private-storage-detail");}}});
  assert.equal(JSON.stringify(invoke(broken,"TESTER_ACTIVATION_BEGIN")).includes("synthetic-private-storage-detail"),false);
  console.log("tester_license_transport: activation/renewal/session binding/replay/disabled/error-redaction PASS (synthetic only)");
} finally { fs.rmSync(dir,{recursive:true,force:true}); }
