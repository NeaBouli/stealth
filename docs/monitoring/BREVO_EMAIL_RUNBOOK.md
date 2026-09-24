# Brevo Email Runbook

SecureCall sends transactional email through the Brevo HTTP API. The active credential is provided
as `BREVO_API_KEY`; the scheduled `Brevo API Key Keepalive` workflow validates that API path twice
per month.

The Brevo SMTP credentials named `securecall-production` and `Master Password` are legacy/unused.
They had no recorded use when reviewed on 2026-09-24 and may be deactivated by Brevo's inactivity
policy. Their inactive state does not break the current SecureCall email path.

When activation-code delivery fails:

1. Check the latest `Brevo API Key Keepalive` workflow result.
2. Check that the runtime has `BREVO_API_KEY` configured, without printing its value.
3. Check the Brevo API credential status and recent-use date.
4. Check the transactional email response and application logs with recipient data redacted.
5. Do not debug, reactivate or rotate the legacy SMTP credentials unless a deployed SMTP transport
   is first identified and documented.

Never record credential values in Git, logs, tickets, screenshots or Bridge files. Rotation,
provider changes and production test email require a separately authorized operational change.
