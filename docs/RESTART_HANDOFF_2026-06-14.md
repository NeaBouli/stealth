# StealthX Restart Handoff - 2026-06-14

## Current Project State

- Repo: `/Users/gio/Desktop/repos/stealth`
- Last known pushed commit before AAB work: `2921f6e`
- Commit `2921f6e` fixed SecureCall incoming-call UI race:
  - S7 accept now switches from `Incoming Secure Call` to active call UI.
  - Tab S4 ringback stops after accept.
  - Duplicate WebSocket-path incoming UI launch is guarded.
- Devices used for verification:
  - S7: `ce10160adc00152604`, SecureCall Pro, installed v70001 during test.
  - Tab S4: `ce12182c68644439037e`, SecureCall Free, installed v70001 during test.

## Local Uncommitted Change

- `client_android/app/build.gradle`
  - `versionCode` is locally bumped from `70` to `71`.
  - `versionName` remains `1.0.40`.
- This versionCode bump is not committed yet because the vC71 AAB did not build.

## AAB Status

- New vC71 AAB was NOT produced.
- Do not upload the existing Desktop AAB as vC71.
- Existing old bundle observed:
  - `/Users/gio/Desktop/repos/stealth/client_android/app/build/outputs/bundle/freeRelease/app-free-release.aab`
  - timestamp: `Jun 13 23:20`
  - size: `37M`
- `/Users/gio/Desktop/SecureCall-LATEST.aab` was not confirmed/replaced during the failed build attempts.

## Build Blocker Before Restart

Gradle/Java local process state became unstable:

- `./gradlew --stop` hung without output.
- `./gradlew --no-daemon --max-workers=1 bundleFreeRelease` hung before normal Gradle output.
- Direct `java -jar gradle/wrapper/gradle-wrapper.jar ...` reached:
  - `To honour the JVM settings for this build a single-use Daemon process will be forked.`
  - then hung.
- Process inspection showed stuck Java `jspawnhelper` and Gradle wrapper/daemon processes.
- Processes were killed before this handoff.
- Last process check showed no active Gradle/Ninja/AAPT build process except the check command itself.

## Do After Mac Restart

Run exactly:

```bash
cd /Users/gio/Desktop/repos/stealth/client_android

./gradlew --stop
./gradlew --no-daemon --max-workers=1 bundleFreeRelease

cp app/build/outputs/bundle/freeRelease/app-free-release.aab \
   ~/Desktop/SecureCall-LATEST.aab

ls -lh ~/Desktop/SecureCall-LATEST.aab

git add app/build.gradle
git commit -m "chore: bump versionCode to 71"
git push origin main
```

Expected result:

- New `/Users/gio/Desktop/SecureCall-LATEST.aab`
- Play Console versionCode: `71`
- Bundle version for split APKs remains derived by Play/Gradle, but base AAB versionCode should be `71`.

## Follow-up Still Open

- SecureCall FCM backup notification id `9001` can remain after call cleanup.
- Source is flavor-specific `SecureCallMessagingService`.
- Recommended fix after Play AAB:
  - centralize incoming notification cancellation for ids `1002` and `9001`.

## Do Not Forget

- Do not commit `gradle.properties`; it was temporarily edited during troubleshooting and restored.
- Only commit `client_android/app/build.gradle` for vC71.
- If `git status` hangs immediately after restart, run it once from a fresh Terminal before starting Gradle.
