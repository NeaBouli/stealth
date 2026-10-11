# Security Policy

## Reporting a Vulnerability

If you discover a security vulnerability in SecureCall or the StealthX platform,
**please do not open a public GitHub issue.**

Instead, report it privately through GitHub's private vulnerability reporting:

**[Report a vulnerability](https://github.com/NeaBouli/stealth/security/advisories/new)**
(Security tab → "Report a vulnerability"). Only the maintainers can see the
report and the discussion until an advisory is published.

Please include:

- A description of the vulnerability
- Steps to reproduce the issue
- The potential impact or severity
- Any suggested remediation (optional)

## Response Timeline

| Action | Timeframe |
|--------|-----------|
| Acknowledgement of report | Within 48 hours |
| Initial assessment | Within 5 business days |
| Status update to reporter | Within 10 business days |
| Fix deployed (critical) | As soon as possible |
| Fix deployed (high) | Within 30 days |

## Supported Versions

| Version | Supported |
|---------|-----------|
| Latest release | Yes |
| Previous release | Security fixes only |
| Older versions | No |

## Security Audit

This project has undergone a comprehensive security audit. The full report is
publicly available at [docs/SECURITY_AUDIT_REPORT.md](docs/SECURITY_AUDIT_REPORT.md).

## Acknowledgements

We gratefully acknowledge security researchers who responsibly disclose
vulnerabilities. With your permission, we will credit you in our security
advisories and changelog.

## Scope

This policy applies to:

- The SecureCall Android application
- The StealthX core crypto engine (Rust)
- The signaling and relay backend infrastructure
- All code published in this repository
