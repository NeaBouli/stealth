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
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicBoolean

/**
 * AdMob integration for FREE flavor only.
 * - Banner ad at bottom of MainActivity
 * - Interstitial ad after every 3rd completed call
 * - NEVER shown during an active call
 * - Requests current privacy consent before any ad request
 */
object AdMobManager {

    private const val TAG = "AdMob"

    private const val BANNER_ID = "ca-app-pub-4336336811005394/5437857296"
    private const val INTERSTITIAL_ID = "ca-app-pub-4336336811005394/4739986746"

    private var interstitialAd: InterstitialAd? = null
    private var bannerContainerRef: WeakReference<FrameLayout>? = null
    private var callCount = 0
    private const val INTERSTITIAL_INTERVAL = 3

    @Volatile
    var isInitialized = false
        private set

    /**
     * Refresh consent on each app launch and request ads only when UMP permits it.
     * Errors fail closed unless UMP has a still-valid consent decision cached.
     */
    fun requestConsentAndLoad(activity: Activity, container: FrameLayout) {
        val consentInformation = UserMessagingPlatform.getConsentInformation(activity)
        val adsRequested = AtomicBoolean(false)

        fun requestAdsIfAllowed() {
            if (!consentInformation.canRequestAds() || !adsRequested.compareAndSet(false, true)) {
                if (!consentInformation.canRequestAds()) container.visibility = View.GONE
                return
            }
            init(activity.applicationContext)
            loadBanner(activity, container)
            preloadInterstitial(activity.applicationContext)
        }

        val parameters = ConsentRequestParameters.Builder().build()
        consentInformation.requestConsentInfoUpdate(
            activity,
            parameters,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    if (formError != null) {
                        Log.w(TAG, "Consent form unavailable: ${formError.message}")
                    }
                    requestAdsIfAllowed()
                }
                requestAdsIfAllowed()
            },
            { requestError ->
                Log.w(TAG, "Consent update failed: ${requestError.message}")
                requestAdsIfAllowed()
            }
        )
    }

    fun isPrivacyOptionsRequired(context: Context): Boolean =
        UserMessagingPlatform.getConsentInformation(context)
            .privacyOptionsRequirementStatus ==
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

    fun showPrivacyOptions(activity: Activity) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w(TAG, "Privacy options unavailable: ${formError.message}")
                android.widget.Toast.makeText(
                    activity,
                    "Ad privacy options are currently unavailable",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

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
            bannerContainerRef = WeakReference(container)
            if (isCallActive) {
                destroyBanner(container)
                container.visibility = View.GONE
                Log.d(TAG, "Banner suppressed while call UI is active")
                return
            }
            val adView = AdView(activity).apply {
                setAdSize(AdSize.BANNER)
                adUnitId = BANNER_ID
                adListener = object : AdListener() {
                    override fun onAdLoaded() {
                        Log.d(TAG, "Banner loaded")
                        if (isCallActive) {
                            destroy()
                            container.removeAllViews()
                            container.visibility = View.GONE
                        } else {
                            container.visibility = View.VISIBLE
                        }
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
        resumeAfterCall() // Ensure ads are unpaused
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
     * Pause banner ads during an active call (BUG-012).
     * Called from CallActivity.onCreate().
     */
    @Volatile
    var isCallActive = false
        private set

    fun pauseForCall() {
        isCallActive = true
        bannerContainerRef?.get()?.let { container ->
            destroyBanner(container)
            container.visibility = View.GONE
        }
        Log.d(TAG, "Ads paused for active call")
    }

    /**
     * Resume banner ads after call ends (BUG-012).
     * Called from CallActivity.onDestroy().
     */
    fun resumeAfterCall() {
        isCallActive = false
        Log.d(TAG, "Ads resumed after call")
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
