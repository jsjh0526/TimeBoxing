package dev.jsjh.timebox.feature.stats

import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.ViewGroup
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
import dev.jsjh.timebox.domain.model.StatisticsWeekStart
import dev.jsjh.timebox.domain.model.calculateWeeklyStatistics
import dev.jsjh.timebox.domain.model.statisticsWeek
import dev.jsjh.timebox.domain.repository.StatisticsRepository
import dev.jsjh.timebox.feature.root.AppBottomBar
import dev.jsjh.timebox.feature.root.AppTab
import dev.jsjh.timebox.feature.root.visibleAppTabs
import dev.jsjh.timebox.feature.settings.AppSettings
import dev.jsjh.timebox.feature.settings.StatisticsSettings
import java.io.File
import java.time.LocalDate
import java.util.Locale
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(qualifiers = "w360dp-h900dp")
class StatisticsScreenTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val today = LocalDate.of(2026, 10, 7)
    private val language = mutableStateOf("en")
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun weekNavigationRejectsFutureAndReturnsToThisWeek() {
        setRoute()
        compose.onNodeWithTag("stats_next_week").assertIsNotEnabled()
        compose.onNodeWithTag("stats_this_week").assertIsNotEnabled()
        compose.onNodeWithTag("stats_previous_week").performClick()
        compose.onNodeWithTag("stats_next_week").assertIsEnabled()
        compose.onNodeWithTag("stats_this_week").assertIsEnabled().performClick()
        compose.onNodeWithTag("stats_next_week").assertIsNotEnabled()
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
    }

    @Test fun emptyDataIsNotAnErrorAndFutureDaysHaveSeparateDescriptions() {
        setRoute(object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>) = dates.associateWith { emptyList<DailyTask>() }
        })
        compose.onNodeWithTag("stats_empty").assertIsDisplayed()
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("0/0")
        compose.onNodeWithTag("stats_error").assertDoesNotExist()
        compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_chart"))
        val future = compose.onNodeWithTag("stats_day_${today.plusDays(1)}").fetchSemanticsNode()
        assertTrue(future.config.toString().contains(context.getString(R.string.stats_future_day)))
        val empty = compose.onNodeWithTag("stats_day_$today").fetchSemanticsNode()
        assertTrue(empty.config.toString().contains(context.getString(R.string.stats_no_tasks_day)))
    }

    @Test fun failedLoadStaysAnErrorUntilRetrySucceeds() {
        var failing = true
        setRoute(object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
                if (failing) error("Read failed")
                return mapOf(today to listOf(task()))
            }
        })
        compose.onNodeWithTag("stats_error").assertIsDisplayed()
        compose.onNodeWithTag("stats_overall_count").assertDoesNotExist()
        compose.runOnIdle { failing = false }
        compose.onNodeWithTag("stats_retry").performClick()
        compose.onNodeWithTag("stats_error").assertDoesNotExist()
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
    }

    @Test fun cancelledSlowPreviousWeekCannotReplaceTheCurrentWeek() {
        val oldQuery = CompletableDeferred<Unit>()
        var requestedOldWeek = false
        val currentStart = statisticsWeek(today, StatisticsWeekStart.SUNDAY).start
        setRoute(object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
                if (dates.first() < currentStart) {
                    requestedOldWeek = true
                    withContext(NonCancellable) { oldQuery.await() }
                    return dates.associateWith { listOf(task(it)) }
                }
                return mapOf(today to listOf(task()))
            }
        })
        try {
            compose.onNodeWithTag("stats_previous_week").performClick()
            compose.onNodeWithTag("stats_loading").assertIsDisplayed()
            compose.runOnIdle { assertTrue(requestedOldWeek) }
            compose.onNodeWithTag("stats_next_week").performClick()
            compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
            compose.runOnIdle { oldQuery.complete(Unit) }
            compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
        } finally { oldQuery.complete(Unit) }
    }

    @Test fun dataRevisionWeekStartDateAndAccountChangesReloadAndStayIsolated() {
        val date = mutableStateOf(today)
        val start = mutableStateOf(StatisticsWeekStart.SUNDAY)
        val revision = mutableStateOf(0)
        val owner = mutableStateOf("guest")
        val calls = mutableListOf<List<LocalDate>>()
        var done = true
        val repository = object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
                calls += dates.toList()
                return if (owner.value == "guest") mapOf(date.value to listOf(task(date.value).copy(isCompleted = done))) else emptyMap()
            }
        }
        compose.setContent { StatisticsRoute(repository, owner.value, date.value, start.value, revision.value) }
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
        compose.runOnIdle { done = false; revision.value++ }
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("0/1")
        compose.runOnIdle { start.value = StatisticsWeekStart.MONDAY }
        compose.runOnIdle { assertEquals(LocalDate.of(2026, 10, 5), calls.last().first()) }
        compose.runOnIdle { date.value = today.plusDays(1) }
        compose.runOnIdle { assertEquals(today.plusDays(1), calls.last().last()) }
        compose.runOnIdle { owner.value = "other-account" }
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("0/0")
    }

    @Test fun obsoleteFailureCannotReplaceASuccessfulRefreshOfTheSameWeek() {
        val releaseOldQuery = CompletableDeferred<Unit>()
        val revision = mutableStateOf(0)
        var delayQuery = false
        val repository = object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>): Map<LocalDate, List<DailyTask>> {
                if (delayQuery) {
                    withContext(NonCancellable) { releaseOldQuery.await() }
                    error("Obsolete read failed")
                }
                return mapOf(today to listOf(task()))
            }
        }
        compose.setContent { StatisticsRoute(repository, "guest", today, StatisticsWeekStart.SUNDAY, revision.value) }
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
        try {
            compose.runOnIdle { delayQuery = true; revision.value++ }
            compose.onNodeWithTag("stats_loading").assertIsDisplayed()
            compose.runOnIdle { delayQuery = false; revision.value++ }
            compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
            compose.runOnIdle { releaseOldQuery.complete(Unit) }
            compose.onNodeWithTag("stats_error").assertDoesNotExist()
            compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
        } finally { releaseOldQuery.complete(Unit) }
    }

    @Test fun returningToForegroundReloadsTheStoredTasks() {
        var completed = true
        setRoute(object : StatisticsRepository {
            override suspend fun getTasksForDates(dates: Collection<LocalDate>) =
                mapOf(today to listOf(task().copy(isCompleted = completed)))
        })
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("1/1")
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        completed = false
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.onNodeWithTag("stats_overall_count").assertTextEquals("0/1")
    }

    @Test fun settingsToggleAndWeekSelectorPreserveOtherChoices() {
        val settings = mutableStateOf(AppSettings(dayStartHour = 3))
        compose.setContent { Column { StatisticsSettings(settings.value) { settings.value = it } } }
        compose.onNodeWithText(context.getString(R.string.settings_show_statistics)).performClick()
        compose.runOnIdle { assertFalse(settings.value.showStatistics) }
        compose.onNodeWithTag("settings_week_monday").performClick()
        compose.runOnIdle {
            assertEquals(StatisticsWeekStart.MONDAY, settings.value.statisticsWeekStart)
            assertEquals(3, settings.value.dayStartHour)
        }
    }

    @Test fun weekSelectorFitsLargeTextInEveryLocale() {
        compose.setContent {
            val localized = localizedContext()
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(LocalDensity.current.density, 1.5f),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Column(Modifier.size(320.dp, 600.dp)) { StatisticsSettings(AppSettings(), {}) }
            }
        }
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            listOf(R.string.stats_week_sunday, R.string.stats_week_monday).forEach { res ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(localizedContext().getString(res))
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTextFits("Week selector for $tag", layouts.single())
            }
        }
    }

    @Test fun allLocalesFitAt320dpWithLargeTextAndRtlAndProduceNonblankPreviews() {
        val week = statisticsWeek(today, StatisticsWeekStart.SUNDAY)
        val stats = calculateWeeklyStatistics(week, week.includedDates.associateWith { day ->
            listOf(task(day), task(day).copy(id = "pending", isCompleted = false, isBig3 = true, schedule = ScheduleBlock(600, 660)))
        })
        compose.setContent {
            val localized = localizedContext()
            val density = LocalDensity.current
            CompositionLocalProvider(
                LocalContext provides localized,
                LocalConfiguration provides localized.resources.configuration,
                LocalDensity provides Density(density.density, 1.5f),
                LocalLayoutDirection provides if (language.value == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr
            ) {
                Box(Modifier.size(320.dp, 800.dp)) {
                    Scaffold(bottomBar = { AppBottomBar(AppTab.STATS, {}, tabs = visibleAppTabs(true)) }) { padding ->
                        StatisticsScreen(week, StatisticsLoadState.Ready(stats), true, {}, {}, {}, {}, Modifier.fillMaxSize().padding(padding))
                    }
                }
            }
        }
        listOf("en", "ko", "es", "hi", "fil", "zu", "fa", "sn", "fr", "de").forEach { tag ->
            compose.runOnIdle { language.value = tag }
            if (tag in listOf("en", "ko", "fa")) savePreview(tag)
            AppTab.entries.forEach { tab ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("app_tab_${tab.name.lowercase()}").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTextFits("Nav label ${tab.name} for $tag", layouts.single())
            }
            val bounds = AppTab.entries.map { compose.onNodeWithTag("app_tab_${it.name.lowercase()}").fetchSemanticsNode().boundsInRoot }
            assertTrue("Overlapping nav tabs for $tag", bounds.sortedBy { it.left }.zipWithNext().all { (a, b) -> a.right <= b.left + 1f })
            compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_chart"))
            compose.onNodeWithTag("stats_chart").assertIsDisplayed()
            if (tag in listOf("en", "ko", "fa")) savePreview(tag)
            compose.onNodeWithTag("stats_list").performScrollToNode(hasTestTag("stats_planned_time"))
            val timeLayouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(localizedContext().getString(R.string.stats_planned_time)).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(timeLayouts) }
            assertTextFits("Metric label for $tag", timeLayouts.single())
        }
    }

    private fun assertTextFits(label: String, layout: TextLayoutResult) {
        // Paragraph canvas width can exceed the glyph width for single-line Text.
        val tooWide = (0 until layout.lineCount).any { line ->
            layout.getLineRight(line) - layout.getLineLeft(line) > layout.size.width + 1f
        }
        assertFalse(
            "$label: size=${layout.size}, lines=${layout.lineCount}, paragraphHeight=${layout.multiParagraph.height}",
            tooWide || layout.didOverflowHeight || layout.multiParagraph.didExceedMaxLines
        )
    }

    private fun setRoute(repository: StatisticsRepository = object : StatisticsRepository {
        override suspend fun getTasksForDates(dates: Collection<LocalDate>) = mapOf(dates.last() to listOf(task(dates.last())))
    }) { compose.setContent { StatisticsRoute(repository, "guest", today, StatisticsWeekStart.SUNDAY, 0) } }

    private fun task(date: LocalDate = today) = DailyTask("done", date = date, title = "Task", isCompleted = true)

    private fun localizedContext() = context.createConfigurationContext(Configuration(context.resources.configuration).apply { setLocale(Locale.forLanguageTag(language.value)) })

    private fun savePreview(locale: String) = compose.runOnIdle {
        val view = compose.activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0)
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        assertTrue("Blank preview for $locale", pixels.distinct().size > 10)
        val file = File("build/reports/statistics-$locale.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }
}
