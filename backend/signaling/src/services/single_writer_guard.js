"use strict";

function assertSingleWriterRuntime(env = process.env) {
  if (env.SIGNALING_REPLICA_COUNT !== "1") {
    throw new Error("Server start requires SIGNALING_REPLICA_COUNT=1 for the file-backed activation store");
  }
}

module.exports = { assertSingleWriterRuntime };
