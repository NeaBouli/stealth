"use strict";

const registerHandlers = require("./register");
const callHandlers = require("./call");
const webrtcHandlers = require("./webrtc");
const phoneHandlers = require("./phone");
const subscriptionHandlers = require("./subscription");
const contactHandlers = require("./contact");

function buildHandlers(ctx) {
  return Object.assign(
    {},
    registerHandlers(ctx),
    callHandlers(ctx),
    webrtcHandlers(ctx),
    phoneHandlers(ctx),
    subscriptionHandlers(ctx),
    contactHandlers(ctx),
  );
}

module.exports = { buildHandlers };
