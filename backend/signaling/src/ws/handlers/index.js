"use strict";

const registerHandlers = require("./register");
const callHandlers = require("./call");
const webrtcHandlers = require("./webrtc");
const phoneHandlers = require("./phone");
const subscriptionHandlers = require("./subscription");
const contactHandlers = require("./contact");
const testerLicenseHandlers = require("./tester_license");

function buildHandlers(ctx) {
  return Object.assign(
    {},
    registerHandlers(ctx),
    callHandlers(ctx),
    webrtcHandlers(ctx),
    phoneHandlers(ctx),
    subscriptionHandlers(ctx),
    contactHandlers(ctx),
    testerLicenseHandlers(ctx),
  );
}

module.exports = { buildHandlers };
