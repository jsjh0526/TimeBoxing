package dev.jsjh.timebox.billing

import android.app.Activity
import android.content.Context
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.activity.ComponentActivity
import android.widget.FrameLayout
import com.android.billingclient.api.BillingClient.BillingResponseCode
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.widget.WidgetAccessStore
import java.time.Duration
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
@LooperMode(LooperMode.Mode.PAUSED)
class AdFreeBillingTest {
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var store: AdFreeAccessStore
    private lateinit var gateway: FakeGateway

    @Before fun setUp() {
        context.getSharedPreferences("ad_free_internal", Context.MODE_PRIVATE).edit().clear().commit()
        store = AdFreeAccessStore(context, enabled = true)
        gateway = FakeGateway()
    }

    private fun manager(enabled: Boolean = true) = AdFreeBillingManager(enabled, gateway, store)
    private fun purchase(acknowledged: Boolean = true, pending: Boolean = false,
                         product: String = AdFreeProductId, token: String = "purchase-token") =
        BillingPurchase(token, listOf(product), !pending, pending, acknowledged)

    @Test fun experimentOffLeavesAdsAloneAndNeverConnectsToPlay() {
        val manager = manager(enabled = false)
        manager.refresh(restore = true)
        assertTrue(manager.state.value.adsAllowed)
        assertFalse(manager.state.value.hasAccess)
        assertEquals(0, gateway.connections)
    }

    @Test fun firstCheckDoesNotBlockAppAndOnlyTemporarilyHoldsAds() {
        val manager = manager()
        assertFalse(manager.state.value.adsAllowed)
        manager.refresh()
        assertTrue(manager.state.value.initialCheckComplete)
        assertTrue(manager.state.value.adsAllowed)
        assertEquals("₩3,000", manager.state.value.formattedPrice)
        assertTrue(manager.state.value.canPurchase)
    }

    @Test fun activePurchaseRemovesAdsAndDisablesDuplicatePurchase() {
        gateway.purchases = listOf(purchase())
        val manager = manager()
        manager.refresh()
        assertTrue(manager.state.value.hasAccess)
        assertFalse(manager.state.value.adsAllowed)
        assertFalse(manager.state.value.canPurchase)
        assertTrue(store.hasAccess())
    }

    @Test fun pendingPurchaseNeverGrantsAccessOrAcknowledges() {
        gateway.purchases = listOf(purchase(acknowledged = false, pending = true))
        val manager = manager()
        manager.refresh()
        assertFalse(manager.state.value.hasAccess)
        assertTrue(manager.state.value.adsAllowed)
        assertEquals(BillingNotice.PENDING, manager.state.value.notice)
        assertFalse(manager.state.value.canPurchase)
        assertTrue(manager.state.value.awaitingConfirmation)
        assertTrue(gateway.acknowledgedTokens.isEmpty())
    }

    @Test fun pendingAndNetworkNoticesClearAfterSuccessfulRetry() {
        gateway.purchases = listOf(purchase(pending = true))
        val manager = manager()
        manager.refresh()
        assertEquals(BillingNotice.PENDING, manager.state.value.notice)
        gateway.purchases = listOf(purchase())
        manager.refresh(preserveNotice = true)
        assertTrue(manager.state.value.hasAccess)
        assertNull(manager.state.value.notice)
        gateway.queryCode = BillingResponseCode.SERVICE_UNAVAILABLE
        manager.refresh()
        assertEquals(BillingNotice.UNAVAILABLE, manager.state.value.notice)
        gateway.queryCode = BillingResponseCode.OK
        manager.refresh(preserveNotice = true)
        assertNull(manager.state.value.notice)
    }

    @Test fun purchaseRequiresFocusedResumedWindowAndPreventsDuplicateLaunches() {
        val manager = manager()
        manager.refresh()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create().start()
        val activity = controller.get()
        activity.setContentView(FrameLayout(activity))
        try {
            manager.purchase(activity)
            assertEquals(0, gateway.launches)
            assertEquals(BillingNotice.WINDOW_NOT_READY, manager.state.value.notice)
            controller.resume().visible().windowFocusChanged(true)
            manager.purchase(activity)
            manager.purchase(activity)
            assertEquals(1, gateway.launches)
            assertTrue(manager.state.value.purchaseInProgress)
            gateway.onPurchasesChanged(BillingResponseCode.USER_CANCELED, false)
            assertFalse(manager.state.value.purchaseInProgress)
            manager.purchase(activity)
            assertEquals(2, gateway.launches)
        } finally {
            controller.pause().stop().destroy()
        }
    }

    @Test fun destroyedActivityAndSdkLaunchExceptionCannotCrashTheApp() {
        val manager = manager()
        manager.refresh()
        val controller = Robolectric.buildActivity(ComponentActivity::class.java).create().start()
        val activity = controller.get()
        activity.setContentView(FrameLayout(activity))
        controller.resume().visible().windowFocusChanged(true)
        gateway.throwOnLaunch = true
        manager.purchase(activity)
        assertFalse(manager.state.value.purchaseInProgress)
        assertEquals(BillingNotice.PURCHASE_FAILED, manager.state.value.notice)
        controller.pause().stop().destroy()
        gateway.throwOnLaunch = false
        manager.purchase(activity)
        assertEquals(1, gateway.launches)
        assertEquals(BillingNotice.WINDOW_NOT_READY, manager.state.value.notice)
    }

    @Test fun acknowledgementCompletesBeforeAccessAndIsIdempotent() {
        gateway.purchases = listOf(purchase(acknowledged = false))
        gateway.deferAcknowledgement = true
        val manager = manager()
        manager.refresh()
        assertFalse(manager.state.value.hasAccess)
        gateway.pendingAcknowledgement!!(BillingResponseCode.OK)
        assertTrue(manager.state.value.hasAccess)
        gateway.deferAcknowledgement = false
        manager.refresh()
        assertEquals(listOf("purchase-token"), gateway.acknowledgedTokens)
    }

    @Test fun acknowledgementFailureDoesNotUnlockAndRestoreCanRetry() {
        gateway.purchases = listOf(purchase(acknowledged = false))
        gateway.acknowledgeCode = BillingResponseCode.SERVICE_UNAVAILABLE
        val manager = manager()
        manager.refresh()
        assertFalse(manager.state.value.hasAccess)
        assertEquals(BillingNotice.VERIFICATION_FAILED, manager.state.value.notice)
        assertTrue(manager.state.value.awaitingConfirmation)
        assertFalse(manager.state.value.canPurchase)
        gateway.acknowledgeCode = BillingResponseCode.OK
        manager.refresh(restore = true)
        assertTrue(manager.state.value.hasAccess)
        assertEquals(BillingNotice.RESTORED, manager.state.value.notice)
    }

    @Test fun otherProductsAndEmptyTokensCannotUnlock() {
        gateway.purchases = listOf(purchase(product = "another_product"), purchase(token = ""))
        val manager = manager()
        manager.refresh(restore = true)
        assertFalse(manager.state.value.hasAccess)
        assertEquals(BillingNotice.NOTHING_TO_RESTORE, manager.state.value.notice)
    }

    @Test fun successfulEmptyQueryRevokesExpiredAccessWithoutTouchingTaskData() {
        store.recordPlayResult(true)
        val manager = manager()
        assertTrue(manager.state.value.hasAccess)
        manager.refresh()
        assertFalse(manager.state.value.hasAccess)
        assertFalse(store.hasAccess())
        assertTrue(manager.state.value.adsAllowed)
    }

    @Test fun transientFailureKeepsBoundedCachedAccessAndAllowsRetry() {
        store.recordPlayResult(true)
        gateway.queryCode = BillingResponseCode.SERVICE_UNAVAILABLE
        val manager = manager()
        manager.refresh()
        assertTrue(manager.state.value.hasAccess)
        assertFalse(manager.state.value.refreshing)
        assertEquals(BillingNotice.UNAVAILABLE, manager.state.value.notice)
        gateway.queryCode = BillingResponseCode.OK
        gateway.purchases = listOf(purchase())
        manager.refresh(restore = true)
        assertEquals(BillingNotice.RESTORED, manager.state.value.notice)
    }

    @Test fun hungServiceTimesOutAndLateCallbacksCannotOverwriteNewQuery() {
        gateway.deferConnection = true
        val manager = manager()
        manager.refresh()
        val late = gateway.pendingConnection!!
        shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(9))
        assertFalse(manager.state.value.refreshing)
        assertTrue(manager.state.value.adsAllowed)
        gateway.deferConnection = false
        gateway.purchases = listOf(purchase())
        manager.refresh()
        assertTrue(manager.state.value.hasAccess)
        late(BillingResponseCode.ERROR)
        assertTrue(manager.state.value.hasAccess)
    }

    @Test fun purchaseEventDuringOldQueryDiscardsItsResultAndRequeries() {
        gateway.deferQuery = true
        val manager = manager()
        manager.refresh()
        val old = gateway.pendingQuery!!
        gateway.onPurchasesChanged(BillingResponseCode.OK, false)
        gateway.deferQuery = false
        gateway.purchases = listOf(purchase())
        old(BillingResponseCode.OK, emptyList())
        assertTrue(manager.state.value.hasAccess)
        assertFalse(manager.state.value.refreshing)
        assertEquals(2, gateway.queries)
    }

    @Test fun userCancellationDoesNotGrantAccessAndIsNotAnErrorState() {
        val manager = manager()
        manager.refresh()
        gateway.onPurchasesChanged(BillingResponseCode.USER_CANCELED, false)
        assertFalse(manager.state.value.hasAccess)
        assertFalse(manager.state.value.purchaseInProgress)
        assertEquals(BillingNotice.CANCELLED, manager.state.value.notice)
    }

    @Test fun missingConsoleProductDisablesPurchaseButDoesNotHideExistingEntitlement() {
        gateway.price = null
        gateway.purchases = listOf(purchase())
        val manager = manager()
        manager.refresh()
        assertTrue(manager.state.value.hasAccess)
        assertNull(manager.state.value.formattedPrice)
        assertFalse(manager.state.value.canPurchase)
    }

    @Test fun widgetRefreshFailureCannotInterruptPurchaseReconciliation() {
        gateway.purchases = listOf(purchase())
        val manager = AdFreeBillingManager(true, gateway, store,
            onCacheRefreshed = { throw IllegalStateException("Alarm unavailable") },
            onAccessChanged = { throw IllegalStateException("Widget unavailable") })
        manager.refresh()
        assertTrue(manager.state.value.hasAccess)
        assertTrue(store.hasAccess())
        assertFalse(manager.state.value.refreshing)
        gateway.purchases = emptyList()
        manager.refresh()
        assertFalse(manager.state.value.hasAccess)
        assertFalse(manager.state.value.refreshing)
    }

    @Test fun cacheNeverGrantsIndefiniteOrClockReversedAccess() {
        val wall = 1_000_000L
        val elapsed = 10_000L
        assertTrue(cachedAccessIsValid(wall, elapsed, wall + 1, elapsed + 1))
        assertFalse(cachedAccessIsValid(wall, elapsed, wall - 1, elapsed + 1))
        assertFalse(cachedAccessIsValid(wall, elapsed, wall + 1, elapsed - 1))
        assertFalse(cachedAccessIsValid(wall, elapsed, wall + AdFreeCacheDurationMillis, elapsed + AdFreeCacheDurationMillis))
        assertFalse(cachedAccessIsValid(0, elapsed, wall, elapsed))
    }

    @Test fun cachedAccessSurvivesManagerRecreationButIsIgnoredWhenExperimentOff() {
        store.recordPlayResult(true)
        assertTrue(AdFreeAccessStore(context, enabled = true).hasAccess())
        assertTrue(manager().state.value.hasAccess)
        assertFalse(AdFreeAccessStore(context, enabled = false).hasAccess())
        assertFalse(manager(enabled = false).state.value.hasAccess)
    }

    @Test fun subscriptionDoesNotOverwriteAccountScopedRewardTime() {
        ActiveUserStore.saveLoggedIn(context, "billing-widget-owner")
        val prefs = context.getSharedPreferences("widget_access_billing_widget_owner", Context.MODE_PRIVATE)
        val rewardedUntil = System.currentTimeMillis() + 60_000
        prefs.edit().putLong("unlocked_until", rewardedUntil).commit()
        store.recordPlayResult(true)
        assertTrue(WidgetAccessStore.isUnlocked(context))
        store.recordPlayResult(false)
        assertEquals(rewardedUntil, WidgetAccessStore.unlockedUntilMillis(context))
        assertTrue(WidgetAccessStore.isUnlocked(context))
        prefs.edit().clear().commit()
        ActiveUserStore.clear(context)
    }

    private class FakeGateway : BillingGateway {
        override var onPurchasesChanged: (Int, Boolean) -> Unit = { _, _ -> }
        var connections = 0
        var queries = 0
        var launches = 0
        var throwOnLaunch = false
        var purchases = emptyList<BillingPurchase>()
        var queryCode = BillingResponseCode.OK
        var acknowledgeCode = BillingResponseCode.OK
        var price: String? = "₩3,000"
        var deferConnection = false
        var deferQuery = false
        var deferAcknowledgement = false
        var pendingConnection: ((Int) -> Unit)? = null
        var pendingQuery: ((Int, List<BillingPurchase>) -> Unit)? = null
        var pendingAcknowledgement: ((Int) -> Unit)? = null
        val acknowledgedTokens = mutableListOf<String>()
        override fun connect(callback: (Int) -> Unit) {
            connections++
            if (deferConnection) pendingConnection = callback else callback(BillingResponseCode.OK)
        }
        override fun queryPurchases(callback: (Int, List<BillingPurchase>) -> Unit) {
            queries++
            if (deferQuery) pendingQuery = callback else callback(queryCode, purchases)
        }
        override fun queryPrice(callback: (String?) -> Unit) = callback(price)
        override fun acknowledge(token: String, callback: (Int) -> Unit) {
            acknowledgedTokens += token
            if (deferAcknowledgement) pendingAcknowledgement = callback else callback(acknowledgeCode)
        }
        override fun launch(activity: Activity): Int {
            launches++
            if (throwOnLaunch) throw IllegalStateException("Simulated stale Activity")
            return BillingResponseCode.OK
        }
    }
}
