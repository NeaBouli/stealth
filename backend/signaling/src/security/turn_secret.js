"use strict";

const fs = require("fs");

const FILE_SECRET_PATTERN = /^[0-9a-f]{64}$/;

// Reuse the format checks; new signing authorities may require an owner-only file.
const TURN_SECRET_NAMES = Object.freeze({
  value: "TURN_SECRET",
  file: "TURN_SECRET_FILE",
  code: "invalid_turn_secret_configuration",
});

function configurationError(message, code) {
  const error = new Error(message);
  error.code = code;
  return error;
}

function configuredValue(env, name) {
  const value = env[name];
  return typeof value === "string" && value.length > 0 ? value : null;
}

function resolveTurnSecret(env = process.env, fileSystem = fs, names = TURN_SECRET_NAMES) {
  const fail = (message) => configurationError(message, names.code);
  const rawSecret = configuredValue(env, names.value);
  const secretFile = configuredValue(env, names.file);

  if (rawSecret && secretFile) {
    throw fail(`${names.value} and ${names.file} cannot be set together`);
  }
  if (!secretFile) return rawSecret;

  let contents;
  try {
    if (names.protectedFile) {
      const { O_RDONLY, O_NOFOLLOW, O_NONBLOCK } = fs.constants;
      const fd = fileSystem.openSync(secretFile, O_RDONLY | O_NOFOLLOW | O_NONBLOCK);
      try {
        const info = fileSystem.fstatSync(fd);
        if (!info.isFile() || (info.mode & 0o7777) !== 0o600 || info.uid !== process.getuid()) {
          throw fail(`${names.file} is not a protected file`);
        }
        const buffer = Buffer.alloc(257);
        contents = buffer.subarray(0, fileSystem.readSync(fd, buffer, 0, buffer.length, 0)).toString("utf8");
      } finally {
        fileSystem.closeSync(fd);
      }
    } else {
      contents = fileSystem.readFileSync(secretFile, "utf8");
    }
  } catch {
    throw fail(`${names.file} is unreadable`);
  }

  if (contents.length > 256) {
    throw fail(`${names.file} has an invalid size`);
  }
  let secret = contents;
  if (secret.endsWith("\r\n")) {
    secret = secret.slice(0, -2);
  } else if (secret.endsWith("\n") || secret.endsWith("\r")) {
    secret = secret.slice(0, -1);
  }
  if (!FILE_SECRET_PATTERN.test(secret)) {
    throw fail(`${names.file} must contain exactly 64 lowercase hex characters`);
  }
  return secret;
}

module.exports = { FILE_SECRET_PATTERN, TURN_SECRET_NAMES, resolveTurnSecret };
