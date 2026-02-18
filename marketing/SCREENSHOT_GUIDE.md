# Screenshot & Graphics Guide for Play Store

## Required Assets

### App Icon
- **Size:** 512 x 512 px
- **Format:** 32-bit PNG (no alpha channel)
- **Source:** `logo.png` in project root
- **Resize** using Android Studio or ImageMagick:
  ```bash
  convert logo.png -resize 512x512 marketing/app_icon_512.png
  ```

### Feature Graphic (required)
- **Size:** 1024 x 500 px
- **Format:** PNG or JPG
- **Content:** Logo + tagline "End-to-End Encrypted Voice Calls"
- **Create 3 versions** with tier branding:
  - `marketing/feature_graphic_free.png`
  - `marketing/feature_graphic_pro.png`
  - `marketing/feature_graphic_premium.png`

**Design suggestions:**
- Dark background (#121212) matching app theme
- Logo centered
- Tagline below logo in white
- Subtle encryption/shield visual elements
- Tier badge in corner (FREE / PRO / PREMIUM)

### Phone Screenshots (2-8 per listing)
- **Size:** 1080 x 1920 (16:9) or 1080 x 2340 (19.5:9)
- **Format:** PNG or JPG

**Recommended screenshots:**

| # | Screen | What to Show |
|---|--------|-------------|
| 1 | Home/Main | App logo, contact list, floating action button |
| 2 | Active Call | Encrypted call in progress, timer, encryption badge |
| 3 | Contacts | Contact list with avatars |
| 4 | Call History | Recent calls with encryption indicators |
| 5 | Settings | Security settings, privacy options |
| 6 | Onboarding | Welcome screen / security explanation |

### How to Capture Screenshots

**Option A: Android Studio Emulator**
1. Run the app in emulator (Pixel 6 recommended)
2. Navigate to desired screen
3. Click camera icon in emulator toolbar
4. Save to `marketing/screenshots/{flavor}/`

**Option B: Real Device**
1. Connect device via USB
2. Navigate to desired screen
3. `adb exec-out screencap -p > screenshot.png`

**Option C: Android Studio Layout Inspector**
1. Run app in debug mode
2. Use Layout Inspector for pixel-perfect captures

### Screenshot Tips
- Use demo/sample data (not real contacts)
- Ensure status bar shows full battery, WiFi, good time (e.g., 10:30)
- Use demo mode: `adb shell settings put global sysui_demo_allowed 1`
- Show encryption indicators prominently
- Capture both light and dark mode (dark mode preferred for privacy apps)

## Directory Structure

```
marketing/
├── SCREENSHOT_GUIDE.md          (this file)
├── launch_plan.md
├── app_icon_512.png             (to create)
├── feature_graphic_free.png     (to create)
├── feature_graphic_pro.png      (to create)
├── feature_graphic_premium.png  (to create)
├── play_store/
│   ├── de/
│   │   ├── title.txt
│   │   ├── short_description.txt
│   │   ├── full_description.txt
│   │   └── release_notes.txt
│   └── en/
│       ├── title.txt
│       ├── short_description.txt
│       ├── full_description.txt
│       └── release_notes.txt
└── screenshots/
    ├── free/                    (add screenshots here)
    ├── pro/                     (add screenshots here)
    └── premium/                 (add screenshots here)
```

## Tools for Creating Graphics

- **Feature Graphic:** Figma (free), Canva, or GIMP
- **Screenshots with frames:** Android Studio, screener.io, or shotsnapp.com
- **Icon refinement:** Android Studio Adaptive Icon wizard
