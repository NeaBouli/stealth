// PM2 ecosystem config — run: pm2 start ecosystem.config.js
// To apply on server: git pull && pm2 reload ecosystem.config.js --update-env
"use strict";

const fs = require("fs");
const path = require("path");

function loadEnvFile(file) {
  try {
    const env = {};
    const raw = fs.readFileSync(file, "utf8");
    for (const line of raw.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) continue;
      const idx = trimmed.indexOf("=");
      if (idx <= 0) continue;
      const key = trimmed.slice(0, idx).trim();
      const value = trimmed.slice(idx + 1).trim().replace(/^['"]|['"]$/g, "");
      env[key] = value;
    }
    return env;
  } catch {
    return {};
  }
}

const productionEnv = loadEnvFile(path.join("/opt", "stealthx", ".env.production"));

module.exports = {
  apps: [
    {
      name: "signaling",
      script: "src/server.js",
      cwd: "/opt/stealthx/signaling",

      // Restart policy
      restart_delay: 3000,
      max_restarts: 20,
      min_uptime: "10s",

      // Memory guard
      max_memory_restart: "512M",

      // No file watching in production
      watch: false,

      // Keep logs structured
      error_file: "/var/log/stealthx/signaling-error.log",
      out_file: "/var/log/stealthx/signaling-out.log",
      log_date_format: "YYYY-MM-DD HH:mm:ss Z",
      merge_logs: true,

      env: {
        ...productionEnv,
        NODE_ENV: "production",
        TRUST_PROXY: "true",
      },
    },
  ],
};
