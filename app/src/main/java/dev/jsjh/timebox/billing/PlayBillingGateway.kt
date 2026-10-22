package dev.jsjh.timebox.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.BillingFlowParams

internal interface BillingGateway {
    var onPurchasesChanged: (Int, Boolean) -> Unit
    fun connect(callback: (Int) -> Unit)
    fun queryPurchases(callback: (Int, List<BillingPurchase>) -> Unit)
    fun queryPrice(callback: (String?) -> Unit)
    fun acknowledge(token: String, callback: (Int) -> Unit)
    fun launch(activity: Activity): Int
}

internal class PlayBillingGateway(context: Context) : BillingGateway {
    override var onPurchasesChanged: (Int, Boolean) -> Unit = { _, _ -> }
    private var product: ProductDetails? = null
    private var offerToken: String? = null
    private var priceGeneration = 0
    private val handler = Handler(Looper.getMainLooper())
    private val client = BillingClient.newBuilder(context.applicationContext)
        .setListener { result, purchases ->
            onPurchasesChanged(result.responseCode, purchases.orEmpty().any {
                AdFreeProductId in it.products && it.purchaseState == Purchase.PurchaseState.PENDING
            })
        }
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    override fun connect(callback: (Int) -> Unit) {
        if (client.isReady) {
            callback(BillingClient.BillingResponseCode.OK)
            return
        }
        client.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) = callback(result.responseCode)
            override fun onBillingServiceDisconnected() = Unit
        })
    }

    override fun queryPurchases(callback: (Int, List<BillingPurchase>) -> Unit) {
        client.queryPurchasesAsync(
            QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.SUBS).build()
        ) { result, purchases ->
            callback(result.responseCode, purchases.map {
                BillingPurchase(it.purchaseToken, it.products,
                    it.purchaseState == Purchase.PurchaseState.PURCHASED,
                    it.purchaseState == Purchase.PurchaseState.PENDING, it.isAcknowledged)
            })
        }
    }

    override fun queryPrice(callback: (String?) -> Unit) {
        val generation = ++priceGeneration
        val params = QueryProductDetailsParams.newBuilder().setProductList(listOf(
            QueryProductDetailsParams.Product.newBuilder().setProductId(AdFreeProductId)
                .setProductType(BillingClient.ProductType.SUBS).build()
        )).build()
        client.queryProductDetailsAsync(params) { result, details ->
            handler.post {
                if (generation != priceGeneration) return@post
                val candidate = details.productDetailsList.firstOrNull { it.productId == AdFreeProductId }
                val offer = candidate?.subscriptionOfferDetails?.firstOrNull {
                    it.basePlanId == AdFreeBasePlanId && it.offerId == null &&
                        it.pricingPhases.pricingPhaseList.singleOrNull()?.let { phase ->
                            phase.billingPeriod == "P1M" && phase.priceAmountMicros > 0 &&
                                phase.recurrenceMode == ProductDetails.RecurrenceMode.INFINITE_RECURRING
                        } == true
                }
                if (result.responseCode == BillingClient.BillingResponseCode.OK && offer != null) {
                    product = candidate
                    offerToken = offer.offerToken
                    callback(offer.pricingPhases.pricingPhaseList.single().formattedPrice)
                } else {
                    product = null
                    offerToken = null
                    callback(null)
                }
            }
        }
    }

    override fun acknowledge(token: String, callback: (Int) -> Unit) {
        client.acknowledgePurchase(
            AcknowledgePurchaseParams.newBuilder().setPurchaseToken(token).build()
        ) { callback(it.responseCode) }
    }

    override fun launch(activity: Activity): Int {
        val currentProduct = product ?: return BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
        val token = offerToken ?: return BillingClient.BillingResponseCode.ITEM_UNAVAILABLE
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(currentProduct).setOfferToken(token).build()
        return client.launchBillingFlow(activity,
            BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build()
        ).responseCode
    }
}
