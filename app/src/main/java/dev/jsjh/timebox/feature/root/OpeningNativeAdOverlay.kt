package dev.jsjh.timebox.feature.root

import android.content.Context
import android.graphics.Color as AndroidColor
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.nativead.AdChoicesView
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.R
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.ads.OpeningNativeAdGate
import dev.jsjh.timebox.ads.OpeningNativeAdPreloader
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val OpeningNativeAdLoadTimeoutMs = 2_000L
private const val OpeningNativeAdPollIntervalMs = 50L

@Composable
fun OpeningNativeAdOverlay() {
    val context = LocalContext.current
    val adUnitId = BuildConfig.ADMOB_OPENING_NATIVE_AD_UNIT_ID
    val canRequestAds = AdsConsentManager.canRequestAds
    val shouldAttempt = remember { OpeningNativeAdGate.consumeEligibility() }
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    var visible by remember { mutableStateOf(false) }
    var closed by remember { mutableStateOf(!shouldAttempt || adUnitId.isBlank()) }
    var timedOut by remember { mutableStateOf(false) }

    DisposableEffect(nativeAd) {
        val adToDispose = nativeAd
        onDispose { adToDispose?.destroy() }
    }

    LaunchedEffect(shouldAttempt, canRequestAds, adUnitId) {
        if (!shouldAttempt || !canRequestAds || adUnitId.isBlank()) return@LaunchedEffect

        OpeningNativeAdPreloader.preload(context, adUnitId)
        val attempts = (OpeningNativeAdLoadTimeoutMs / OpeningNativeAdPollIntervalMs).toInt()
        repeat(attempts) {
            val ad = OpeningNativeAdPreloader.consume()
            if (ad != null) {
                if (closed || timedOut) {
                    ad.destroy()
                } else {
                    OpeningNativeAdGate.markShown(context)
                    nativeAd = ad
                    visible = true
                }
                return@LaunchedEffect
            }
            delay(OpeningNativeAdPollIntervalMs)
        }
        timedOut = true
        closed = true
    }

    val ad = nativeAd
    if (closed || !visible || ad == null) return

    fun dismiss() {
        visible = false
        closed = true
        nativeAd = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.78f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = ::dismiss
                )
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .widthIn(max = 430.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF262A33))
                .padding(start = 18.dp, top = 18.dp, end = 18.dp, bottom = 22.dp)
        ) {
            AndroidView(
                factory = { createOpeningNativeAdView(it) },
                update = { bindOpeningNativeAdView(it, ad) },
                modifier = Modifier.fillMaxWidth()
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(42.dp)
                    .clip(RoundedCornerShape(21.dp))
                    .clickable(onClick = ::dismiss)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(R.string.opening_ad_close),
                    tint = Color.White
                )
            }
        }
    }
}

private fun createOpeningNativeAdView(context: Context): NativeAdView {
    val adView = NativeAdView(context).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val container = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val headline = TextView(context).apply {
        setTextColor(AndroidColor.WHITE)
        textSize = 25f
        typeface = Typeface.DEFAULT_BOLD
        includeFontPadding = false
        maxLines = 3
        setPadding(0, 0, context.dp(52), 0)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    val mediaFrame = FrameLayout(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            context.dp(330)
        ).apply {
            topMargin = context.dp(18)
        }
        background = GradientDrawable().apply {
            cornerRadius = context.dp(10).toFloat()
            setColor(AndroidColor.BLACK)
        }
    }

    val mediaView = MediaView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
    }

    val adBadge = TextView(context).apply {
        text = "AD"
        setTextColor(AndroidColor.WHITE)
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            setColor(AndroidColor.rgb(76, 80, 94))
        }
        layoutParams = FrameLayout.LayoutParams(context.dp(44), context.dp(30), Gravity.TOP or Gravity.START)
    }

    val adChoices = AdChoicesView(context).apply {
        layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        )
    }

    mediaFrame.addView(mediaView)
    mediaFrame.addView(adBadge)
    mediaFrame.addView(adChoices)

    val body = TextView(context).apply {
        setTextColor(AndroidColor.rgb(165, 171, 184))
        textSize = 16f
        includeFontPadding = false
        maxLines = 2
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            topMargin = context.dp(14)
        }
    }

    container.addView(headline)
    container.addView(mediaFrame)
    container.addView(body)
    adView.addView(container)

    adView.mediaView = mediaView
    adView.adChoicesView = adChoices
    adView.headlineView = headline
    adView.bodyView = body

    return adView
}

private fun bindOpeningNativeAdView(adView: NativeAdView, nativeAd: NativeAd) {
    (adView.headlineView as TextView).text = nativeAd.headline

    val mediaView = adView.mediaView
    val mediaContent = nativeAd.mediaContent
    val mediaFrame = mediaView?.parent as? View
    if (mediaView != null && mediaContent != null) {
        (mediaFrame?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = adView.context.dp(330)
            params.topMargin = adView.context.dp(18)
            mediaFrame.layoutParams = params
        }
        mediaFrame?.visibility = View.VISIBLE
        mediaView.visibility = View.VISIBLE
        mediaView.mediaContent = mediaContent
    } else {
        (mediaFrame?.layoutParams as? LinearLayout.LayoutParams)?.let { params ->
            params.height = adView.context.dp(30)
            params.topMargin = adView.context.dp(14)
            mediaFrame.layoutParams = params
        }
        mediaFrame?.visibility = View.VISIBLE
        mediaView?.visibility = View.GONE
    }

    val bodyView = adView.bodyView as TextView
    if (nativeAd.body.isNullOrBlank()) {
        bodyView.visibility = View.GONE
    } else {
        bodyView.visibility = View.VISIBLE
        bodyView.text = nativeAd.body
    }

    adView.setNativeAd(nativeAd)
}

private fun Context.dp(value: Int): Int =
    (value * resources.displayMetrics.density).roundToInt()
