# ACTION REQUIRED: Deploy Backend & Update URLs

## Step 1: Deploy to Railway.app

1. Go to: https://railway.app
2. Login with GitHub
3. New Project -> Deploy from GitHub -> NeaBouli/stealth
4. Set Root Directory to: `backend/signaling`
5. Set environment variables (see BACKEND_DEPLOYMENT_GUIDE.md)
6. Wait for deploy (~2 min)

## Step 2: Get Your URLs

After deployment, Railway shows your URL:
```
https://[YOUR-APP-NAME].up.railway.app
```

## Step 3: Get Metered.ca TURN Credentials

1. Sign up: https://www.metered.ca/stun-turn
2. Get credentials from Dashboard
3. Add TURN_USER and TURN_PASS to Railway Environment Variables

## Step 4: Update Android App

Replace `[YOUR-APP-NAME]` with your actual Railway app name:

File: `client_android/app/build.gradle`
```gradle
buildConfigField "String", "SERVER_URL", "\"wss://[YOUR-APP-NAME].up.railway.app/signal\""
```

## Step 5: Rebuild APKs

```bash
cd client_android
./gradlew clean assembleFreeRelease
```

## Step 6: Test

Install APK on 2 devices and make a test call!

## Full Guide

See: [BACKEND_DEPLOYMENT_GUIDE.md](./BACKEND_DEPLOYMENT_GUIDE.md)
