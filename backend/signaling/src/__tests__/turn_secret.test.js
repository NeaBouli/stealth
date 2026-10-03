"use strict";

const assert = require("assert");
const fs = require("fs");
const os = require("os");
const path = require("path");
const { resolveTurnSecret } = require("../security/turn_secret");

const RAW_KEY = ["TURN", "SECRET"].join("_");
const FILE_KEY = [RAW_KEY, "FILE"].join("_");
const syntheticSecret = "ab".repeat(32);

function expectConfigurationError(env, forbidden = syntheticSecret) {
  assert.throws(
    () => resolveTurnSecret(env),
    (error) => error?.code === "invalid_turn_secret_configuration"
      && !error.message.includes(forbidden),
  );
}

const directory = fs.mkdtempSync(path.join(os.tmpdir(), "securecall-turn-secret-"));

try {
  assert.strictEqual(resolveTurnSecret({}), null);

  const legacySecret = ["legacy", "compatible", "value"].join("-");
  assert.strictEqual(resolveTurnSecret({ [RAW_KEY]: legacySecret }), legacySecret);

  const validFile = path.join(directory, "valid");
  fs.writeFileSync(validFile, `${syntheticSecret}\n`, { mode: 0o600 });
  assert.strictEqual(resolveTurnSecret({ [FILE_KEY]: validFile }), syntheticSecret);

  const validCrLfFile = path.join(directory, "valid-crlf");
  fs.writeFileSync(validCrLfFile, `${syntheticSecret}\r\n`, { mode: 0o600 });
  assert.strictEqual(resolveTurnSecret({ [FILE_KEY]: validCrLfFile }), syntheticSecret);

  expectConfigurationError({
    [RAW_KEY]: legacySecret,
    [FILE_KEY]: validFile,
  }, legacySecret);
  expectConfigurationError({ [FILE_KEY]: path.join(directory, "missing") });
  expectConfigurationError({ [FILE_KEY]: directory });

  const invalidValues = [
    "",
    "a".repeat(63),
    "a".repeat(65),
    "A".repeat(64),
    `${"a".repeat(32)}g${"a".repeat(31)}`,
    `${"a".repeat(32)}\n${"a".repeat(32)}`,
    `${syntheticSecret}\n\n`,
    `${syntheticSecret} `,
  ];
  invalidValues.forEach((value, index) => {
    const file = path.join(directory, `invalid-${index}`);
    fs.writeFileSync(file, value, { mode: 0o600 });
    expectConfigurationError({ [FILE_KEY]: file }, value || syntheticSecret);
  });

  const oversizedFile = path.join(directory, "oversized");
  fs.writeFileSync(oversizedFile, "a".repeat(257), { mode: 0o600 });
  expectConfigurationError({ [FILE_KEY]: oversizedFile });

  console.log("turn_secret.test.js: PASS");
} finally {
  fs.rmSync(directory, { recursive: true, force: true });
}
