# ============================================================
# SecureCall ProGuard/R8 Rules
# ============================================================

# ---- OkHttp + Okio ----
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class okio.** { *; }

# ---- Firebase ----
-dontwarn com.google.firebase.**
-dontwarn com.google.android.gms.**

# ---- JNI / Native Methods ----
-keep class com.securecall.crypto.CoreCrypto {
    native <methods>;
    public static *;
}
-keep class com.securecall.app.ghostnet.media.native.NativeOpus {
    native <methods>;
    *;
}

# ---- Enums ----
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ---- WebSocket ----
-keep class * extends okhttp3.WebSocketListener { *; }

# ---- Kotlin ----
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-dontwarn kotlinx.**

# ---- Material Components ----
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

# ---- ViewBinding ----
-keep class **.databinding.** { *; }

# ---- Preference ----
-keep class androidx.preference.** { *; }
-dontwarn androidx.preference.**

# ---- Log stripping (release only) ----
# Remove verbose, debug, and info log calls at compile time
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# ---- WebRTC ----
-keep class org.webrtc.** { *; }
-dontwarn org.webrtc.**
