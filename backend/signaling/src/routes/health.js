"use strict";

function setup(app) {
  app.get("/", (req, res) => {
    res.json({
      status: "ok",
      message: "SecureCall Signaling Server (Client IDs + Forwarding)"
    });
  });

  app.get("/health", (req, res) => {
    res.json({
      status: "ok",
      uptime: Math.round(process.uptime()),
      timestamp: new Date().toISOString()
    });
  });
}

module.exports = { setup };
