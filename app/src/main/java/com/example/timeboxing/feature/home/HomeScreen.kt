package com.example.timeboxing.feature.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.DailyTaskSource
import com.example.timeboxing.domain.model.ScheduleBlock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay

private val ScreenBackground = Color(0xFF121212)
private val CardBackground   = Color(0xFF1E1E1E)
private val CardBorder       = Color(0xFF2A2A2A)
private val CardInner        = Color(0xFF121212)
private val Accent           = Color(0xFF8687E7)
private val AccentDark       = Color(0xFF6B6BC5)
private val Muted            = Color(0xFF6A7282)
private val Secondary        = Color(0xFF99A1AF)
private val Tertiary         = Color(0xFF4A5565)
private val Priority         = Color(0xFFFF9680)
private val Big3CheckboxRing = Color(0xFF4A5565)
private val TagBackground    = Color(0xFF444444)

@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    tasks: List<DailyTask>,
    date: LocalDate,
    currentTime: LocalTime,
    onOpenTimetable: () -> Unit,
    onMarkTaskComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onAddTask: () -> Unit,
    onNotificationsClick: () -> Unit = {}
) {
    var big3Expanded        by rememberSaveable { mutableStateOf(true) }
    var unscheduledExpanded by rememberSaveable { mutableStateOf(false) }
    var showNotificationPanel by rememberSaveable { mutableStateOf(false) }

    val liveTime by produceState(initialValue = currentTime) {
        while (true) { value = LocalTime.now(); delay(1000) }
    }

    val currentMinute = liveTime.hour * 60 + liveTime.minute
    val scheduled   = tasks.filter { it.schedule != null }.sortedBy { it.schedule!!.startMinute }
    val unscheduled = tasks.filter { it.schedule == null && it.source != DailyTaskSource.RECURRING }
    val currentTask = scheduled.firstOrNull { currentMinute in it.schedule!!.startMinute until it.schedule!!.endMinute }
    val nextTask    = scheduled.firstOrNull { it.schedule!!.startMinute > currentMinute }
    val big3        = tasks.filter { it.isBig3 }.take(3)
    val upcoming    = scheduled.filter { task ->
        task.schedule!!.startMinute > currentMinute && task.schedule!!.startMinute < currentMinute + (6 * 60)
    }.take(5)

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().background(ScreenBackground),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item { HomeHeader(date, liveTime, tasks.count { it.isCompleted }, tasks.size, onNotificationsClick = { showNotificationPanel = true }) }
            item {
                NowCard(
                    task = currentTask, currentMinute = currentMinute,
                    onOpenTask     = { currentTask?.let { onOpenTask(it.id) } },
                    onMarkComplete = { currentTask?.let { onMarkTaskComplete(it.id) } }
                )
            }
            nextTask?.let { item { UpNextCard(it) } }
            item { Big3Card(big3, big3Expanded, { big3Expanded = !big3Expanded }, onOpenTask, onMarkTaskComplete) }
            item { UpcomingCard(upcoming, onOpenTask) }
            if (unscheduled.isNotEmpty()) {
                item { UnscheduledCard(unscheduled, unscheduledExpanded, { unscheduledExpanded = !unscheduledExpanded }, onOpenTask, onMarkTaskComplete) }
            }
            item { PrimaryButton("Open Timetable", onOpenTimetable) }
            item { SecondaryButton("Add New Task", onAddTask) }
        }

        // 알림 패널 오버레이
        AnimatedVisibility(
            visible = showNotificationPanel,
            enter   = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit    = fadeOut() + slideOutVertically(targetOffsetY = { it })
        ) {
            NotificationPanel(
                tasks         = tasks,
                currentMinute = currentMinute,
                onDismiss     = { showNotificationPanel = false },
                onOpenTask    = { id -> showNotificationPanel = false; onOpenTask(id) }
            )
        }
    }
}

@Composable
private fun HomeHeader(date: LocalDate, currentTime: LocalTime, completedCount: Int, totalCount: Int, onNotificationsClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(date.format(DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)), style = TextStyle(color = Color.White, fontSize = 28.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.7).sp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), style = TextStyle(color = Secondary, fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.Medium))
            }
            Text(currentTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(color = Accent, fontSize = 20.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(horizontalAlignment = Alignment.End) {
                Text("Today", style = TextStyle(color = Muted, fontSize = 13.sp, lineHeight = 13.sp))
                Text("$completedCount/$totalCount", style = TextStyle(color = Color.White, fontSize = 17.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(CardBackground).clickable(onClick = onNotificationsClick), contentAlignment = Alignment.Center) {
                BellIcon(Muted)
            }
        }
    }
}

@Composable
private fun NowCard(task: DailyTask?, currentMinute: Int, onOpenTask: () -> Unit, onMarkComplete: () -> Unit) {
    if (task == null) {
        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(16.dp)).padding(horizontal = 20.dp, vertical = 20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelWithDot("NOW", Tertiary, Muted)
                Text("No scheduled task right now", style = titleStyle(15.sp, FontWeight.Normal), color = Secondary)
                Text("Take a break or plan your next block", style = bodyStyle(13.sp, Tertiary))
            }
        }
        return
    }

    val schedule  = task.schedule ?: return
    val remaining = maxOf(schedule.endMinute - currentMinute, 0)

    Box(modifier = Modifier.fillMaxWidth().height(204.dp).clip(RoundedCornerShape(16.dp)).background(Brush.linearGradient(listOf(Accent, AccentDark), start = Offset.Zero, end = Offset(900f, 500f))).clickable(onClick = onOpenTask)) {
        Box(modifier = Modifier.align(Alignment.TopEnd).offset(x = 52.dp, y = (-64).dp).size(128.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.05f)))
        Column(modifier = Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                LabelWithDot("NOW", Color.White.copy(alpha = 0.8f), Color.White.copy(alpha = 0.8f))
                Text(task.title, style = titleStyle(20.sp, FontWeight.SemiBold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ClockIcon(Color.White.copy(alpha = 0.9f), modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(formatRange(schedule), style = bodyStyle(14.sp, Color.White.copy(alpha = 0.9f), FontWeight.Medium))
                    }
                    Box(modifier = Modifier.clip(CircleShape).background(Color.White.copy(alpha = 0.2f)).padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Text("$remaining min left", style = bodyStyle(13.sp, Color.White, FontWeight.SemiBold))
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp)).background(Color.White.copy(alpha = 0.2f)).clickable(onClick = onMarkComplete), contentAlignment = Alignment.Center) {
                Text(if (task.isCompleted) "Completed" else "Mark Complete", style = bodyStyle(16.sp, Color.White, FontWeight.SemiBold))
            }
        }
    }
}

@Composable
private fun UpNextCard(task: DailyTask) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("UP NEXT", style = sectionTitle(Muted))
                Text(task.schedule?.let { formatClock(it.startMinute) }.orEmpty(), style = bodyStyle(13.sp, Secondary, FontWeight.Medium))
            }
            Text(task.title, style = titleStyle(16.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            task.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = bodyStyle(13.sp, Muted), maxLines = 2, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun Big3Card(tasks: List<DailyTask>, expanded: Boolean, onToggle: () -> Unit, onOpenTask: (String) -> Unit, onMarkTaskComplete: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp)).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarIcon(color = Priority, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TOP 3 PRIORITIES", style = sectionTitle(Priority))
                }
                ChevronIcon(expanded = expanded, color = Priority)
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(150)) + fadeOut(animationSpec = tween(90))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tasks.forEachIndexed { index, task ->
                        Big3Row(index + 1, task, onOpenTask = { onOpenTask(task.id) }, onMarkComplete = { onMarkTaskComplete(task.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun Big3Row(index: Int, task: DailyTask, onOpenTask: () -> Unit, onMarkComplete: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(CardInner).clickable(onClick = onOpenTask).padding(horizontal = 12.dp, vertical = 12.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Big3CompletionToggle(completed = task.isCompleted, onClick = onMarkComplete)
                Spacer(modifier = Modifier.width(12.dp))
                Text("#$index", style = bodyStyle(12.sp, Priority, FontWeight.Bold))
                Spacer(modifier = Modifier.width(8.dp))
                Text(task.title, style = titleStyle(15.sp, FontWeight.Medium).copy(color = if (task.isCompleted) Secondary else Color.White, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None), maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            task.schedule?.let { Text("${formatClock(it.startMinute)}  ${it.durationMinutes}min", style = bodyStyle(12.sp, if (task.isCompleted) Muted else Secondary), modifier = Modifier.padding(start = 32.dp)) }
        }
    }
}

@Composable
private fun UpcomingCard(tasks: List<DailyTask>, onOpenTask: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp)).padding(16.dp)) {
        val timelineHeight = ((tasks.size * 48) + ((tasks.size - 1).coerceAtLeast(0) * 10)).dp
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("UPCOMING BLOCKS", style = sectionTitle(Secondary))
            Box {
                Box(modifier = Modifier.padding(start = 4.dp, top = 8.dp).width(2.dp).height(timelineHeight).background(Brush.verticalGradient(listOf(Accent, Accent.copy(alpha = 0.3f), Color.Transparent))))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    tasks.forEachIndexed { index, task -> UpcomingRow(task = task, highlighted = index == 0, onOpenTask = onOpenTask) }
                }
            }
        }
    }
}

@Composable
private fun UpcomingRow(task: DailyTask, highlighted: Boolean, onOpenTask: (String) -> Unit) {
    val schedule = task.schedule ?: return
    Row(modifier = Modifier.fillMaxWidth().clickable { onOpenTask(task.id) }.padding(vertical = 2.dp), verticalAlignment = Alignment.Top) {
        Row(modifier = Modifier.width(64.dp), verticalAlignment = Alignment.Top) {
            Box(modifier = Modifier.padding(top = 4.dp).size(10.dp).shadow(elevation = if (highlighted) 10.dp else 0.dp, shape = CircleShape, ambientColor = Accent.copy(alpha = 0.5f), spotColor = Accent.copy(alpha = 0.5f)).clip(CircleShape).background(if (highlighted) Accent else CardBackground).border(2.8.dp, if (highlighted) Accent else Accent.copy(alpha = 0.6f), CircleShape))
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(formatClock(schedule.startMinute), style = bodyStyle(14.sp, if (highlighted) Accent else Secondary, FontWeight.SemiBold))
                Text("${schedule.durationMinutes}m", style = bodyStyle(11.sp, Tertiary))
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(task.title, style = titleStyle(15.sp, FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
            task.note?.takeIf { it.isNotBlank() }?.let { Text(it, style = bodyStyle(12.sp, Muted), maxLines = 1, overflow = TextOverflow.Ellipsis) }
            if (task.isBig3) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarIcon(color = Priority, modifier = Modifier.size(10.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("PRIORITY", style = TextStyle(color = Priority, fontSize = 10.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.25.sp))
                }
            }
        }
    }
}

@Composable
private fun UnscheduledCard(tasks: List<DailyTask>, expanded: Boolean, onToggle: () -> Unit, onOpenTask: (String) -> Unit, onMarkTaskComplete: (String) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).dashedBorder(CardBorder).padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("UNSCHEDULED", style = sectionTitle(Secondary))
                    Text("Tasks waiting to be planned", style = bodyStyle(12.sp, Tertiary))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(tasks.size.toString(), style = TextStyle(color = Accent, fontSize = 24.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold))
                        Text("items", style = bodyStyle(11.sp, Tertiary))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    ChevronIcon(expanded = expanded, color = Secondary)
                }
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(150)) + fadeOut(animationSpec = tween(90))
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(CardBorder))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        tasks.forEach { task ->
                            val highlighted = task.isBig3
                            Box(modifier = Modifier.fillMaxWidth().shadow(elevation = if (highlighted) 10.dp else 0.dp, shape = RoundedCornerShape(10.dp), ambientColor = Accent.copy(alpha = 0.2f), spotColor = Accent.copy(alpha = 0.2f)).clip(RoundedCornerShape(10.dp)).background(if (highlighted) Color(0xFF2A2A2A) else Color(0xFF363636)).then(if (highlighted) Modifier.border(0.7.dp, Accent, RoundedCornerShape(10.dp)) else Modifier).clickable { onOpenTask(task.id) }.padding(horizontal = 12.dp, vertical = 12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    HomeCompletionToggle(completed = task.isCompleted, onClick = { onMarkTaskComplete(task.id) })
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(task.title, style = titleStyle(16.sp, FontWeight.Medium).copy(color = if (task.isCompleted) Secondary else Color.White, textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) { task.tags.forEach { tag -> TagChip("#$tag") } }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(56.dp).shadow(elevation = 10.dp, shape = RoundedCornerShape(14.dp), ambientColor = Accent.copy(alpha = 0.2f), spotColor = Accent.copy(alpha = 0.2f)).clip(RoundedCornerShape(14.dp)).background(Accent).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarIcon(Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = bodyStyle(16.sp, Color.White, FontWeight.SemiBold))
        }
    }
}

@Composable
private fun SecondaryButton(label: String, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(52.dp).clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlusIcon(Color.White, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(label, style = bodyStyle(15.sp, Color.White, FontWeight.Medium))
        }
    }
}

@Composable
private fun Big3CompletionToggle(completed: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(if (completed) Big3CheckboxRing.copy(alpha = 0.18f) else Color.Transparent).border(1.4.dp, if (completed) Accent else Big3CheckboxRing, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (completed) CheckIcon(Accent, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun HomeCompletionToggle(completed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(modifier = modifier.size(24.dp).clip(CircleShape).background(if (completed) Accent else Color.Transparent).border(1.4.dp, Accent, CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        if (completed) CheckIcon(Color.White, modifier = Modifier.size(12.dp))
    }
}

@Composable
private fun CheckIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = (size.minDimension * 0.18f).coerceAtLeast(2.dp.toPx())
        drawLine(
            color = color,
            start = Offset(size.width * 0.18f, size.height * 0.54f),
            end = Offset(size.width * 0.42f, size.height * 0.76f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = color,
            start = Offset(size.width * 0.42f, size.height * 0.76f),
            end = Offset(size.width * 0.84f, size.height * 0.27f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
private fun TagChip(label: String) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(TagBackground).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, style = bodyStyle(10.sp, Color(0xFFD1D5DC)))
    }
}

@Composable
private fun LabelWithDot(label: String, dotColor: Color, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(dotColor))
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = TextStyle(color = textColor, fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.65.sp))
    }
}

private fun Modifier.dashedBorder(color: Color): Modifier = drawBehind {
    val strokeWidth = 0.7.dp.toPx()
    drawRoundRect(color = color, topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f), size = Size(size.width - strokeWidth, size.height - strokeWidth), cornerRadius = CornerRadius(14.dp.toPx(), 14.dp.toPx()), style = Stroke(width = strokeWidth, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))))
}

// ── 알림 패널 ─────────────────────────────────────────────────────────────────

@Composable
private fun NotificationPanel(
    tasks: List<DailyTask>,
    currentMinute: Int,
    onDismiss: () -> Unit,
    onOpenTask: (String) -> Unit
) {
    val alertTasks = tasks
        .filter { it.schedule?.reminderEnabled == true }
        .sortedBy { it.schedule!!.startMinute }
    var pastExpanded by rememberSaveable { mutableStateOf(false) }
    val (pastAlerts, activeAndUpcomingAlerts) = alertTasks.partition { task ->
        val schedule = task.schedule ?: return@partition false
        schedule.endMinute <= currentMinute
    }
    val nextAlert = alertTasks.firstOrNull { task ->
        val schedule = task.schedule ?: return@firstOrNull false
        !task.isCompleted && schedule.startMinute >= currentMinute
    }
    val activeAlert = alertTasks.firstOrNull { task ->
        val schedule = task.schedule ?: return@firstOrNull false
        currentMinute in schedule.startMinute until schedule.endMinute
    }
    val summary = when {
        alertTasks.isEmpty() -> "No alerts set"
        activeAlert != null -> "Now · ${activeAlert.title}"
        nextAlert != null -> "Next at ${formatClock(nextAlert.schedule!!.startMinute)}"
        else -> "All reminders passed"
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(onClick = onDismiss)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(CardBackground)
                .clickable(enabled = false, onClick = {})
        ) {
            Box(modifier = Modifier.padding(top = 12.dp).width(40.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFF3A3A3A)).align(Alignment.CenterHorizontally))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReminderBellIcon(Accent, Modifier.size(18.dp))
                        Text("Reminders", style = TextStyle(color = Color.White, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
                    }
                    Text(
                        "${alertTasks.size} alerts today · $summary",
                        style = TextStyle(color = Secondary, fontSize = 12.sp, lineHeight = 18.sp)
                    )
                }
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF2A2A2A)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
                    Text("\u00D7", style = TextStyle(color = Secondary, fontSize = 20.sp, lineHeight = 20.sp))
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(CardBorder))

            if (alertTasks.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF242424)).border(0.7.dp, CardBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            ReminderBellIcon(Secondary, Modifier.size(24.dp))
                        }
                        Text("No reminders yet", style = TextStyle(color = Color.White, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold))
                        Text(
                            "Turn on Alert in a time block to get notified.",
                            style = TextStyle(color = Muted, fontSize = 12.sp, lineHeight = 18.sp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (activeAndUpcomingAlerts.isEmpty()) {
                        Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF202020)).padding(horizontal = 14.dp, vertical = 16.dp)) {
                            Text("No upcoming reminders left today.", style = TextStyle(color = Secondary, fontSize = 13.sp, lineHeight = 19.sp))
                        }
                    } else {
                        activeAndUpcomingAlerts.forEach { task ->
                            ReminderTaskRow(task = task, currentMinute = currentMinute, onOpenTask = onOpenTask)
                        }
                    }

                    if (pastAlerts.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { pastExpanded = !pastExpanded }
                                .padding(horizontal = 4.dp, vertical = 8.dp)
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    if (pastExpanded) "Hide past reminders" else "Show past reminders",
                                    style = TextStyle(color = Secondary, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
                                )
                                Text("${pastAlerts.size}", style = TextStyle(color = Muted, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium))
                            }
                        }
                        if (pastExpanded) {
                            pastAlerts.forEach { task ->
                                ReminderTaskRow(task = task, currentMinute = currentMinute, onOpenTask = onOpenTask)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ReminderTaskRow(
    task: DailyTask,
    currentMinute: Int,
    onOpenTask: (String) -> Unit
) {
    val schedule = task.schedule ?: return
    val isPast = schedule.endMinute <= currentMinute
    val isNow = currentMinute in schedule.startMinute until schedule.endMinute

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(when { isNow -> Accent.copy(alpha = 0.12f); isPast -> Color(0xFF1A1A1A); else -> Color(0xFF242424) })
            .then(if (isNow) Modifier.border(0.7.dp, Accent.copy(alpha = 0.4f), RoundedCornerShape(12.dp)) else Modifier)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ReminderStateIcon(completed = task.isCompleted, isNow = isNow, isPast = isPast)

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                style = TextStyle(
                    color = if (isPast) Secondary else Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 20.sp,
                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}",
                style = TextStyle(
                    color = if (isNow) Accent else Secondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isNow) FontWeight.SemiBold else FontWeight.Normal
                )
            )
        }

        when {
            isNow -> Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Accent.copy(alpha = 0.2f)).padding(horizontal = 8.dp, vertical = 4.dp)) {
                Text("NOW", style = TextStyle(color = Accent, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp))
            }
            isPast -> Text(if (task.isCompleted) "DONE" else "MISSED", style = TextStyle(color = Muted, fontSize = 11.sp, fontWeight = FontWeight.Medium))
            else -> Text("ON", style = TextStyle(color = Secondary, fontSize = 11.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun ReminderStateIcon(completed: Boolean, isNow: Boolean, isPast: Boolean) {
    val bg = when {
        isNow -> Accent.copy(alpha = 0.22f)
        isPast -> Color(0xFF202020)
        else -> Color(0xFF2A2A2A)
    }
    val color = when {
        isNow -> Accent
        completed -> Secondary
        isPast -> Muted
        else -> Secondary
    }
    Box(
        modifier = Modifier.size(36.dp).clip(CircleShape).background(bg),
        contentAlignment = Alignment.Center
    ) {
        when {
            completed -> CheckMiniIcon(color, Modifier.size(16.dp))
            isNow -> NowDotIcon(color, Modifier.size(16.dp))
            else -> ReminderClockIcon(color, Modifier.size(17.dp))
        }
    }
}

@Composable
private fun ReminderBellIcon(color: Color, modifier: Modifier = Modifier) {
    // 아웃라인 벨 아이콘 — HomeScreen BellIcon과 동일
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val w = size.width; val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.09f)
            cubicTo(w * 0.22f, h * 0.09f, w * 0.17f, h * 0.30f, w * 0.17f, h * 0.52f)
            lineTo(w * 0.11f, h * 0.70f); lineTo(w * 0.89f, h * 0.70f)
            lineTo(w * 0.83f, h * 0.52f)
            cubicTo(w * 0.83f, h * 0.30f, w * 0.78f, h * 0.09f, w * 0.5f, h * 0.09f)
        }
        drawPath(path, color, style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round))
        drawArc(color = color, startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.37f, h * 0.70f),
            size = androidx.compose.ui.geometry.Size(w * 0.26f, h * 0.16f),
            style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun CheckMiniIcon(color: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun NowDotIcon(color: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Filled.RadioButtonChecked, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun ReminderClockIcon(color: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun BellIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.6.dp.toPx()
        val w = size.width
        val h = size.height
        // 종 몸통 — 위에서 아래로 퍼지는 아웃라인
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.09f)
            cubicTo(w * 0.22f, h * 0.09f, w * 0.17f, h * 0.30f, w * 0.17f, h * 0.52f)
            lineTo(w * 0.11f, h * 0.70f)
            lineTo(w * 0.89f, h * 0.70f)
            lineTo(w * 0.83f, h * 0.52f)
            cubicTo(w * 0.83f, h * 0.30f, w * 0.78f, h * 0.09f, w * 0.5f, h * 0.09f)
        }
        drawPath(path, color, style = Stroke(width = stroke, join = StrokeJoin.Round, cap = StrokeCap.Round))
        // 클래퍼 (아래 반원)
        drawArc(
            color = color,
            startAngle = 0f, sweepAngle = 180f, useCenter = false,
            topLeft = Offset(w * 0.37f, h * 0.70f),
            size = Size(w * 0.26f, h * 0.16f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
private fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Filled.AccessTime, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun ChevronIcon(expanded: Boolean, color: Color) {
    Icon(
        imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
        contentDescription = null,
        tint = color,
        modifier = Modifier.size(18.dp)
    )
}

@Composable
private fun CalendarIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 2.05.dp.toPx()
        val iconScale = 1.06f
        val scaleX = size.width / 24f
        val scaleY = size.height / 24f
        fun p(x: Float, y: Float): Offset {
            val cx = 12f + (x - 12f) * iconScale
            val cy = 12f + (y - 12f) * iconScale
            return Offset(cx * scaleX, cy * scaleY)
        }

        drawLine(color, p(4.8f, 6.7f), p(15.7f, 6.7f), stroke, StrokeCap.Round)
        drawLine(color, p(4.8f, 6.7f), p(4.8f, 16.9f), stroke, StrokeCap.Round)
        drawLine(color, p(4.8f, 16.9f), p(12.0f, 16.9f), stroke, StrokeCap.Round)
        drawLine(color, p(16.8f, 6.7f), p(16.8f, 11.0f), stroke, StrokeCap.Round)
        drawLine(color, p(5.3f, 10.2f), p(15.0f, 10.2f), stroke, StrokeCap.Round)

        drawLine(color, p(8.1f, 4.1f), p(8.1f, 7.7f), stroke, StrokeCap.Round)
        drawLine(color, p(13.7f, 4.1f), p(13.7f, 7.7f), stroke, StrokeCap.Round)

        val clockStroke = 2.05.dp.toPx()
        drawCircle(
            color = color,
            radius = 4.35f * iconScale * minOf(scaleX, scaleY),
            center = p(15.95f, 15.95f),
            style = Stroke(width = clockStroke)
        )
        drawLine(color, p(15.95f, 15.95f), p(15.95f, 13.45f), clockStroke, StrokeCap.Round)
        drawLine(color, p(15.95f, 15.95f), p(17.85f, 17.05f), clockStroke, StrokeCap.Round)
    }
}

@Composable
private fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    Icon(Icons.Filled.Add, contentDescription = null, tint = color, modifier = modifier)
}

@Composable
private fun StarIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            cubicTo(w * 0.55f, h * 0.06f, w * 0.58f, h * 0.09f, w * 0.61f, h * 0.14f)
            lineTo(w * 0.70f, h * 0.33f)
            lineTo(w * 0.91f, h * 0.36f)
            cubicTo(w * 0.98f, h * 0.37f, w * 1.01f, h * 0.46f, w * 0.96f, h * 0.51f)
            lineTo(w * 0.80f, h * 0.66f)
            lineTo(w * 0.84f, h * 0.88f)
            cubicTo(w * 0.86f, h * 0.96f, w * 0.77f, h * 1.01f, w * 0.70f, h * 0.97f)
            lineTo(w * 0.50f, h * 0.86f)
            lineTo(w * 0.30f, h * 0.97f)
            cubicTo(w * 0.23f, h * 1.01f, w * 0.14f, h * 0.96f, w * 0.16f, h * 0.88f)
            lineTo(w * 0.20f, h * 0.66f)
            lineTo(w * 0.04f, h * 0.51f)
            cubicTo(w * -0.01f, h * 0.46f, w * 0.02f, h * 0.37f, w * 0.09f, h * 0.36f)
            lineTo(w * 0.30f, h * 0.33f)
            lineTo(w * 0.39f, h * 0.14f)
            cubicTo(w * 0.42f, h * 0.09f, w * 0.45f, h * 0.06f, w * 0.50f, h * 0.06f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

// ── 헬퍼 ──────────────────────────────────────────────────────────────────────

private fun sectionTitle(color: Color): TextStyle =
    TextStyle(color = color, fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.65.sp)

private fun titleStyle(size: TextUnit, weight: FontWeight): TextStyle =
    TextStyle(color = Color.White, fontSize = size, lineHeight = size * 1.25f, fontWeight = weight)

private fun bodyStyle(size: TextUnit, color: Color, weight: FontWeight = FontWeight.Normal): TextStyle =
    TextStyle(color = color, fontSize = size, lineHeight = size * 1.5f, fontWeight = weight)

private fun formatClock(totalMinutes: Int): String =
    String.format(Locale.ENGLISH, "%d:%02d", totalMinutes / 60, totalMinutes % 60)

private fun formatRange(schedule: ScheduleBlock): String =
    "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}"
