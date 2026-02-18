# SecureCall Beta Testing Plan

## Overview

Structured beta testing program before production release on Google Play Store.

## Timeline

| Phase | Duration | Testers | Track |
|-------|----------|---------|-------|
| Internal Testing | 1 week | Developer only | Internal |
| Closed Beta | 2 weeks | 10-20 invited testers | Closed Testing |
| Open Beta | 1 week (optional) | Public | Open Testing |
| Production | — | Everyone | Production |

**Total estimated time: 4-5 weeks**

## Phase 1: Internal Testing (Week 1)

### Scope
Test all 3 flavors (FREE, PRO, PREMIUM) on real devices.

### Test Matrix

| Test Case | FREE | PRO | PREMIUM |
|-----------|------|-----|---------|
| App installs and launches | | | |
| Registration / onboarding | | | |
| Make encrypted call | | | |
| Receive encrypted call | | | |
| Audio quality acceptable | | | |
| Call over WiFi | | | |
| Call over cellular (4G/5G) | | | |
| Contacts screen loads | | | |
| Call history displays | | | |
| Settings screen works | | | |
| In-app purchase flow (sandbox) | | | |
| Crashlytics reports (FREE only) | | | |
| Debug features hidden | | | |
| Dark mode works | | | |
| App survives backgrounding | | | |
| App handles incoming call notification | | | |
| Battery usage reasonable | | | |
| No ANR (Application Not Responding) | | | |

### Devices to Test

- Low-end: Android 6.0 (API 23), 2GB RAM
- Mid-range: Android 10-12, 4GB RAM
- High-end: Android 13-14, 8GB+ RAM
- Various screen sizes (5", 6", 6.7")

### Exit Criteria
- All critical test cases pass
- No crashes during normal usage
- Voice quality is acceptable on all connection types

## Phase 2: Closed Beta (Weeks 2-3)

### Tester Recruitment

Target: 10-20 testers from:
- Privacy/security enthusiasts
- Android developer communities
- Friends and family
- r/privacy, r/Android communities

### Tester Requirements
- Android 6.0+ device
- Willing to provide feedback
- Available for 2 weeks
- Understands this is beta software

### Feedback Collection

Provide testers with a feedback form covering:

1. **Device info** — Model, Android version, carrier
2. **Installation** — Any issues installing?
3. **Onboarding** — Was the setup clear?
4. **Call quality** — Rate 1-5 (clarity, latency, drops)
5. **UI/UX** — Intuitive? Confusing elements?
6. **Performance** — Lag, battery drain, heating?
7. **Crashes** — When and how often?
8. **Features** — What's missing? What's unnecessary?
9. **Security perception** — Do you feel your calls are secure?
10. **Overall** — Would you recommend this app?

### Feedback Channels
- Google Form (primary)
- Email: beta@stealthx.app
- GitHub Issues (for bugs)

### Monitoring
- Check Play Console crash reports daily
- Review ANR reports
- Monitor Crashlytics (FREE tier)
- Track feedback form responses

### Exit Criteria
- Crash-free rate > 99%
- ANR rate < 0.47%
- No critical bugs unresolved
- Average call quality rating > 3.5/5
- All P0/P1 bugs fixed

## Phase 3: Open Beta (Week 4, Optional)

### Decision Criteria
Proceed to open beta only if:
- Closed beta met all exit criteria
- No architectural issues discovered
- Server infrastructure handles 20 concurrent users

### Monitoring
- Expanded crash monitoring
- Performance metrics at scale
- Server load monitoring
- User review sentiment

### Exit Criteria
- Crash-free rate > 99.5%
- No new critical bugs
- Positive feedback trend
- Server stable under load

## Phase 4: Production Release

### Go / No-Go Checklist

- [ ] All beta exit criteria met
- [ ] Privacy Policy live and accessible
- [ ] Store listing finalized (all languages)
- [ ] Screenshots current and accurate
- [ ] Support email monitored (support@stealthx.app)
- [ ] Hotfix process documented and tested
- [ ] Server capacity sufficient for launch
- [ ] Staged rollout plan ready (5% → 100%)

### Rollout Strategy

| Day | Rollout % | Action |
|-----|-----------|--------|
| Day 1 | 5% | Monitor closely, check for crashes |
| Day 3 | 10% | Review metrics, fix any issues |
| Day 5 | 25% | Monitor server load |
| Day 7 | 50% | Check user reviews |
| Day 10 | 100% | Full release |

Halt rollout if:
- Crash rate exceeds 2%
- Critical bug reported by multiple users
- Server instability

## Bug Severity Classification

| Severity | Description | Response Time |
|----------|-------------|--------------|
| P0 - Critical | App crash, data loss, security vulnerability | Fix immediately, hotfix release |
| P1 - High | Major feature broken, call drops frequently | Fix within 24 hours |
| P2 - Medium | Minor feature broken, UI glitch | Fix in next release |
| P3 - Low | Cosmetic issue, minor improvement | Backlog |

## Post-Beta Actions

1. Compile all feedback into actionable items
2. Prioritize fixes for v0.3
3. Thank beta testers (in-app acknowledgment or Pro upgrade)
4. Write post-mortem of beta findings
5. Update documentation with lessons learned
