package dev.jsjh.timebox.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.text.BidiFormatter
import dev.jsjh.timebox.R
import dev.jsjh.timebox.billing.AdFreeBillingState
import dev.jsjh.timebox.billing.BillingNotice

private val SubscriptionAccent = Color(0xFF8687E7)

@Composable
internal fun AdFreeSubscriptionSection(
    state: AdFreeBillingState,
    onPurchase: () -> Unit,
    onRestore: () -> Unit,
    onManage: () -> Unit
) {
    var confirmPurchase by remember { mutableStateOf(false) }
    SectionCard(stringResource(R.string.billing_title), icon = {
        Icon(Icons.Filled.Block, null, tint = SubscriptionAccent, modifier = Modifier.size(18.dp))
    }) {
        Column(Modifier.testTag("subscription_section"), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.billing_benefits), color = Color.White, fontSize = 14.sp)
            if (state.hasAccess) {
                Text(stringResource(R.string.billing_active), color = Color(0xFF7AE582),
                    modifier = Modifier.testTag("subscription_active"))
            } else {
                state.formattedPrice?.let {
                    val price = BidiFormatter.getInstance(LocalLayoutDirection.current == LayoutDirection.Rtl).unicodeWrap(it)
                    Text(stringResource(R.string.billing_price_month, price), color = Color.White, fontSize = 20.sp)
                }
                if (state.refreshing) {
                    Text(stringResource(R.string.billing_checking), color = Color(0xFF9AA1AF))
                }
                Button(
                    onClick = { confirmPurchase = true }, enabled = state.canPurchase,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SubscriptionAccent),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 46.dp).testTag("subscription_purchase")
                ) {
                    Text(stringResource(R.string.billing_subscribe), textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth())
                }
            }
            state.notice?.let {
                Text(stringResource(billingNoticeResource(it)), fontSize = 13.sp,
                    color = Color(0xFFB5BDCC), modifier = Modifier.testTag("subscription_notice"))
            }
            Text(stringResource(R.string.billing_renewal), color = Color(0xFF9AA1AF), fontSize = 12.sp)
            TextButton(onClick = onRestore, enabled = !state.refreshing && !state.purchaseInProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = SubscriptionAccent),
                modifier = Modifier.fillMaxWidth().testTag("subscription_restore")) {
                Text(stringResource(R.string.billing_restore), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onManage, enabled = !state.purchaseInProgress,
                colors = ButtonDefaults.textButtonColors(contentColor = SubscriptionAccent),
                modifier = Modifier.fillMaxWidth().testTag("subscription_manage")) {
                Text(stringResource(R.string.billing_manage), textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth())
            }
        }
    }
    if (confirmPurchase) {
        AlertDialog(
            onDismissRequest = { confirmPurchase = false },
            title = { Text(stringResource(R.string.billing_test_title)) },
            text = { Text(stringResource(R.string.billing_test_warning)) },
            confirmButton = {
                TextButton(onClick = { confirmPurchase = false; if (state.canPurchase) onPurchase() }) {
                    Text(stringResource(R.string.billing_continue))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmPurchase = false }) { Text(stringResource(R.string.billing_cancel)) }
            }
        )
    }
}

@Composable
internal fun SubscriptionWidgetAccessSection() {
    SectionCard(stringResource(R.string.settings_widget_title), icon = {
        Icon(Icons.Filled.Widgets, null, tint = SubscriptionAccent, modifier = Modifier.size(18.dp))
    }) {
        Text(stringResource(R.string.billing_widget_active), color = Color(0xFF7AE582),
            modifier = Modifier.testTag("subscription_widget_unlimited"))
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.billing_widget_detail), color = Color(0xFF9AA1AF), fontSize = 13.sp)
    }
}

internal fun billingNoticeResource(notice: BillingNotice): Int = when (notice) {
    BillingNotice.UNAVAILABLE -> R.string.billing_unavailable
    BillingNotice.CANCELLED -> R.string.billing_cancelled
    BillingNotice.PENDING -> R.string.billing_pending
    BillingNotice.RESTORED -> R.string.billing_restored
    BillingNotice.NOTHING_TO_RESTORE -> R.string.billing_nothing_to_restore
    BillingNotice.PURCHASE_FAILED -> R.string.billing_failed
    BillingNotice.VERIFICATION_FAILED -> R.string.billing_verification_failed
    BillingNotice.WINDOW_NOT_READY -> R.string.billing_window_not_ready
}
