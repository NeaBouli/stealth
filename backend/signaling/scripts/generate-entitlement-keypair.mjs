#!/usr/bin/env node

import crypto from "node:crypto";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = fs.realpathSync(path.resolve(scriptDirectory, "../../.."));

function parseArguments(argv) {
  const options = { privateFile: "", publicFile: "", json: false };
  for (const argument of argv) {
    if (argument === "--json") options.json = true;
    else if (argument.startsWith("--private-file=")) options.privateFile = argument.slice("--private-file=".length);
    else if (argument.startsWith("--public-file=")) options.publicFile = argument.slice("--public-file=".length);
    else throw new Error(`Unknown argument: ${argument}`);
  }
  if (!options.privateFile || !options.publicFile) {
    throw new Error("--private-file and --public-file are required");
  }
  if (!path.isAbsolute(options.privateFile) || !path.isAbsolute(options.publicFile)) {
    throw new Error("Entitlement key paths must be absolute");
  }
  options.privateFile = path.resolve(options.privateFile);
  options.publicFile = path.resolve(options.publicFile);
  if (options.privateFile === options.publicFile) throw new Error("Private and public key paths must differ");
  return options;
}

function isWithin(parent, child) {
  const relative = path.relative(parent, child);
  return relative === "" || (!relative.startsWith(`..${path.sep}`) && relative !== "..");
}

function validateOutput(file, { privateParent }) {
  const parent = path.dirname(file);
  const parentStats = fs.lstatSync(parent);
  if (!parentStats.isDirectory() || parentStats.isSymbolicLink()) {
    throw new Error(`${privateParent ? "Private" : "Public"} key parent must be a non-symlink directory`);
  }
  const realParent = fs.realpathSync(parent);
  const realTarget = path.join(realParent, path.basename(file));
  if (isWithin(repositoryRoot, realTarget)) throw new Error("Entitlement keys must be written outside the repository");
  if (privateParent && (parentStats.mode & 0o077) !== 0) {
    throw new Error("Private key parent must not grant group/world access");
  }
  if (fs.existsSync(file)) throw new Error(`${privateParent ? "Private" : "Public"} key file already exists`);
  return realTarget;
}

function writeExclusive(file, value, mode) {
  let descriptor;
  let created = false;
  try {
    descriptor = fs.openSync(file, fs.constants.O_CREAT | fs.constants.O_EXCL | fs.constants.O_WRONLY, mode);
    created = true;
    fs.writeFileSync(descriptor, value, { encoding: "utf8" });
    fs.fsyncSync(descriptor);
    fs.chmodSync(file, mode);
    fs.closeSync(descriptor);
    descriptor = undefined;
  } catch (error) {
    if (descriptor !== undefined) {
      try {
        fs.closeSync(descriptor);
      } catch {
        // The output is removed below even when descriptor cleanup also fails.
      }
      descriptor = undefined;
    }
    if (created) fs.rmSync(file, { force: true });
    throw error;
  }
}

function generate(options) {
  const privateTarget = validateOutput(options.privateFile, { privateParent: true });
  const publicTarget = validateOutput(options.publicFile, { privateParent: false });
  const { privateKey, publicKey } = crypto.generateKeyPairSync("ed25519");
  const privatePem = privateKey.export({ type: "pkcs8", format: "pem" });
  const publicJwk = publicKey.export({ format: "jwk" });
  const publicBase64url = publicJwk.x;
  if (typeof publicBase64url !== "string" || !/^[A-Za-z0-9_-]{43}$/.test(publicBase64url)
    || Buffer.from(publicBase64url, "base64url").byteLength !== 32) {
    throw new Error("Generated entitlement public key is invalid");
  }

  const proof = crypto.randomBytes(32);
  const signature = crypto.sign(null, proof, privateKey);
  if (signature.length !== 64 || !crypto.verify(null, proof, publicKey, signature)) {
    throw new Error("Generated entitlement key pair failed its signature proof");
  }

  let privateCreated = false;
  let publicCreated = false;
  try {
    writeExclusive(privateTarget, privatePem, 0o600);
    privateCreated = true;
    writeExclusive(publicTarget, `${publicBase64url}\n`, 0o644);
    publicCreated = true;
  } catch (error) {
    if (publicCreated) fs.rmSync(publicTarget, { force: true });
    if (privateCreated) fs.rmSync(privateTarget, { force: true });
    throw error;
  }

  const fingerprint = crypto.createHash("sha256")
    .update(Buffer.from(publicBase64url, "base64url"))
    .digest("hex");
  return {
    ok: true,
    privateFile: privateTarget,
    publicFile: publicTarget,
    publicKeyFingerprint: fingerprint,
    publicKeyFormat: "ed25519_raw_base64url",
  };
}

try {
  const options = parseArguments(process.argv.slice(2));
  const result = generate(options);
  if (options.json) process.stdout.write(`${JSON.stringify(result)}\n`);
  else {
    process.stdout.write("Entitlement key pair generated\n");
    process.stdout.write(`Private file: ${result.privateFile}\n`);
    process.stdout.write(`Public file: ${result.publicFile}\n`);
    process.stdout.write(`Public fingerprint: ${result.publicKeyFingerprint}\n`);
  }
} catch (error) {
  process.stderr.write(`Entitlement key generation failed: ${error instanceof Error ? error.message : "unknown failure"}\n`);
  process.exitCode = 1;
}
