package dev.jsjh.timebox.billing

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.BillingClient.BillingResponseCode
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.ads.AdsConsentManager
import dev.jsjh.timebox.ads.OpeningNativeAdGate
import dev.jsjh.timebox.ui.isReadyForExternalUi
import dev.jsjh.timebox.widget.TodoWidgetUpdater
import dev.jsjh.timebox.widget.WidgetAccessStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal object AdFreeBilling {
    private var instance: AdFreeBillingManager? = null

    @Synchronized fun get(context: Context): AdFreeBillingManager = instance ?: run {
        val appContext = context.applicationContext
        AdFreeBillingManager(
            enabled = BuildConfig.MONETIZATION_ENABLED,
            gateway = if (BuildConfig.MONETIZATION_ENABLED) PlayBillingGateway(appContext) else null,
            accessStore = AdFreeAccessStore(appContext),
            onCacheRefreshed = { WidgetAccessStore.scheduleExpiryRefresh(appContext) },
            onAccessChanged = {
                TodoWidgetUpdater.requestUpdate(appContext)
            }
        ).also { instance = it }
    }
}

internal class AdFreeBillingManager(
    enabled: Boolean,
    private val gateway: BillingGateway?,
    private val accessStore: AdFreeAccessStore,
    private val handler: Handler = Handler(Looper.getMainLooper()),
    private val onCacheRefreshed: () -> Unit = {},
    private val onAccessChanged: () -> Unit = {}
) {
    private val mutableState = MutableStateFlow(AdFreeBillingState(
        enabled = enabled, hasAccess = enabled && accessStore.hasAccess(), initialCheckComplete = !enabled
    ))
    val state = mutableState.asStateFlow()
    private var queryGeneration = 0
    private var refreshInFlight = false
    private var refreshAgain = false
    private var restoreRequested = false
    private var queryTimeout: Runnable? = null
    private var expiryRefresh: Runnable? = null
    private var purchaseTimeout: Runnable? = null
    private val acknowledgedThisSession = mutableSetOf<String>()

    init {
        gateway?.onPurchasesChanged = { code, pending -> onMain {
            clearPurchaseTimeout()
            mutableState.value = state.value.copy(
                purchaseInProgress = false,
                awaitingConfirmation = code == BillingResponseCode.OK || code == BillingResponseCode.ITEM_ALREADY_OWNED,
                notice = when {
                    code == BillingResponseCode.USER_CANCELED -> BillingNotice.CANCELLED
                    code == BillingResponseCode.OK && pending -> BillingNotice.PENDING
                    code == BillingResponseCode.OK || code == BillingResponseCode.ITEM_ALREADY_OWNED -> null
                    else -> BillingNotice.PURCHASE_FAILED
                }
            )
            refresh(preserveNotice = true)
        } }
        scheduleCacheExpiry()
    }

    fun refresh(restore: Boolean = false, preserveNotice: Boolean = false) {
        if (!state.value.enabled || gateway == null) return
        restoreRequested = restoreRequested || restore
        if (refreshInFlight) {
            refreshAgain = true
            return
        }
        refreshInFlight = true
        refreshAgain = false
        val generation = ++queryGeneration
        mutableState.value = state.value.copy(refreshing = true, formattedPrice = null,
            notice = if (preserveNotice) state.value.notice else null)
        queryTimeout = Runnable { if (isCurrent(generation)) failRefresh(generation) }
            .also { handler.postDelayed(it, 8_000) }
        try {
            gateway.connect { code -> onMain {
                if (!isCurrent(generation)) return@onMain
                if (code != BillingResponseCode.OK) {
                    failRefresh(generation)
                } else {
                    queryPurchases(generation)
                }
            } }
        } catch (_: Exception) {
            failRefresh(generation)
        }
    }

    private fun queryPurchases(generation: Int) {
        try {
            gateway?.queryPurchases { code, purchases -> onMain {
                if (!isCurrent(generation)) return@onMain
                if (refreshAgain) {
                    finishRefresh(generation)
                } else if (code != BillingResponseCode.OK) {
                    failRefresh(generation)
                } else {
                    reconcile(generation, purchases)
                }
            } }
        } catch (_: Exception) {
            failRefresh(generation)
        }
    }

    private fun reconcile(generation: Int, purchases: List<BillingPurchase>) {
        val active = purchases.filter { it.isAdFree && it.purchased && it.token.isNotBlank() }
        val unacknowledged = active.filter { !it.acknowledged && it.token !in acknowledgedThisSession }
            .distinctBy { it.token }
        var remaining = unacknowledged.size
        var acknowledgementFailed = false

        fun complete() {
            if (!isCurrent(generation)) return
            if (refreshAgain) {
                finishRefresh(generation)
                return
            }
            val hasAccess = active.any { it.acknowledged || it.token in acknowledgedThisSession }
            // An acknowledgement error is not proof that an existing subscription expired.
            if (hasAccess || !acknowledgementFailed) commitAccess(hasAccess)
            mutableState.value = state.value.copy(
                initialCheckComplete = true,
                awaitingConfirmation = purchases.any { it.isAdFree && it.pending } ||
                    (acknowledgementFailed && !hasAccess),
                notice = when {
                    acknowledgementFailed && !hasAccess -> BillingNotice.VERIFICATION_FAILED
                    purchases.any { it.isAdFree && it.pending } -> BillingNotice.PENDING
                    restoreRequested && hasAccess -> BillingNotice.RESTORED
                    restoreRequested -> BillingNotice.NOTHING_TO_RESTORE
                    else -> state.value.notice?.takeIf {
                        it == BillingNotice.CANCELLED || it == BillingNotice.PURCHASE_FAILED ||
                            it == BillingNotice.WINDOW_NOT_READY
                    }
                }
            )
            restoreRequested = false
            finishRefresh(generation)
            queryPrice(generation)
        }

        if (remaining == 0) {
            complete()
            return
        }
        unacknowledged.forEach { purchase ->
            try {
                gateway?.acknowledge(purchase.token) { code -> onMain {
                    if (!isCurrent(generation)) return@onMain
                    if (code == BillingResponseCode.OK) acknowledgedThisSession += purchase.token
                    else acknowledgementFailed = true
                    remaining -= 1
                    if (remaining == 0) complete()
                } }
            } catch (_: Exception) {
                acknowledgementFailed = true
                remaining -= 1
                if (remaining == 0) complete()
            }
        }
    }

    private fun queryPrice(generation: Int) {
        if (generation != queryGeneration) return
        try {
            gateway?.queryPrice { price -> onMain {
                if (generation != queryGeneration) return@onMain
                mutableState.value = state.value.copy(formattedPrice = price,
                    notice = if (price == null && !state.value.hasAccess && state.value.notice == null)
                        BillingNotice.UNAVAILABLE else state.value.notice)
            } }
        } catch (_: Exception) {
            mutableState.value = state.value.copy(formattedPrice = null, notice = BillingNotice.UNAVAILABLE)
        }
    }

    fun purchase(activity: Activity) {
        if (!state.value.canPurchase || gateway == null) return
        if (!activity.isReadyForExternalUi() || AdsConsentManager.isConsentFlowActive(activity) ||
            OpeningNativeAdGate.isOverlayActive()) {
            mutableState.value = state.value.copy(notice = BillingNotice.WINDOW_NOT_READY)
            return
        }
        mutableState.value = state.value.copy(purchaseInProgress = true, awaitingConfirmation = true, notice = null)
        val code = try { gateway.launch(activity) } catch (_: Exception) { BillingResponseCode.ERROR }
        if (code != BillingResponseCode.OK) {
            mutableState.value = state.value.copy(purchaseInProgress = false, awaitingConfirmation = false,
                notice = BillingNotice.PURCHASE_FAILED)
            if (code == BillingResponseCode.ITEM_ALREADY_OWNED) refresh(restore = true)
            return
        }
        if (!state.value.purchaseInProgress) return
        clearPurchaseTimeout()
        purchaseTimeout = Runnable {
            mutableState.value = state.value.copy(purchaseInProgress = false)
            refresh()
        }.also { handler.postDelayed(it, 90_000) }
    }

    fun manage(activity: Activity) {
        if (!activity.isReadyForExternalUi()) return
        val uri = Uri.parse("https://play.google.com/store/account/subscriptions").buildUpon()
            .appendQueryParameter("sku", AdFreeProductId)
            .appendQueryParameter("package", activity.packageName).build()
        runCatching { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            mutableState.value = state.value.copy(notice = BillingNotice.UNAVAILABLE)
        }
    }

    private fun failRefresh(generation: Int) {
        if (!isCurrent(generation)) return
        val stillCached = accessStore.hasAccess()
        val changed = state.value.hasAccess != stillCached
        mutableState.value = state.value.copy(hasAccess = stillCached, initialCheckComplete = true,
            formattedPrice = null, notice = BillingNotice.UNAVAILABLE)
        if (changed) runCatching(onAccessChanged)
        restoreRequested = false
        finishRefresh(generation)
    }

    private fun commitAccess(hasAccess: Boolean) {
        val changed = state.value.hasAccess != hasAccess
        accessStore.recordPlayResult(hasAccess)
        mutableState.value = state.value.copy(hasAccess = hasAccess)
        scheduleCacheExpiry()
        if (hasAccess || changed) runCatching(onCacheRefreshed)
        if (changed) runCatching(onAccessChanged)
    }

    private fun finishRefresh(generation: Int) {
        if (!isCurrent(generation)) return
        queryTimeout?.let(handler::removeCallbacks)
        queryTimeout = null
        refreshInFlight = false
        mutableState.value = state.value.copy(refreshing = false)
        if (refreshAgain) refresh(preserveNotice = true)
    }

    private fun scheduleCacheExpiry() {
        expiryRefresh?.let(handler::removeCallbacks)
        val remaining = accessStore.cacheExpiresAtMillis() - System.currentTimeMillis()
        if (remaining <= 0) return
        expiryRefresh = Runnable {
            if (!accessStore.hasAccess()) {
                mutableState.value = state.value.copy(hasAccess = false)
                runCatching(onAccessChanged)
            }
            refresh()
        }.also { handler.postDelayed(it, remaining + 1) }
    }

    private fun clearPurchaseTimeout() {
        purchaseTimeout?.let(handler::removeCallbacks)
        purchaseTimeout = null
    }

    private fun isCurrent(generation: Int): Boolean = refreshInFlight && generation == queryGeneration

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == handler.looper) block() else handler.post(block)
    }
}
