package dev.jsjh.timebox.billing

internal const val AdFreeProductId = "timebox_ad_free"
internal const val AdFreeBasePlanId = "monthly"
internal const val AdFreeCacheDurationMillis = 24L * 60 * 60 * 1_000

enum class BillingNotice {
    UNAVAILABLE, CANCELLED, PENDING, RESTORED, NOTHING_TO_RESTORE, PURCHASE_FAILED,
    VERIFICATION_FAILED, WINDOW_NOT_READY
}

data class AdFreeBillingState(
    val enabled: Boolean,
    val hasAccess: Boolean = false,
    val initialCheckComplete: Boolean = false,
    val refreshing: Boolean = false,
    val purchaseInProgress: Boolean = false,
    val awaitingConfirmation: Boolean = false,
    val formattedPrice: String? = null,
    val notice: BillingNotice? = null
) {
    val adsAllowed: Boolean get() = !enabled || (initialCheckComplete && !hasAccess)
    val canPurchase: Boolean get() = enabled && !hasAccess && !refreshing &&
        !purchaseInProgress && !awaitingConfirmation && formattedPrice != null
}

internal data class BillingPurchase(
    val token: String,
    val products: List<String>,
    val purchased: Boolean,
    val pending: Boolean,
    val acknowledged: Boolean
) {
    val isAdFree: Boolean get() = AdFreeProductId in products
}

internal fun cachedAccessIsValid(
    checkedAtMillis: Long,
    checkedAtElapsedMillis: Long,
    nowMillis: Long,
    nowElapsedMillis: Long
): Boolean = checkedAtMillis > 0 && checkedAtElapsedMillis >= 0 &&
    nowMillis - checkedAtMillis in 0 until AdFreeCacheDurationMillis &&
    nowElapsedMillis - checkedAtElapsedMillis in 0 until AdFreeCacheDurationMillis
