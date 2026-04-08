# F-Droid: Strip all Firebase classes.
# WalletConnect android-core has internal Firebase push references
# (PushMessagingService) that are unused in the F-Droid build.
# Override the main proguard-rules.pro keep rule.

# Remove the "keep" from main rules — allow R8 to strip Firebase
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# Aggressively remove all Firebase classes (unused in fdroid)
-assumenosideeffects class com.google.firebase.** { *; }
