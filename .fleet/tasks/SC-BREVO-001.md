# SC-BREVO-001 — Read-only SMTP ownership inventory

Worker: Claude Code
Mode: read-only analysis; report only

Use `architecture-map` and identify the email module and runtime hop. Inspect only repository
paths relevant to signaling email, deployment manifests, CI, website forms and operator docs.

Report:
- every active or dead Brevo/Sendinblue/SMTP call path;
- exact environment-variable names and consuming modules, never values;
- which service likely owns `securecall-production` and whether `Master Password` is referenced;
- tests or static checks that prove the conclusion;
- safe next action for each credential and any uncertainty requiring provider/runtime metadata.

Do not modify application files, contact providers, access secrets, send email, deploy, or infer
usage from names alone. Write only `.fleet/reports/SC-BREVO-001.md` on your task branch.
