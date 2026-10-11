package dev.jsjh.timebox.feature.stats

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.ConfigurationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.CompletionStatistics
import dev.jsjh.timebox.domain.model.DailyStatistics
import dev.jsjh.timebox.domain.model.StatisticsWeek
import dev.jsjh.timebox.domain.model.StatisticsWeekStart
import dev.jsjh.timebox.domain.model.WeeklyStatistics
import dev.jsjh.timebox.domain.model.WeeklyStatisticsComparison
import dev.jsjh.timebox.domain.model.calculateWeeklyStatistics
import dev.jsjh.timebox.domain.model.statisticsComparisonWeek
import dev.jsjh.timebox.domain.model.statisticsWeek
import dev.jsjh.timebox.domain.repository.StatisticsRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.time.format.TextStyle as DateTextStyle
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

private val Background = Color(0xFF121212)
private val Primary = Color(0xFFF3F4F6)
private val Secondary = Color(0xFF99A1AF)
private val Divider = Color(0xFF303030)
private val Accent = Color(0xFF8687E7)
private val Completed = Color(0xFF79CE8E)
private val Decreased = Color(0xFFFF9E92)
private val Pending = Color(0xFF535965)

internal sealed interface StatisticsLoadState {
    data object Loading : StatisticsLoadState
    data object Error : StatisticsLoadState
    data class Ready(val statistics: WeeklyStatistics, val comparison: WeeklyStatisticsComparison? = null) : StatisticsLoadState
}

@Composable
fun StatisticsRoute(
    repository: StatisticsRepository,
    ownerKey: String,
    today: LocalDate,
    weekStart: StatisticsWeekStart,
    refreshKey: Any,
    modifier: Modifier = Modifier
) {
    var offset by rememberSaveable(ownerKey) { mutableIntStateOf(0) }
    val week = remember(today, weekStart, offset) { statisticsWeek(today, weekStart, offset) }
    var attempt by remember { mutableIntStateOf(0) }
    var foregroundRevision by remember { mutableIntStateOf(0) }
    var state by remember(ownerKey, repository, week) { mutableStateOf<StatisticsLoadState>(StatisticsLoadState.Loading) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foregroundRevision++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(ownerKey, repository, week, refreshKey, attempt, foregroundRevision) {
        state = StatisticsLoadState.Loading
        state = try {
            val previousWeek = statisticsComparisonWeek(week)
            val tasks = repository.getTasksForDates(previousWeek.includedDates + week.includedDates)
            // A cancelled or obsolete query must never publish into the current screen.
            currentCoroutineContext().ensureActive()
            val statistics = calculateWeeklyStatistics(week, tasks)
            val previous = calculateWeeklyStatistics(previousWeek, tasks)
            StatisticsLoadState.Ready(statistics, WeeklyStatisticsComparison(statistics, previous))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            currentCoroutineContext().ensureActive()
            StatisticsLoadState.Error
        }
    }
    StatisticsScreen(
        modifier = modifier,
        week = week,
        state = state,
        isCurrentWeek = offset == 0,
        onPreviousWeek = { offset-- },
        onNextWeek = { if (offset < 0) offset++ },
        onThisWeek = { offset = 0 },
        onRetry = { attempt++ }
    )
}

@Composable
internal fun StatisticsScreen(
    week: StatisticsWeek,
    state: StatisticsLoadState,
    isCurrentWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    val locale = ConfigurationCompat.getLocales(LocalConfiguration.current).get(0) ?: Locale.ENGLISH
    val dateFormatter = remember(locale) { DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale) }
    LazyColumn(
        modifier = modifier.fillMaxSize().background(Background).testTag("stats_list"),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.stats_title), Modifier.weight(1f), style = textStyle(28, FontWeight.Bold))
                IconButton(onClick = onThisWeek, enabled = !isCurrentWeek, modifier = Modifier.testTag("stats_this_week")) {
                    Icon(Icons.Filled.CalendarToday, stringResource(R.string.stats_this_week), tint = if (isCurrentWeek) Secondary else Accent)
                }
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPreviousWeek, modifier = Modifier.testTag("stats_previous_week")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.stats_previous_week), tint = Primary)
                }
                Text(
                    "${week.start.format(dateFormatter)}\n${week.end.format(dateFormatter)}",
                    modifier = Modifier.weight(1f).testTag("stats_week_range"),
                    style = textStyle(14, FontWeight.Medium), textAlign = TextAlign.Center
                )
                IconButton(onClick = onNextWeek, enabled = !isCurrentWeek, modifier = Modifier.testTag("stats_next_week")) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, stringResource(R.string.stats_next_week), tint = if (isCurrentWeek) Secondary else Primary)
                }
            }
        }
        when (state) {
            StatisticsLoadState.Loading -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 48.dp).testTag("stats_loading"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator(color = Accent, modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.stats_loading), style = textStyle(14, color = Secondary))
                }
            }
            StatisticsLoadState.Error -> item {
                Column(Modifier.fillMaxWidth().padding(vertical = 32.dp).testTag("stats_error"), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.stats_load_error), style = textStyle(14), textAlign = TextAlign.Center)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("stats_retry")) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = Accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.stats_retry), style = textStyle(14, color = Accent))
                    }
                }
            }
            is StatisticsLoadState.Ready -> {
                val stats = state.statistics
                val comparison = state.comparison
                item {
                    Text(stringResource(R.string.stats_overall), style = textStyle(14, color = Secondary))
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        Text(countsLabel(stats.overall), Modifier.weight(1f).testTag("stats_overall_count"), style = textStyle(36, FontWeight.SemiBold))
                        Text(percentageLabel(stats.overall), style = textStyle(22, FontWeight.Medium, Completed))
                    }
                    Spacer(Modifier.height(14.dp))
                    LinearProgressIndicator(
                        progress = { if (stats.overall.total == 0) 0f else stats.overall.completed.toFloat() / stats.overall.total },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Completed, trackColor = Divider
                    )
                    if (comparison != null) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            stringResource(
                                R.string.stats_comparison_period,
                                comparison.previous.week.start.format(dateFormatter),
                                comparison.previous.week.through.format(dateFormatter)
                            ),
                            modifier = Modifier.fillMaxWidth().testTag("stats_comparison_period"),
                            style = textStyle(12, color = Secondary)
                        )
                        Spacer(Modifier.height(6.dp))
                        RateComparison(comparison.overallPercentagePointChange, "stats_overall_comparison")
                    }
                    if (stats.overall.total == 0) {
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.stats_no_tasks), Modifier.testTag("stats_empty"), style = textStyle(13, color = Secondary))
                    }
                }
                item { DailyChart(stats, locale, dateFormatter) }
                item {
                    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(18.dp)) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            CompletionRow(stringResource(R.string.stats_big3), stats.big3, "stats_big3")
                            if (comparison != null) RateComparison(comparison.big3PercentagePointChange, "stats_big3_comparison")
                        }
                        StatsDivider()
                        CompletionRow(stringResource(R.string.stats_scheduled), stats.scheduled, "stats_scheduled")
                        StatsDivider()
                        MetricRow(stringResource(R.string.stats_planned_time), durationLabel(stats.plannedMinutes), "stats_planned_time")
                        MetricRow(stringResource(R.string.stats_completed_time), durationLabel(stats.completedPlannedMinutes), "stats_completed_time")
                        StatsDivider()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            MetricRow(stringResource(R.string.stats_carried_over), String.format(locale, "%d", stats.carriedOverCount), "stats_carried_over")
                            if (comparison != null) CountComparison(comparison.carriedOverChange)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyChart(stats: WeeklyStatistics, locale: Locale, dateFormatter: DateTimeFormatter) {
    val days = stats.days.associateBy { it.date }
    val maxTasks = maxOf(1, stats.days.maxOfOrNull { it.overall.total } ?: 0)
    Column(Modifier.fillMaxWidth().testTag("stats_chart"), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(stringResource(R.string.stats_daily), style = textStyle(16, FontWeight.SemiBold))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ChartLegend(Completed, stringResource(R.string.stats_completed))
            ChartLegend(Pending, stringResource(R.string.stats_pending))
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
            stats.week.dates.forEach { date ->
                ChartDay(date, days[date], maxTasks, locale, dateFormatter, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ChartDay(date: LocalDate, stats: DailyStatistics?, maxTasks: Int, locale: Locale, dateFormatter: DateTimeFormatter, modifier: Modifier) {
    val description = when {
        stats == null -> "${date.format(dateFormatter)}: ${stringResource(R.string.stats_future_day)}"
        stats.overall.total == 0 -> "${date.format(dateFormatter)}: ${stringResource(R.string.stats_no_tasks_day)}"
        else -> stringResource(R.string.stats_chart_day, date.format(dateFormatter), stats.overall.completed, stats.overall.total)
    }
    val barHeight = 140f * (stats?.overall?.total ?: 0) / maxTasks
    val completedHeight = if (stats == null || stats.overall.total == 0) 0f else barHeight * stats.overall.completed / stats.overall.total
    val color = if (stats == null) Secondary.copy(alpha = 0.4f) else Secondary
    Column(modifier.testTag("stats_day_$date").semantics(mergeDescendants = true) { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.fillMaxWidth().height(170.dp), contentAlignment = Alignment.BottomCenter) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(if (stats == null) "\u2014" else String.format(locale, "%d", stats.overall.completed), style = textStyle(11, color = color))
                Spacer(Modifier.height(6.dp))
                if (barHeight == 0f) {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(if (stats == null) Divider.copy(alpha = 0.5f) else Pending))
                } else {
                    Column(Modifier.fillMaxWidth().height(barHeight.dp).clip(RoundedCornerShape(3.dp))) {
                        Box(Modifier.fillMaxWidth().height((barHeight - completedHeight).dp).background(Pending))
                        Box(Modifier.fillMaxWidth().height(completedHeight.dp).background(Completed))
                    }
                }
            }
        }
        Text(date.dayOfWeek.getDisplayName(DateTextStyle.NARROW, locale), style = textStyle(12, color = color), textAlign = TextAlign.Center)
        Text(String.format(locale, "%d", date.dayOfMonth), style = textStyle(11, color = color), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChartLegend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).clip(RoundedCornerShape(2.dp)).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = textStyle(12, color = Secondary))
    }
}

@Composable
private fun CompletionRow(label: String, stats: CompletionStatistics, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = textStyle(14, color = Secondary))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(countsLabel(stats), style = textStyle(20, FontWeight.SemiBold))
            Text(percentageLabel(stats), style = textStyle(16, color = Accent))
        }
    }
}

@Composable
private fun MetricRow(label: String, value: String, tag: String) {
    Column(Modifier.fillMaxWidth().testTag(tag), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, style = textStyle(14, color = Secondary))
        Text(value, style = textStyle(20, FontWeight.SemiBold))
    }
}

@Composable
private fun RateComparison(change: Int?, tag: String) {
    val label = when {
        change == null -> stringResource(R.string.stats_comparison_unavailable)
        change > 0 -> stringResource(R.string.stats_comparison_rate_up, change)
        change < 0 -> stringResource(R.string.stats_comparison_rate_down, -change)
        else -> stringResource(R.string.stats_comparison_same)
    }
    val color = when {
        change == null || change == 0 -> Secondary
        change > 0 -> Completed
        else -> Decreased
    }
    Text(label, Modifier.fillMaxWidth().testTag(tag), style = textStyle(13, color = color))
}

@Composable
private fun CountComparison(change: Int?) {
    val label = when {
        change == null -> stringResource(R.string.stats_comparison_unavailable)
        change > 0 -> stringResource(R.string.stats_comparison_count_more, change)
        change < 0 -> stringResource(R.string.stats_comparison_count_fewer, -change)
        else -> stringResource(R.string.stats_comparison_same)
    }
    Text(label, Modifier.fillMaxWidth().testTag("stats_carried_over_comparison"), style = textStyle(13, color = Secondary))
}

@Composable
private fun StatsDivider() { Box(Modifier.fillMaxWidth().height(1.dp).background(Divider)) }

@Composable
private fun countsLabel(stats: CompletionStatistics) = stringResource(R.string.stats_counts, stats.completed, stats.total)

@Composable
private fun percentageLabel(stats: CompletionStatistics) = stats.percentage?.let { stringResource(R.string.stats_percentage, it) } ?: "\u2014"

@Composable
private fun durationLabel(minutes: Long) = if (minutes < 60) stringResource(R.string.stats_minutes, minutes)
    else stringResource(R.string.stats_hours_minutes, minutes / 60, minutes % 60)

private fun textStyle(size: Int, weight: FontWeight = FontWeight.Normal, color: Color = Primary) =
    TextStyle(fontSize = size.sp, lineHeight = (size * 1.4f).sp, fontWeight = weight, color = color)
