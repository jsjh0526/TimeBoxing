package dev.jsjh.timebox.feature.settings

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jsjh.timebox.R
import dev.jsjh.timebox.billing.AdFreeBillingState
import dev.jsjh.timebox.billing.BillingNotice
import java.io.File
import java.util.Locale
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h1000dp")
class AdFreeSubscriptionSectionTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val language = mutableStateOf("en")
    private val state = mutableStateOf(AdFreeBillingState(enabled = true,
        initialCheckComplete = true, formattedPrice = "₩3,000"))
    private var purchases = 0
    private var restores = 0
    private var manages = 0
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun purchaseRequiresAnExplicitTestPaymentWarningAndCanBeCancelled() {
        showSection()
        compose.onNodeWithTag("subscription_purchase").performClick()
        compose.onNodeWithText(context.getString(R.string.billing_test_warning)).assertIsDisplayed()
        assertEquals(0, purchases)
        compose.onNodeWithText(context.getString(R.string.billing_cancel)).performClick()
        assertEquals(0, purchases)
        compose.onNodeWithTag("subscription_purchase").performClick()
        compose.onNodeWithText(context.getString(R.string.billing_continue)).performClick()
        assertEquals(1, purchases)
    }

    @Test fun activeSubscriptionHasManagementAndRestoreButNoSecondPurchase() {
        state.value = state.value.copy(hasAccess = true)
        showSection()
        compose.onNodeWithTag("subscription_active").assertIsDisplayed()
        compose.onNodeWithTag("subscription_purchase").assertDoesNotExist()
        compose.onNodeWithTag("subscription_restore").performScrollTo().performClick()
        compose.onNodeWithTag("subscription_manage").performScrollTo().performClick()
        assertEquals(1, restores)
        assertEquals(1, manages)
    }

    @Test fun missingProductOrPendingFlowCannotLaunchPurchase() {
        state.value = state.value.copy(formattedPrice = null, notice = BillingNotice.UNAVAILABLE)
        showSection()
        compose.onNodeWithTag("subscription_purchase").assertIsNotEnabled()
        compose.runOnIdle { state.value = state.value.copy(formattedPrice = "₹99.00", purchaseInProgress = true) }
        compose.onNodeWithTag("subscription_purchase").assertIsNotEnabled()
        compose.onNodeWithTag("subscription_restore").assertIsNotEnabled()
    }

    @Test fun everyLocaleFitsSmallWidthAndLargeFontsWithoutClippedLabels() {
        showSection(fontScale = 2f)
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            val localized = localizedContext()
            listOf(R.string.billing_title, R.string.billing_subscribe, R.string.billing_restore,
                R.string.billing_manage, R.string.billing_benefits, R.string.billing_renewal).forEach { id ->
                val node = compose.onNodeWithText(localized.getString(id))
                node.performScrollTo()
                val layouts = mutableListOf<TextLayoutResult>()
                node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Missing layout for $tag/$id", layouts.isNotEmpty())
                if (layouts.single().hasVisualOverflow) savePreview("$tag-overflow-$id")
                assertFalse("Clipped text for $tag/$id (${localized.getString(id)}): ${layouts.single().size}", layouts.single().hasVisualOverflow)
                val bounds = node.fetchSemanticsNode().boundsInRoot
                assertTrue("Text exceeds width for $tag/$id", bounds.left >= -1 && bounds.right <= 361)
            }
            if (tag in listOf("en", "ko", "fa")) {
                compose.onNodeWithTag("subscription_purchase").performScrollTo()
                savePreview(tag)
            }
        }
    }

    @Test fun widgetSubscriptionViewDoesNotOfferRewardedAds() {
        compose.setContent { SubscriptionWidgetAccessSection() }
        compose.onNodeWithTag("subscription_widget_unlimited").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.settings_widget_unlock_extend)).assertDoesNotExist()
        compose.onNodeWithText(context.getString(R.string.settings_widget_unlock_open)).assertDoesNotExist()
    }

    private fun localizedContext() = context.createConfigurationContext(
        Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.value)) }
    )

    private fun showSection(fontScale: Float = 1f) {
        compose.setContent {
            val localized = localizedContext()
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Column(Modifier.size(320.dp, 850.dp).background(Color(0xFF121212))
                    .verticalScroll(rememberScrollState()).padding(12.dp)) {
                    AdFreeSubscriptionSection(state.value, { purchases++ }, { restores++ }, { manages++ })
                }
            }
        }
    }

    private fun savePreview(tag: String) {
        compose.runOnIdle {
            val view = compose.activity.findViewById<ViewGroup>(android.R.id.content)
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            val colors = mutableSetOf<Int>()
            for (y in 0 until bitmap.height step 17) for (x in 0 until bitmap.width step 17)
                colors += bitmap.getPixel(x, y)
            assertTrue("Blank subscription preview", colors.size > 5)
            val output = File("build/qa/subscription-$tag.png")
            output.parentFile!!.mkdirs()
            output.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
