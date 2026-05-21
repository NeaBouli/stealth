// PM2 ecosystem config — run: pm2 start ecosystem.config.js
// To apply on server: git pull && pm2 reload ecosystem.config.js --update-env
"use strict";

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
        NODE_ENV: "production",
      },
    },
  ],
};
