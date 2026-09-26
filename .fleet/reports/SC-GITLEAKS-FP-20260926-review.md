verdict: ok
reviewer: Codex

The allowlist is anchored to one non-secret environment-read statement and does not suppress
literal assignments, fallback literals, appended same-line secrets or generic API-key findings.
The regression script generates synthetic values at runtime and verifies both clean and leak cases.
No historical credential fingerprint, path or commit is ignored. Rotation Gate S0 remains open.
