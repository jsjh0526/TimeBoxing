package dev.jsjh.timebox.ads

import android.content.Context
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.nativead.NativeAd
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import java.util.concurrent.TimeUnit

object OpeningNativeAdPreloader {
    private val MaxCachedAdAgeMs = TimeUnit.MINUTES.toMillis(55)

    private var cachedAd: NativeAd? = null
    private var cachedAtMillis = 0L
    private var loading = false
    private var generation = 0

    fun preload(context: Context, adUnitId: String) {
        discardExpiredCache()
        if (!AdsConsentManager.canRequestAds || adUnitId.isBlank() || loading || cachedAd != null) return

        val requestGeneration = generation
        loading = true
        TimeBoxAnalytics.openingAdRequested()
        AdLoader.Builder(context.applicationContext, adUnitId)
            .forNativeAd { ad ->
                loading = false
                TimeBoxAnalytics.adLoadResult(TimeBoxAnalytics.PLACEMENT_OPENING, loaded = true)
                if (requestGeneration != generation) {
                    ad.destroy()
                } else {
                    cachedAd?.destroy()
                    cachedAd = ad
                    cachedAtMillis = System.currentTimeMillis()
                }
            }
            .withAdListener(object : AdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    if (requestGeneration == generation) loading = false
                    TimeBoxAnalytics.adLoadResult(
                        placement = TimeBoxAnalytics.PLACEMENT_OPENING,
                        loaded = false,
                        errorCode = error.code
                    )
                }
            })
            .build()
            .loadAd(AdRequest.Builder().build())
    }

    fun consume(): NativeAd? {
        discardExpiredCache()
        val ad = cachedAd
        cachedAd = null
        cachedAtMillis = 0L
        return ad
    }

    fun clear() {
        generation += 1
        loading = false
        cachedAd?.destroy()
        cachedAd = null
        cachedAtMillis = 0L
    }

    private fun discardExpiredCache() {
        val ad = cachedAd ?: return
        val ageMs = System.currentTimeMillis() - cachedAtMillis
        if (ageMs >= MaxCachedAdAgeMs) {
            ad.destroy()
            cachedAd = null
            cachedAtMillis = 0L
        }
    }
}
