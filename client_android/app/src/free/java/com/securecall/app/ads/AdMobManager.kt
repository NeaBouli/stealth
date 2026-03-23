package com.securecall.app.ads

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

/**
 * AdMob integration for FREE flavor only.
 * - Banner ad at bottom of MainActivity
 * - Interstitial ad after every 3rd completed call
 * - NEVER shown during an active call
 * - Uses test IDs in debug builds
 */
object AdMobManager {

    private const val TAG = "AdMob"

    // TODO: Replace with real AdMob IDs from admob.google.com before production release
    // Test Ad Unit IDs (Google-provided, safe for debug)
    private const val BANNER_ID = "ca-app-pub-3940256099942544/6300978111"         // TODO: real Banner ID
    private const val INTERSTITIAL_ID = "ca-app-pub-3940256099942544/1033173712"   // TODO: real Interstitial ID

    private var interstitialAd: InterstitialAd? = null
    private var callCount = 0
    private const val INTERSTITIAL_INTERVAL = 3

    @Volatile
    var isInitialized = false
        private set

    /**
     * Initialize AdMob SDK. Call from Application.onCreate() or MainActivity.
     */
    fun init(context: Context) {
        try {
            MobileAds.initialize(context) { status ->
                Log.d(TAG, "AdMob initialized: ${status.adapterStatusMap}")
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "AdMob init failed: ${e.message}")
        }
    }

    /**
     * Load and attach a banner ad to the given container.
     */
    fun loadBanner(activity: Activity, container: FrameLayout) {
        try {
            val adView = AdView(activity).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Banner loaded")
                        container.visibility = View.VISIBLE
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        Log.w(TAG, "Banner failed: ${error.message}")
                        container.visibility = View.GONE
                    }
                }
            }
            container.removeAllViews()
            container.addView(adView)
            adView.loadAd(AdRequest.Builder().build())
            Log.d(TAG, "Banner load requested")
        } catch (e: Exception) {
            Log.e(TAG, "Banner load error: ${e.message}")
        }
    }

    /**
     * Preload an interstitial ad for showing after calls.
     */
    fun preloadInterstitial(context: Context) {
        if (interstitialAd != null) return
        try {
            InterstitialAd.load(context, INTERSTITIAL_ID, AdRequest.Builder().build(),
                object : InterstitialAdLoadCallback() {
                    override fun onAdLoaded(ad: InterstitialAd) {
                        interstitialAd = ad
                        Log.d(TAG, "Interstitial preloaded")
                    }
                    override fun onAdFailedToLoad(error: LoadAdError) {
                        interstitialAd = null
                        Log.w(TAG, "Interstitial failed to load: ${error.message}")
                    }
                })
        } catch (e: Exception) {
            Log.e(TAG, "Interstitial preload error: ${e.message}")
        }
    }

    /**
     * Called after each completed call. Shows interstitial every INTERSTITIAL_INTERVAL calls.
     * Returns true if an ad was shown.
     */
    fun onCallCompleted(activity: Activity): Boolean {
        callCount++
        if (callCount % INTERSTITIAL_INTERVAL != 0) return false

        val ad = interstitialAd ?: return false
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d(TAG, "Interstitial dismissed")
                interstitialAd = null
                preloadInterstitial(activity)
            }
            override fun onAdFailedToShowFullScreenContent(error: com.google.android.gms.ads.AdError) {
                Log.w(TAG, "Interstitial failed to show: ${error.message}")
                interstitialAd = null
                preloadInterstitial(activity)
            }
        }
        ad.show(activity)
        Log.d(TAG, "Interstitial shown after call #$callCount")
        return true
    }

    /**
     * Destroy banner when activity is destroyed.
     */
    fun destroyBanner(container: FrameLayout?) {
        container?.let {
            for (i in 0 until it.childCount) {
                val child = it.getChildAt(i)
                if (child is AdView) child.destroy()
            }
            it.removeAllViews()
        }
    }
}
