# StealthX Backup And Restore

Last updated: 2026-06-11

## Scope

The v1 backend persists operational state as JSON files on Hetzner:

- `/opt/stealthx/signaling/data/activation_codes.json`
- `/opt/stealthx/signaling/data/sold_codes.json`
- `/opt/stealthx/signaling/data/wallets.json`
- `/opt/stealthx/signaling/data/subscriptions.json`
- `/opt/stealthx/signaling/data/custom_ids.json`
- plus FCM, pending activation, gift-code, Stripe event, and license JSON files as present.

For v1 this is acceptable for low traffic, provided backups are active. PostgreSQL remains the recommended v1.1 migration path.

## Daily Backup

Hetzner runs:

```sh
/opt/stealthx/scripts/backup-signaling-data.sh
```

Cron:

```cron
17 3 * * * /opt/stealthx/scripts/backup-signaling-data.sh
```

Backup destination:

```text
/opt/stealthx/backups/signaling-data/
```

Log file:

```text
/var/log/stealthx/backup-signaling-data.log
```

Retention:

```text
30 days
```

Each archive is a gzip-compressed tarball named:

```text
signaling-data-YYYYMMDDTHHMMSSZ.tar.gz
```

## Manual Backup

```sh
ssh hetzner '/opt/stealthx/scripts/backup-signaling-data.sh'
```

Verify:

```sh
ssh hetzner 'ls -lh /opt/stealthx/backups/signaling-data | tail'
ssh hetzner 'tail -20 /var/log/stealthx/backup-signaling-data.log'
```

## Restore Procedure

1. Stop the backend:

```sh
ssh hetzner 'pm2 stop signaling'
```

2. Preserve the current data directory:

```sh
ssh hetzner 'ts=$(date -u +%Y%m%dT%H%M%SZ); cp -a /opt/stealthx/signaling/data /opt/stealthx/signaling/data.before-restore.$ts'
```

3. Restore the selected archive:

```sh
ssh hetzner 'rm -rf /opt/stealthx/signaling/data/* && tar -C /opt/stealthx/signaling/data -xzf /opt/stealthx/backups/signaling-data/signaling-data-YYYYMMDDTHHMMSSZ.tar.gz'
```

4. Restart the backend:

```sh
ssh hetzner 'pm2 start signaling && pm2 status && curl -s http://127.0.0.1:8080/health'
```

5. Verify public API:

```sh
curl -fsS https://api.stealthx.tech/health
curl -fsS https://api.stealthx.tech/licenses/status
```

## Notes

- The backup script uses `flock` to prevent overlapping runs.
- Archives are written as `.tmp` first and atomically renamed after `tar` succeeds.
- Archives are mode `600`.
- Keep the backup host and production host access-controlled; these files may contain activation and purchase state.
