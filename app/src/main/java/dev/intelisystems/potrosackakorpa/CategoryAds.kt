package dev.intelisystems.potrosackakorpa

import android.app.Activity
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform

/** Independent banners; product counts and pagination exclude ad slots. */
internal class CategoryAds(private val activity: Activity, private val adSize: AdSize = AdSize.MEDIUM_RECTANGLE,
                           private val onReady: () -> Unit = {}) {
    private val consent = UserMessagingPlatform.getConsentInformation(activity)
    private val slots = linkedMapOf<LinearLayout, AdView?>()
    private var consentStarted = false
    private var consentFinished = false
    private var initializing = false
    private var ready = false
    private var destroyed = false
    private var resumed = false

    fun insert(parent: LinearLayout) {
        if (BuildConfig.AD_BANNER_ID.isEmpty() || destroyed) return
        val slot = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            visibility = View.GONE
            val gap = (16 * resources.displayMetrics.density).toInt()
            setPadding(0, gap, 0, gap)
            addView(TextView(activity).apply {
                text = "Oglas"
                textSize = 12f
                setTextColor(KorpaUi.muted)
            })
            parent.addView(this, LinearLayout.LayoutParams(-1, -2))
        }
        slots[slot] = null
        if (!consentStarted) {
            consentStarted = true
            consent.requestConsentInfoUpdate(activity, ConsentRequestParameters.Builder().build(), {
                if (!destroyed) UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) {
                    consentFinished = true
                    prepare()
                }
            }, {
                consentFinished = true
                prepare() // Only a still-valid previous consent can permit requests on error.
            })
        } else prepare()
    }

    private fun prepare() {
        if (destroyed || !consentFinished || !consent.canRequestAds() || slots.isEmpty()) return
        if (ready) { onReady(); load(); return }
        if (initializing) return
        initializing = true
        MobileAds.initialize(activity.applicationContext) {
            activity.runOnUiThread {
                ready = true
                if (!destroyed) { onReady(); load() }
            }
        }
    }

    private fun load() {
        slots.keys.toList().forEach { load(it) }
    }

    private fun load(target: LinearLayout) {
        if (destroyed || !slots.containsKey(target) || slots[target] != null || !consent.canRequestAds()) return
        // Do not request a fixed-size banner if the available list width cannot fit it.
        (target.parent as? View)?.post {
            if (destroyed || !slots.containsKey(target) || slots[target] != null || !consent.canRequestAds()) return@post
            val parent = target.parent as? View ?: return@post
            val width = ((parent.width - parent.paddingLeft - parent.paddingRight) /
                activity.resources.displayMetrics.density).toInt()
            if (width < adSize.width) { remove(target); return@post }
            val ad = AdView(activity)
            slots[target] = ad
            ad.adUnitId = BuildConfig.AD_BANNER_ID
            ad.setAdSize(adSize)
            ad.adListener = object : AdListener() {
                override fun onAdLoaded() {
                    if (!destroyed && slots[target] === ad) {
                        target.visibility = View.VISIBLE
                        if (!resumed) ad.pause()
                    }
                }
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (slots[target] === ad) remove(target)
                }
            }
            target.addView(ad)
            ad.loadAd(AdRequest.Builder().build())
        }
    }

    fun privacy() {
        if (consent.privacyOptionsRequirementStatus !=
            com.google.android.ump.ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED) {
            Toast.makeText(activity, "Podešavanja saglasnosti trenutno nisu potrebna ili još nisu učitana.", Toast.LENGTH_LONG).show()
            return
        }
        clear()
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { error ->
            if (!destroyed && error != null) Toast.makeText(activity,
                "Podešavanja trenutno nisu dostupna. Pokušaj ponovo.", Toast.LENGTH_LONG).show()
        }
    }

    fun pause() { resumed = false; slots.values.toList().forEach { it?.pause() } }
    fun resume() { resumed = true; slots.values.toList().forEach { it?.resume() } }
    private fun remove(target: LinearLayout) {
        val ad = slots.remove(target)
        ad?.adListener = object : AdListener() {}
        ad?.destroy()
        (target.parent as? LinearLayout)?.removeView(target)
    }
    fun clear() {
        slots.keys.toList().forEach { remove(it) }
    }
    fun destroy() { destroyed = true; clear() }
}
