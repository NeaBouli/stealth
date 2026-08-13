# Contributing to SecureCall / StealthX

Thank you for your interest in SecureCall. We value transparency and community feedback.

## What We Accept

We welcome the following contributions via [GitHub Issues](https://github.com/NeaBouli/stealth/issues):

- **Bug Reports** -- If you find a bug in the app, please report it using our [Bug Report template](https://github.com/NeaBouli/stealth/issues/new?template=bug_report.yml).
- **Feature Requests** -- Have an idea for a new feature? Submit it using our [Feature Request template](https://github.com/NeaBouli/stealth/issues/new?template=feature_request.yml).
- **Security Vulnerabilities** -- If you discover a security issue, please report it via **[GitHub Issues](https://github.com/NeaBouli/stealth/issues)**. See [SECURITY.md](SECURITY.md) for our full disclosure policy.

## What We Do Not Accept

**We do not accept code contributions (Pull Requests).**

This repository publishes source code for transparency and independent security auditing. Official SecureCall branding, backend services, store releases, paid Pro/Premium offerings, and all use rights remain controlled by Vendetta Labs.

- Pull Requests will be **closed without review**.
- Forks, builds, derivative works, redistribution, rebranding, hosting, and use of the SecureCall/StealthX name, logo, official backend services, store listings, or paid license infrastructure require prior written permission.
- Patches, code suggestions, or implementation changes submitted via issues or other channels will not be incorporated.

## Why No Code Contributions?

SecureCall is a security-critical application. Every line of code that handles encryption, key management, or transport must go through our internal security review process. Accepting external code contributions would compromise our ability to guarantee the integrity and security of the platform.

We believe this approach -- full source transparency without external code contributions -- provides the best balance between openness and security assurance.

## Code of Conduct

All interactions in this repository are governed by our [Code of Conduct](CODE_OF_CONDUCT.md). Please be respectful and constructive.

## Contact

- **Security issues:** [Open an issue](https://github.com/NeaBouli/stealth/issues)
- **General inquiries:** [Open an issue](https://github.com/NeaBouli/stealth/issues)
- **Legal / Licensing:** [Open an issue](https://github.com/NeaBouli/stealth/issues)

## Gradle dependency verification

Android dependencies are checksum-locked in
`client_android/gradle/verification-metadata.xml`. When a reviewed dependency update changes
the graph, rerun the same affected Gradle CI tasks with
`--write-verification-metadata sha256`, inspect the metadata diff for only the expected
component/version changes, and then rerun the tasks without the write flag. Do not accept
unrelated checksum churn.
