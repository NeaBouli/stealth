id: SC-SEC-RUNTIME-ROTATION-MAP-20260926
status: partial
worker: claude
branch: agent/claude/SC-SEC-RUNTIME-ROTATION-MAP-20260926
summary: Read-only prod map (ssh fleet@hetzner, uid fleet, groups adm+systemd-journal; no sudo/docker). No MAP.md; lane:
 client -> Traefik (/srv/traefik/dynamic/stealthx.yml) -> api.stealthx.tech -> host.docker.internal:8080 -> PM2 app.
 ADMIN_API_KEY + TURN_SECRET consumer 1: signaling = PM2 God daemon (root, /root/.pm2) running node
   /opt/stealthx/signaling/src/server.js, up since 2026-08-01 07:38 UTC; env injected by ecosystem.config.js
   reading /opt/stealthx/.env.production (root 600) at start/reload only. Uses: requireAdmin x-admin-key
   (/admin/*, /routing/list, /ice-servers, /api/subscription/:id); HMAC-SHA1 TURN REST creds (getIceServers).
 TURN_SECRET consumer 2: container stealthx-coturn (coturn/coturn:latest, network_mode host, turnserver as nobody,
   up since 2026-09-15 03:00 via cert cron) reading /opt/stealthx/coturn/turnserver.conf: static-auth-secret is a
   LITERAL (64 chars, no env ref) -> env change alone does NOT rotate coturn. Repo deploy/ + deploy_signaling.sh stale.
 Health: GET api.stealthx.tech/health -> 200 ok; unauth /routing/list -> 401; TURN 3478+5349 tcp/udp listening.
 Rotation/reload (root): backup .env.production + turnserver.conf -> set new TURN_SECRET in both files, new
   ADMIN_API_KEY in .env.production -> `docker restart stealthx-coturn` -> `cd /opt/stealthx/signaling && pm2 reload
   ecosystem.config.js --update-env` back-to-back (not plain pm2 restart) -> health/401/TURN smoke; brief TURN
   auth mismatch until clients refetch ICE.
 Rollback: reverse order to backed-up NEW pair; never restore .env.production.bak.20260612*,
   backups/env-production-pre-20260801*, signaling-rollback-20260801* (hold exposed values; purge them).
files: .fleet/reports/SC-SEC-RUNTIME-ROTATION-MAP-20260926.md (only file; no server/provider/GitHub writes)
tests: No code change -> no suite. Verified via ssh read-only: ps lstart/args (no env), ss -tuln, ls -la perms,
 awk shape check on turnserver.conf (length/env-ref only, value never printed), grep counts on logs, curl GET x2.
 Logs /var/log/stealthx/signaling-out.log (143911 lines, 2026-05-21 -> now, no rotation): admin-effect markers
 [BROADCAST]/[GIFT] Created/"Reset to 0 by admin"/simulate = 0; 113 "unauthorized" = REGISTER sig rejects.
risks: Misuse cannot be excluded: read-only admin routes (/routing/list, /ice-servers, /admin/gifts,
 /api/subscription) log nothing; Traefik has no accessLog (log level WARN only); coturn logs to docker stdout
 (root-only); gap 2026-05-16..05-21 (docker-era signaling logs not readable). TURN relay abuse via exposed secret
 is unverifiable (2 UDP sockets >=49152 now; only total-quota=100, no user-quota/max-bps).
 Watchdog broken since 2026-06-09 (watchdog.log: "Permission denied" x157799, no auto-recovery).
security: F1 /opt/stealthx/coturn/turnserver.conf is 644 root on multi-tenant host (github-runner, vlc-tunnel, vlabs
 users) and holds the TURN secret literally -> any local user can read it; after rotation set root:65534 0640.
 F2 exposed pair still live-shaped: signaling proc started 2026-08-01, .env.production mtime 2026-08-01, conf mtime
 2026-05-16 (= exposure date) -> TURN_SECRET almost certainly the exposed value; ADMIN_API_KEY unconfirmed
 (value comparison forbidden). Treat both as compromised.
next: Codex (current perms = fleet user): only read-only verify - curl health/401, ps lstart of PM2 node + turnserver
 to confirm restarts, tail/grep /var/log/stealthx, ss; repo follow-ups (gitleaks allowlist/.gitleaksignore after
 confirmation, mark repo deploy/ + deploy_signaling.sh stale). Cannot edit env/conf, restart PM2/docker, or read root-only logs (PM2, docker).
 Gio/operator (root): generate values, edit both files, chmod conf, restart/reload, purge old backups, enable
 Traefik accessLog, fix watchdog cron/perms, confirm in BRIDGE (no values).
