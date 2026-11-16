package dev.intelisystems.potrosackakorpa

import android.app.Activity
import android.os.SystemClock
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.ump.UserMessagingPlatform

/** Navigation never waits for an ad download; only an already loaded ad is shown. */
internal class NavigationAd(private val activity: Activity) {
    private val preferences = activity.getSharedPreferences("navigation-ads", Activity.MODE_PRIVATE)
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private var ad: InterstitialAd? = null
    private var loadedAt = 0L
    private var loading = false
    private var destroyed = false
    private var navigating = false
    private var continuation: (() -> Unit)? = null

    fun preload() {
        if (destroyed || activity.isFinishing || loading || navigating ||
            BuildConfig.AD_INTERSTITIAL_ID.isEmpty() || !consent.canRequestAds()) return
        if (ad != null && SystemClock.elapsedRealtime() - loadedAt < 55 * 60_000L) return
        ad = null
        loading = true
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                if (destroyed || activity.isFinishing || !consent.canRequestAds()) {
                    loading = false
                    return@runOnUiThread
                }
                InterstitialAd.load(activity, BuildConfig.AD_INTERSTITIAL_ID, AdRequest.Builder().build(),
                    object : InterstitialAdLoadCallback() {
                        override fun onAdLoaded(value: InterstitialAd) {
                            loading = false
                            if (!destroyed && consent.canRequestAds()) {
                                ad = value
                                loadedAt = SystemClock.elapsedRealtime()
                            }
                        }
                        override fun onAdFailedToLoad(error: LoadAdError) { loading = false }
                    })
            }
        }
    }

    fun navigate(openMap: () -> Unit) {
        if (destroyed || activity.isFinishing || navigating) return
        val ready = ad
        val now = System.currentTimeMillis()
        val last = preferences.getLong("last_shown", 0L)
        if (ready == null || !consent.canRequestAds() ||
            SystemClock.elapsedRealtime() - loadedAt >= 55 * 60_000L ||
            (last != 0L && now - last < 5 * 60_000L)) {
            openMap()
            return
        }
        navigating = true
        continuation = openMap
        ad = null // An interstitial may only be shown once.
        ready.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdShowedFullScreenContent() {
                preferences.edit().putLong("last_shown", System.currentTimeMillis()).apply()
            }
            override fun onAdDismissedFullScreenContent() { finishNavigation() }
            override fun onAdFailedToShowFullScreenContent(error: AdError) { finishNavigation() }
        }
        try {
            ready.show(activity)
        } catch (_: RuntimeException) {
            finishNavigation()
        }
    }

    private fun finishNavigation() {
        val action = continuation ?: return
        continuation = null // Dismiss/failure cannot open the map twice.
        navigating = false
        if (!destroyed && !activity.isFinishing && !activity.isDestroyed) action()
    }

    fun destroy() {
        destroyed = true
        ad = null
        continuation = null
    }
}
