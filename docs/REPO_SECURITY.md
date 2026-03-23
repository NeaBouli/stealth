# Repository Security Setup

## Branch Protection Rules

**URL:** https://github.com/NeaBouli/stealth/settings/branches

1. Click "Add branch protection rule"
2. Branch name pattern: `main`
3. Enable:
   - [x] Require a pull request before merging
   - [x] Require approvals: 1
   - [x] Dismiss stale pull request approvals when new commits are pushed
   - [x] Include administrators
   - [x] Restrict who can push to matching branches

## Code Security

**URL:** https://github.com/NeaBouli/stealth/settings/security_analysis

Enable:
- [x] Dependency graph
- [x] Dependabot alerts
- [x] Dependabot security updates
- [x] Secret scanning
- [x] Push protection (blocks accidental secret commits)

## Repository Settings

**URL:** https://github.com/NeaBouli/stealth/settings

- Visibility: Public (source available, not open source)
- Wikis: Disabled (use website wiki instead)
- Issues: Enabled
- Sponsorships: Disabled
- Projects: Disabled
- Discussions: Disabled

## Collaborator Settings

- You (NeaBouli) as sole admin
- No direct push to main after protection is enabled
- All changes via Pull Request
