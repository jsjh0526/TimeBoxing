package dev.jsjh.timebox.feature.root

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.StateRestorationTester
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
import dev.jsjh.timebox.feature.settings.AppSurveys
import dev.jsjh.timebox.feature.settings.SurveySettingsSection
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowDialog

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w320dp-h800dp")
class AppAnnouncementDialogTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val announcement = CurrentAppAnnouncement.value
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @After fun resetFontScale() { RuntimeEnvironment.setFontScale(1f) }

    @Test fun firstThreeDisplaysAreDismissibleWithoutSuppressionCheckbox() {
        val count = mutableStateOf(1)
        val dismissals = mutableListOf<Pair<String, Boolean>>()
        compose.setContent { AppAnnouncementDialog(announcement, count.value, { true }) { source, suppressed -> dismissals += source to suppressed } }
        (1..3).forEach {
            compose.runOnIdle { count.value = it }
            compose.onNodeWithTag("announcement_suppress").assertDoesNotExist()
            compose.onNodeWithTag("announcement_close").assertIsDisplayed().performClick()
        }
        assertEquals(List(3) { "confirm" to false }, dismissals)
    }

    @Test fun fourthDisplayCheckboxDefaultsOffAndCanSuppressOnClose() {
        var dismissed: Pair<String, Boolean>? = null
        compose.setContent { AppAnnouncementDialog(announcement, 4, { true }) { source, suppressed -> dismissed = source to suppressed } }
        compose.onNodeWithTag("announcement_suppress").performScrollTo().assertIsOff().performClick().assertIsOn()
        compose.onNodeWithTag("announcement_close").performClick()
        assertEquals("confirm" to true, dismissed)
    }

    @Test fun closingUncheckedDoesNotSuppressDailyDisplays() {
        var suppressed = true
        compose.setContent { AppAnnouncementDialog(announcement, 4, { true }) { _, value -> suppressed = value } }
        compose.onNodeWithTag("announcement_close").performClick()
        assertFalse(suppressed)
    }

    @Test fun checkboxChoiceSurvivesStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppAnnouncementDialog(announcement, 4, { true }) { _, _ -> } }
        compose.onNodeWithTag("announcement_suppress").performScrollTo().performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("announcement_suppress").performScrollTo().assertIsOn()
    }

    @Test fun aNewRevisionDoesNotRestoreThePreviousCheckboxChoice() {
        val current = mutableStateOf(announcement)
        val restoration = StateRestorationTester(compose)
        restoration.setContent { AppAnnouncementDialog(current.value, 4, { true }) { _, _ -> } }
        compose.onNodeWithTag("announcement_suppress").performScrollTo().performClick().assertIsOn()
        compose.runOnIdle { current.value = announcement.copy(revision = announcement.revision + 1) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("announcement_suppress").performScrollTo().assertIsOff()
    }

    @Test fun failedSurveyOpeningLeavesTheDialogDismissible() {
        var opened = false
        var dismissed: String? = null
        compose.setContent { AppAnnouncementDialog(announcement, 1, { opened }) { source, _ -> dismissed = source } }
        compose.onNodeWithTag("announcement_survey").performClick()
        assertEquals(null, dismissed)
        compose.onNodeWithTag("announcement_close").assertIsDisplayed()
        compose.runOnIdle { opened = true }
        compose.onNodeWithTag("announcement_survey").performClick()
        assertEquals("survey", dismissed)
    }

    @Test fun settingsKeepsBothSurveysAndUsesTheApprovedNewUrl() {
        val urls = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { urls += uri }
            }) { SurveySettingsSection() }
        }
        compose.onNodeWithTag("settings_feature_survey").performClick()
        compose.onNodeWithTag("settings_language_survey").performClick()
        assertEquals(listOf("https://forms.gle/aNrqJNmCrr2qaVr29", "https://forms.gle/3GKB9VFp9btoddve6"), urls)
        assertFalse(AppSurveys.open(AppSurveys.FEATURES_URL) { throw IllegalArgumentException("No browser") })
    }

    @Test fun allLocalesFitWithLargeTextAndRtlAndCloseRemainsVisible() {
        val language = mutableStateOf("en")
        compose.runOnIdle { RuntimeEnvironment.setFontScale(1.5f) }
        compose.setContent {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(language.value))
            })
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Column(Modifier.size(320.dp, 800.dp).padding(horizontal = 20.dp)) { SurveySettingsSection() }
                AppAnnouncementDialog(announcement, 4, { true }) { _, _ -> }
            }
        }
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            compose.onNodeWithTag("announcement_close").assertIsDisplayed()
            listOf("announcement_title", "announcement_message").forEach { nodeTag -> assertFits(nodeTag, compose.onNodeWithTag(nodeTag)) }
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            assertFits("survey button $tag", compose.onNodeWithText(localized.getString(R.string.announcement_take_survey)))
            compose.onNodeWithTag("announcement_suppress").performScrollTo().assertIsDisplayed()
            assertFits("suppression $tag", compose.onNodeWithText(localized.getString(R.string.announcement_do_not_show_again)))
            if (tag in listOf("ko", "en", "fa")) saveDialogPreview(tag)
        }
    }

    @Test fun surveyRowsWrapInAllLocalesAt320dpWithLargeText() {
        val language = mutableStateOf("en")
        compose.setContent {
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.value)) })
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) { Column(Modifier.size(320.dp, 800.dp).padding(horizontal = 20.dp)) { SurveySettingsSection() } }
        }
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) })
            listOf(R.string.settings_feature_survey_title, R.string.settings_feature_survey_subtitle, R.string.settings_language_survey_title, R.string.settings_language_survey_subtitle).forEach { res ->
                assertFits("Survey row $res in $tag", compose.onNodeWithText(localized.getString(res)))
            }
        }
    }

    private fun assertFits(label: String, node: androidx.compose.ui.test.SemanticsNodeInteraction) {
        val layouts = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        val layout = layouts.single()
        val tooWide = (0 until layout.lineCount).any { layout.getLineRight(it) - layout.getLineLeft(it) > layout.size.width + 1f }
        assertFalse("Overflow in $label", tooWide || layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines)
    }

    private fun saveDialogPreview(tag: String) = compose.runOnIdle {
        val view = requireNotNull(ShadowDialog.getLatestDialog()?.window?.decorView)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Blank dialog preview", pixels.distinct().size > 10)
        val file = File("build/reports/announcement-$tag.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
