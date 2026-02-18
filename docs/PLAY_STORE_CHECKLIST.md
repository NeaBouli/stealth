# Google Play Store Launch Checklist

## Prerequisites

- [ ] Google Play Developer Account created (one-time fee: €25)
- [ ] Developer profile completed (name, address, email, phone)
- [ ] Release keystore generated and securely stored
- [ ] Privacy Policy hosted at public URL (https://stealthx.app/privacy)

## App Setup (repeat for each flavor: FREE, PRO, PREMIUM)

### Store Listing

- [ ] App title (30 chars max)
- [ ] Short description (80 chars max)
- [ ] Full description (4000 chars max)
- [ ] App icon (512 x 512 PNG, 32-bit, no alpha)
- [ ] Feature graphic (1024 x 500 PNG or JPG)
- [ ] Phone screenshots (2-8, 16:9 or 9:16, min 320px, max 3840px)
- [ ] Tablet screenshots (optional but recommended)
- [ ] App category: **Communication**
- [ ] Tags: encrypted calls, privacy, secure messaging, VoIP

### Content Rating

- [ ] IARC rating questionnaire completed
- [ ] Expected rating: **PEGI 3** / **Everyone** (no violent/sexual content)

### Target Audience & Content

- [ ] Target age group: **18+** (encryption/privacy app)
- [ ] Not primarily directed at children: confirmed
- [ ] Ads declaration: **No ads** (all tiers)

### Privacy & Data Safety

- [ ] Privacy Policy URL added
- [ ] Data Safety form completed:
  - Data collected: Crash logs (FREE only, optional)
  - Data shared: None
  - Data encrypted: Yes (in transit)
  - Data deletion: Users can delete from app
- [ ] App permissions declared and justified

### Pricing & Distribution

- [ ] FREE version: Free with in-app purchases
- [ ] PRO version: Paid or Free + IAP upgrade
- [ ] PREMIUM version: Paid or Free + IAP upgrade
- [ ] Countries: All (or select countries)
- [ ] Contains ads: No

## Release Tracks

### 1. Internal Testing (first)

- [ ] Upload signed AAB (app-free-release.aab)
- [ ] Add internal testers (email list)
- [ ] Test install and basic functionality
- [ ] Verify crash reporting works (FREE)
- [ ] Verify debug features are hidden
- [ ] Test on minimum SDK device (API 23 / Android 6.0)

### 2. Closed Testing (Alpha/Beta)

- [ ] Create closed testing track
- [ ] Upload AAB
- [ ] Add 10-20 beta testers
- [ ] Set up feedback collection (email or form)
- [ ] Monitor crashes in Play Console
- [ ] Monitor ANR rate
- [ ] Run for minimum 2 weeks
- [ ] Address critical feedback

### 3. Open Testing (optional)

- [ ] Promote from closed testing
- [ ] Monitor metrics for 1 week
- [ ] Check vitals (crash rate < 1.09%, ANR rate < 0.47%)

### 4. Production Release

- [ ] All testing complete
- [ ] Crash-free rate > 99%
- [ ] Staged rollout: 5% → 10% → 25% → 50% → 100%
- [ ] Monitor reviews and ratings
- [ ] Respond to user reviews

## Post-Launch

- [ ] Set up Play Console alerts (crashes, bad reviews)
- [ ] Monitor Android Vitals daily for first week
- [ ] Prepare hotfix process (in case of critical bugs)
- [ ] Plan v0.3 features based on feedback

## Application IDs

| Flavor  | Application ID             |
|---------|---------------------------|
| FREE    | `com.securecall.app.free`    |
| PRO     | `com.securecall.app.pro`     |
| PREMIUM | `com.securecall.app.premium` |

## Version Info

| Field       | Value        |
|-------------|-------------|
| versionName | `0.2-beta`  |
| versionCode | `2`         |
