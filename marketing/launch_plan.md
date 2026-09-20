# SecureCall Launch Plan

## Pre-Launch (2-4 weeks before)

### Landing Page — neabouli.github.io/stealth

**Pages needed:**
- `/` — Hero section, features, download CTA
- `/privacy` — Privacy policy (required for Play Store)
- `/security` — Technical security details
- `/pricing` — Free vs Pro vs Premium comparison
- `/beta` — Beta signup form

**Tech stack suggestion:** Static site (Hugo/Next.js) on Vercel or GitHub Pages

**Key messaging:**
- "Your voice belongs to you"
- "End-to-end encrypted voice calls"
- "Call content is not stored. Source and audit status available."
- "Built in Greece with data minimization in mind."

### Social Media Accounts

Create accounts on:
- [x] GitHub (repository)
- [ ] Twitter/X (@SecureCallApp or @StealthXApp)
- [ ] LinkedIn (StealthX company page)
- [ ] Mastodon (for privacy community)

## Launch Day

### Social Media Posts

**Twitter/X:**
```
Introducing SecureCall — end-to-end encrypted voice calls with transparent metadata handling.

Built with XChaCha20-Poly1305 encryption, a Rust crypto engine, and source-available code.

Your voice belongs to you.

🔗 Play Store: [link]
📖 Source: [github link]

#privacy #encryption #android #opensource
```

**LinkedIn:**
```
We're launching SecureCall — an end-to-end encrypted voice calling app for Android.

In a world of mass surveillance, we believe private communication is a fundamental right.

Key features:
• XChaCha20-Poly1305 application-frame encryption
• No call-content storage and documented metadata handling
• Source-available code for full transparency
• Built with a memory-safe Rust crypto engine

The Free edition is distributed through Google Play. Direct paid editions remain release-gated until the matching product and finance approvals are complete.

#CyberSecurity #Privacy #Encryption #Android #Startup
```

### Reddit Posts

**Target subreddits:**

| Subreddit | Approach |
|-----------|----------|
| r/privacy | Focus on data minimization, documented metadata handling, source availability |
| r/Android | Focus on app features, Material Design 3 UI, quality |
| r/privacytoolsIO | Technical details, comparison with alternatives |
| r/netsec | Security architecture, crypto details, audit results |
| r/selfhosted | Source-available, self-hostable signaling server |
| r/de | German-language privacy and Android angle |

**Reddit post template:**
```
Title: [Show & Tell] SecureCall — E2E encrypted voice calls with documented metadata handling (source available)

Hey r/privacy,

I've been working on SecureCall, an Android app for end-to-end encrypted voice calls.

Why another encrypted calling app?
- No call-content storage; limited routing and delivery metadata is handled as documented
- Source available: Full code on GitHub for verification
- Rust crypto engine: Memory-safe cryptography (XChaCha20, X25519)
- No accounts needed: Privacy by design

Technical details:
- XChaCha20-Poly1305 (AEAD) for voice encryption
- Per-call X25519 and HKDF-SHA256 key derivation; no Double Ratchet or post-compromise-security claim
- Opus audio codec
- Compatible with VPN connections managed by Android or another trusted app

Free tier available, no ads, Crashlytics can be disabled.

Would love feedback from this community. AMA about the security architecture.

Links: [Play Store] | [GitHub] | [Privacy Policy]
```

### Product Hunt

**Preparation:**
- [ ] Create maker profile
- [ ] Prepare Product Hunt listing:
  - Tagline: "End-to-end encrypted voice calls with transparent metadata handling"
  - Description: Feature highlights + tech details
  - Screenshots/video
  - First comment from maker (story behind the app)
- [ ] Schedule for Tuesday or Wednesday launch (best days)
- [ ] Recruit 5-10 supporters to upvote and comment early
- [ ] Respond to every comment on launch day

### Hacker News

**Post title:** "Show HN: SecureCall – E2E encrypted voice calls with Rust crypto engine"

Focus on:
- Technical architecture (Rust, XChaCha20, WebRTC)
- Why we built it
- Source-available model
- Security audit results

## Press & Outreach

### Press Release Template

**FOR IMMEDIATE RELEASE**

**SecureCall Launches End-to-End Encrypted Voice Calling App for Android**

*Source-available app uses XChaCha20-Poly1305 encryption and documents its metadata handling*

[City], [Date] — StealthX today announced the public beta of SecureCall, an Android application that provides end-to-end encrypted voice calls with privacy-oriented data minimization.

Unlike mainstream communication apps, SecureCall is designed from the ground up for privacy:

- Voice calls are encrypted with XChaCha20-Poly1305, a modern AEAD cipher
- The cryptography engine is written in Rust for memory safety
- Call content and contact lists are not stored on the signaling service; limited routing and delivery metadata is handled as documented
- The complete source code is publicly available for security verification

"We believe private communication is a fundamental right, not a premium feature," said [Founder Name], founder of StealthX. "SecureCall makes encrypted calling accessible while publishing its implementation and current security limits."

SecureCall has a Free Google Play edition and separately planned direct Pro and Premium editions. Paid distribution stays closed until the matching product and finance release gates pass.

**About StealthX**
StealthX is a privacy technology project building tools for secure communication.

**Contact:** https://github.com/NeaBouli/stealth/issues
**Website:** https://neabouli.github.io/stealth

### Media Outreach Targets

| Outlet | Contact | Angle |
|--------|---------|-------|
| TechCrunch | tips@techcrunch.com | Privacy-first alternative to mainstream VoIP |
| The Verge | tips@theverge.com | New encrypted calling app |
| Ars Technica | tips@arstechnica.com | Technical deep-dive (Rust, crypto) |
| Heise (DE) | redaktion@heise.de | German-made privacy app |
| Golem (DE) | redaktion@golem.de | Technical details, source-available |
| t3n (DE) | redaktion@t3n.de | German startup story |
| Privacy News Online | — | Privacy tool review |
| EFF (blog) | — | Supporting encrypted communication |

### Influencer / Reviewer Outreach

| Person | Platform | Why |
|--------|----------|-----|
| Techlore | YouTube | Privacy-focused tech reviews |
| The Hated One | YouTube | Privacy advocacy |
| Rob Braxman | YouTube | Privacy tech |
| Louis Rossmann | YouTube | Open source advocacy |
| Mike Kuketz | Blog (DE) | German privacy blog |
| Privacy Guides | Website | Privacy tool recommendations |
| GrapheneOS community | Forum | Privacy-focused Android users |

## Post-Launch (Week 1-4)

### Monitoring

- Google Play Console: ratings, reviews, crashes, vitals
- Social media mentions
- GitHub stars, issues, PRs
- Download numbers per tier

### Community Building

- Respond to every Play Store review
- Engage with Reddit/HN comments
- Create FAQ based on common questions
- Start a blog on neabouli.github.io/stealth with technical posts
- Consider a Matrix/Signal group for the community

### Metrics to Track

| Metric | Target (Month 1) |
|--------|------------------|
| Downloads (FREE) | 1,000+ |
| Downloads (PRO) | 100+ |
| Downloads (PREMIUM) | 50+ |
| Play Store rating | 4.0+ |
| Crash-free rate | > 99.5% |
| Day-7 retention | > 30% |
| GitHub stars | 500+ |
