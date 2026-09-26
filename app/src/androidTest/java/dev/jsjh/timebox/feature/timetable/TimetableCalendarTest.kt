package dev.jsjh.timebox.feature.timetable

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.jsjh.timebox.R
import java.time.LocalDate
import java.time.LocalTime
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TimetableCalendarTest {
    @get:Rule
    val compose = createComposeRule()

    private val today = LocalDate.of(2026, 9, 23)
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val addLabel get() = context.getString(R.string.calendar_add_for_date)

    @Test
    fun todayKeepsExistingTimelineButDateHeaderOpensCalendarWithAddButton() {
        setScreen(date = today)

        compose.onNodeWithText(addLabel).assertDoesNotExist()
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(addLabel).assertIsDisplayed()
    }

    @Test
    fun futureTimelineAndCalendarAddToTheirSelectedDate() {
        val future = LocalDate.of(2026, 10, 4)
        var addedDate: LocalDate? = null
        var selectedDate: LocalDate? = null
        setScreen(date = future, onAdd = { addedDate = it }, onSelect = { selectedDate = it })

        compose.onNodeWithText(addLabel).assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(future, addedDate) }

        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText("8").performScrollTo().performClick()
        compose.onNodeWithText(addLabel).assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(LocalDate.of(2026, 10, 8), addedDate)
            assertEquals(addedDate, selectedDate)
        }
    }

    @Test
    fun pastDatesHaveNoAddButtonInTimelineOrCalendar() {
        setScreen(date = today.minusDays(1))

        compose.onNodeWithText(addLabel).assertDoesNotExist()
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(addLabel).assertDoesNotExist()
    }

    @Test
    fun calendarAddButtonStaysVisibleAndStationaryWhenContentScrolls() {
        setScreen(date = today)
        compose.onNodeWithTag("timetable_date_picker").performClick()
        val before = compose.onNodeWithText(addLabel).getUnclippedBoundsInRoot()

        compose.onNodeWithTag("calendar_content").performTouchInput { swipeUp() }

        compose.onNodeWithText(addLabel).assertIsDisplayed()
        assertEquals(before, compose.onNodeWithText(addLabel).getUnclippedBoundsInRoot())
    }

    @Test
    fun weeklySummaryUsesCivilDateEvenBeforeDayStartAndWhileViewingAnotherMonth() {
        val sunday = LocalDate.of(2026, 9, 27)
        val requestedDates = mutableSetOf<LocalDate>()
        setScreen(
            date = LocalDate.of(2026, 12, 25),
            appToday = sunday.minusDays(1),
            calendarToday = sunday,
            stats = { dates ->
                requestedDates.addAll(dates)
                dates.associateWith { date -> if (date == sunday) 1 to 2 else 0 to 0 }
            }
        )

        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 1, 2)).assertIsDisplayed()
        compose.runOnIdle {
            assertTrue((0L..6L).all { sunday.plusDays(it) in requestedDates })
        }
    }

    @Test
    fun refreshedDataUpdatesSummaryEvenWhenVisibleTasksDoNotChange() {
        val revision = mutableStateOf(0)
        setScreen(
            date = today,
            refreshKey = { revision.value },
            stats = { dates -> dates.associateWith { if (it == today) (revision.value + 1) to 2 else 0 to 0 } }
        )
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 1, 2)).assertIsDisplayed()

        compose.runOnIdle { revision.value++ }

        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 2, 2)).assertIsDisplayed()
    }

    @Test
    fun slowStatsShowLoadingInsteadOfAnEmptyWeek() {
        val result = CompletableDeferred<Map<LocalDate, Pair<Int, Int>>>()
        setScreen(date = today, stats = { result.await() })
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(context.getString(R.string.calendar_week_loading)).assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 0, 0)).assertDoesNotExist()

        compose.runOnIdle { result.complete(calendarWeekDates(today).associateWith { 1 to 2 }) }

        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 7, 14)).assertIsDisplayed()
    }

    @Test
    fun failedStatsRecoverOnRefreshWithoutClosingCalendar() {
        val revision = mutableStateOf(0)
        setScreen(date = today, refreshKey = { revision.value }, stats = { dates ->
            if (revision.value == 0) error("Simulated read failure")
            dates.associateWith { 0 to 0 }
        })
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(context.getString(R.string.calendar_week_unavailable)).assertIsDisplayed()
        compose.onNodeWithText(addLabel).assertIsDisplayed()

        compose.runOnIdle { revision.value++ }

        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 0, 0)).assertIsDisplayed()
    }

    @Test
    fun staleStatsCannotOverwriteNewRepositoryResults() {
        val revision = mutableStateOf(0)
        val oldResult = CompletableDeferred<Map<LocalDate, Pair<Int, Int>>>()
        setScreen(date = today, refreshKey = { revision.value }, stats = { dates ->
            if (revision.value == 0) oldResult.await() else dates.associateWith { 1 to 2 }
        })
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText(context.getString(R.string.calendar_week_loading)).assertIsDisplayed()

        compose.runOnIdle { revision.value++ }
        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 7, 14)).assertIsDisplayed()
        compose.runOnIdle { oldResult.complete(calendarWeekDates(today).associateWith { 0 to 0 }) }

        compose.onNodeWithText(context.getString(R.string.calendar_week_summary, 7, 14)).assertIsDisplayed()
    }

    @Test
    fun calendarClosePreservesTimelineScroll() {
        setScreen(date = today)
        compose.onNode(hasScrollAction()).performTouchInput { swipeUp() }
        val before = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertTrue(before > 0f)

        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText("\u00D7").performClick()

        val after = compose.onNode(hasScrollAction()).fetchSemanticsNode()
            .config[SemanticsProperties.VerticalScrollAxisRange].value()
        assertEquals(before, after, 1f)
    }

    @Test
    fun selectedDateAndAddActionSurviveStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var addedDate: LocalDate? = null
        setScreen(date = LocalDate.of(2026, 10, 4), onAdd = { addedDate = it }, restoration = restoration)
        compose.onNodeWithTag("timetable_date_picker").performClick()
        compose.onNodeWithText("8").performScrollTo().performClick()

        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(addLabel).assertIsDisplayed().performClick()

        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 8), addedDate) }
    }

    @Test
    fun newLabelsFitSmallScreensWithLargeFontsInEverySupportedLanguage() {
        val language = mutableStateOf("en")
        setScreen(date = today, width = 320.dp, fontScale = 2f, language = { language.value })
        compose.onNodeWithTag("timetable_date_picker").performClick()

        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            val localized = context.createConfigurationContext(Configuration(context.resources.configuration).apply {
                setLocale(Locale.forLanguageTag(tag))
            })
            listOf(
                localized.getString(R.string.calendar_add_for_date),
                localized.getString(R.string.calendar_week_summary, 0, 0)
            ).forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text, useUnmergedTree = true)
                    .assertIsDisplayed()
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue("Missing text layout for $tag: $text", layouts.isNotEmpty())
                layouts.forEach {
                    assertFalse(
                        "Clipped text for $tag: $text; size=${it.size}, lines=${it.lineCount}, " +
                            "width=${it.didOverflowWidth}, height=${it.didOverflowHeight}, " +
                            "constraints=${it.layoutInput.constraints}, paragraph=${it.multiParagraph.width} x ${it.multiParagraph.height}",
                        it.hasVisualOverflow
                    )
                }
            }
        }
    }

    private fun setScreen(
        date: LocalDate,
        appToday: LocalDate = today,
        calendarToday: LocalDate = today,
        onAdd: (LocalDate) -> Unit = {},
        onSelect: (LocalDate) -> Unit = {},
        refreshKey: () -> Any? = { Unit },
        stats: suspend (List<LocalDate>) -> Map<LocalDate, Pair<Int, Int>> = { dates -> dates.associateWith { 0 to 0 } },
        width: Dp = 360.dp,
        fontScale: Float = 1f,
        language: () -> String = { "en" },
        restoration: StateRestorationTester? = null
    ) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            val configuration = Configuration(LocalConfiguration.current).apply {
                setLocale(Locale.forLanguageTag(language()))
                this.fontScale = fontScale
            }
            val localized = LocalContext.current.createConfigurationContext(configuration)
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides configuration,
                LocalDensity provides Density(LocalDensity.current.density, fontScale),
                LocalLayoutDirection provides if (language() == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.size(width = width, height = 600.dp)) {
                    TimetableScreen(
                        modifier = Modifier.fillMaxSize(),
                        tasks = emptyList(),
                        date = date,
                        currentTime = LocalTime.of(1, 30),
                        showCurrentTime = date == appToday,
                        onPreviousDay = {},
                        onNextDay = {},
                        onToday = {},
                        today = appToday,
                        calendarToday = calendarToday,
                        calendarStatsRefreshKey = refreshKey(),
                        calendarStatsForDates = { stats(it) },
                        onSelectDate = onSelect,
                        onAddTaskForDate = onAdd,
                        onOpenTask = {},
                        onToggleComplete = {},
                        onMoveToUnscheduled = {},
                        onUpdateSchedule = { _, _ -> },
                        onAddTask = {}
                    )
                }
            }
        }
        if (restoration != null) restoration.setContent(content) else compose.setContent(content)
    }
}
