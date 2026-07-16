"use strict";

const assert = require("assert");
const { assertSingleWriterRuntime } = require("../services/single_writer_guard");

assert.doesNotThrow(() => assertSingleWriterRuntime({
  SIGNALING_REPLICA_COUNT: "1",
}));
assert.throws(
  () => assertSingleWriterRuntime({}),
  /SIGNALING_REPLICA_COUNT=1/,
);
assert.throws(
  () => assertSingleWriterRuntime({ SIGNALING_REPLICA_COUNT: "2" }),
  /SIGNALING_REPLICA_COUNT=1/,
);

console.log("single_writer_guard.test.js ok");
