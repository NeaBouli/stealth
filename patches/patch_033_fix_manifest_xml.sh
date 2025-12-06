#!/bin/bash
set -e

echo "== patch_033: fix AndroidManifest (root + services inside application) =="

cat <<'MANI' > client_android/app/src/main/AndroidManifest.xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.securecall.app">

    <!-- Basis-Rechte; später bei Bedarf erweitern -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
    <!-- Für VpnService -->
    <uses-permission android:name="android.permission.BIND_VPN_SERVICE" />

    <application
        android:allowBackup="false"
        android:label="SecureCall"
        android:theme="@style/Theme.AppCompat.Light.NoActionBar">

        <service
            android:name=".vpn.GhostVpnService"
            android:permission="android.permission.BIND_VPN_SERVICE"
            android:exported="false" />

        <!-- BACKEND-22: WebSocket Hintergrund-Service -->
        <service
            android:name="com.securecall.app.net.WebSocketService"
            android:enabled="true"
            android:exported="false" />

        <activity android:name=".MainActivity" />
        <activity android:name=".SettingsActivity" />
        <activity android:name=".CallActivity" />

    </application>

</manifest>
MANI

echo "[OK] Wrote client_android/app/src/main/AndroidManifest.xml"
echo "== patch_033 done =="
