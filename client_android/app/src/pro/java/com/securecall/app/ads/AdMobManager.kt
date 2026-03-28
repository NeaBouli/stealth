package com.securecall.app.ads

import android.app.Activity
import android.content.Context
import android.widget.FrameLayout

/** No-op AdMob stub for PRO flavor — no ads. */
object AdMobManager {
    var isInitialized = false; private set
    fun init(context: Context) { isInitialized = true }
    fun loadBanner(activity: Activity, container: FrameLayout) {}
    fun preloadInterstitial(context: Context) {}
    fun onCallCompleted(activity: Activity): Boolean = false
    fun pauseForCall() {}
    fun resumeAfterCall() {}
    fun destroyBanner(container: FrameLayout?) {}
}
