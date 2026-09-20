"use strict";

const fs = require("fs");

const FILE_SECRET_PATTERN = /^[0-9a-f]{64}$/;

function configurationError(message) {
  const error = new Error(message);
  error.code = "invalid_turn_secret_configuration";
  return error;
}

function configuredValue(env, name) {
  const value = env[name];
  return typeof value === "string" && value.length > 0 ? value : null;
}

function resolveTurnSecret(env = process.env, fileSystem = fs) {
  const rawSecret = configuredValue(env, "TURN_SECRET");
  const secretFile = configuredValue(env, "TURN_SECRET_FILE");

  if (rawSecret && secretFile) {
    throw configurationError("TURN_SECRET and TURN_SECRET_FILE cannot be set together");
  }
  if (!secretFile) return rawSecret;

  let contents;
  try {
    contents = fileSystem.readFileSync(secretFile, "utf8");
  } catch {
    throw configurationError("TURN_SECRET_FILE is unreadable");
  }

  if (contents.length > 256) {
    throw configurationError("TURN_SECRET_FILE has an invalid size");
  }
  let secret = contents;
  if (secret.endsWith("\r\n")) {
    secret = secret.slice(0, -2);
  } else if (secret.endsWith("\n") || secret.endsWith("\r")) {
    secret = secret.slice(0, -1);
  }
  if (!FILE_SECRET_PATTERN.test(secret)) {
    throw configurationError("TURN_SECRET_FILE must contain exactly 64 lowercase hex characters");
  }
  return secret;
}

module.exports = { FILE_SECRET_PATTERN, resolveTurnSecret };
