package com.securecall.app.fcm

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * F-Droid stub: no Firebase Messaging Service.
 * The fdroid AndroidManifest.xml removes the service declaration (tools:node="remove").
 * This stub extends Service to satisfy lint's Instantiatable check on the main manifest.
 */
class SecureCallMessagingService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
}
