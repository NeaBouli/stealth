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

### Recovering a stale fulfillment lock

The VLABS order and sold-code stores fail closed when their adjacent `.lock`
file already exists. Never delete either lock while a writer may still be
running.

1. Stop all signaling instances that can write the affected store and confirm
   that no replacement instance or deployment is starting.
2. Take a filesystem snapshot or copy of the JSON store and its lock file.
3. Read the lock owner (`pid:owner-token`) and confirm that the PID is absent on
   the host where the store is mounted. On shared storage, repeat this check on
   every writer host.
4. Remove only the verified stale lock (`vlabs_fulfillment_orders.json.lock` or
   `sold_codes.json.lock`). Do not edit or replace the JSON store.
5. Start one signaling instance, verify that it can read the store, and run a
   signed non-production duplicate/retry check before restoring normal traffic.

If ownership or writer state cannot be proven, leave the lock in place and
restore from a verified snapshot instead of forcing recovery.
- Archives are written as `.tmp` first and atomically renamed after `tar` succeeds.
- Archives are mode `600`.
- Keep the backup host and production host access-controlled; these files may contain activation and purchase state.
