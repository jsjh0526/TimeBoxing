package com.example.timeboxing.feature.home

import androidx.compose.animation.animateContentSize
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
    var big3Expanded by rememberSaveable { mutableStateOf(true) }
    var unscheduledExpanded by rememberSaveable { mutableStateOf(false) }
    val liveTime by produceState(initialValue = currentTime) {
        while (true) {
            value = LocalTime.now()
            delay(1000)
        }
    }

    val currentMinute = liveTime.hour * 60 + liveTime.minute
    val scheduled     = tasks.filter { it.schedule != null }.sortedBy { it.schedule!!.startMinute }
    val unscheduled   = tasks.filter { it.schedule == null && it.source != DailyTaskSource.RECURRING }
    val currentTask   = scheduled.firstOrNull { currentMinute in it.schedule!!.startMinute until it.schedule!!.endMinute }
    val nextTask      = scheduled.firstOrNull { it.schedule!!.startMinute > currentMinute }
    val big3          = tasks.filter { it.isBig3 }.take(3)
    val upcoming      = scheduled
        .filter { task ->
            task.schedule!!.startMinute > currentMinute &&
                task.schedule!!.startMinute < currentMinute + (6 * 60)
        }
        .take(5)

    LazyColumn(
        modifier = modifier.fillMaxSize().background(ScreenBackground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        item { HomeHeader(date, liveTime, tasks.count { it.isCompleted }, tasks.size, onNotificationsClick) }
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
            item {
                UnscheduledCard(unscheduled, unscheduledExpanded, { unscheduledExpanded = !unscheduledExpanded }, onOpenTask, onMarkTaskComplete)
            }
        }
        item { PrimaryButton("Open Timetable", onOpenTimetable) }
        item { SecondaryButton("Add New Task", onAddTask) }
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
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).border(0.7.dp, CardBorder, RoundedCornerShape(14.dp)).animateContentSize().padding(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    StarIcon(color = Priority, modifier = Modifier.size(12.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("TOP 3 PRIORITIES", style = sectionTitle(Priority))
                }
                ChevronIcon(expanded = expanded, color = Priority)
            }
            if (expanded) {
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
        Row(verticalAlignment = Alignment.Top) {
            Big3CompletionToggle(completed = task.isCompleted, modifier = Modifier.padding(top = 2.dp), onClick = onMarkComplete)
            Spacer(modifier = Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("#$index", style = bodyStyle(12.sp, Priority, FontWeight.Bold))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        task.title,
                        style = titleStyle(15.sp, FontWeight.Medium).copy(
                            color = if (task.isCompleted) Secondary else Color.White,
                            textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                        ),
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                task.schedule?.let {
                    Text(
                        "${formatClock(it.startMinute)}  ${it.durationMinutes}min",
                        style = bodyStyle(12.sp, if (task.isCompleted) Muted else Secondary)
                    )
                }
            }
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
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(CardBackground).dashedBorder(CardBorder).animateContentSize().padding(16.dp)) {
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
            if (expanded) {
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
private fun Big3CompletionToggle(completed: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clip(CircleShape)
            .background(if (completed) Big3CheckboxRing.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.4.dp, if (completed) Accent else Big3CheckboxRing, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (completed) CheckIcon(Accent, modifier = Modifier.size(10.dp))
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
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * 0.2f,  size.height * 0.55f), Offset(size.width * 0.42f, size.height * 0.76f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.42f, size.height * 0.76f), Offset(size.width * 0.8f,  size.height * 0.26f), stroke, StrokeCap.Round)
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

@Composable
private fun BellIcon(color: Color) {
    Canvas(modifier = Modifier.size(20.dp)) {
        val stroke = 1.8.dp.toPx()
        val bellPath = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.14f)
            cubicTo(size.width * 0.28f, size.height * 0.14f, size.width * 0.22f, size.height * 0.34f, size.width * 0.22f, size.height * 0.5f)
            lineTo(size.width * 0.18f, size.height * 0.66f)
            lineTo(size.width * 0.82f, size.height * 0.66f)
            lineTo(size.width * 0.78f, size.height * 0.5f)
            cubicTo(size.width * 0.78f, size.height * 0.34f, size.width * 0.72f, size.height * 0.14f, size.width * 0.5f, size.height * 0.14f)
        }
        drawPath(path = bellPath, color = color, style = Stroke(width = stroke))
        drawArc(color = color, startAngle = 30f, sweepAngle = 120f, useCenter = false, topLeft = Offset(size.width * 0.37f, size.height * 0.69f), size = Size(size.width * 0.26f, size.height * 0.14f), style = Stroke(width = stroke, cap = StrokeCap.Round))
    }
}

@Composable
private fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color = color, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(size.width * 0.7f, center.y),   stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronIcon(expanded: Boolean, color: Color) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.8.dp.toPx()
        if (expanded) {
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.38f), Offset(size.width * 0.5f,  size.height * 0.62f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.5f,  size.height * 0.62f), Offset(size.width * 0.75f, size.height * 0.38f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, Offset(size.width * 0.38f, size.height * 0.25f), Offset(size.width * 0.62f, size.height * 0.5f),  stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.62f, size.height * 0.5f),  Offset(size.width * 0.38f, size.height * 0.75f), stroke, StrokeCap.Round)
        }
    }
}

@Composable
private fun CalendarIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        drawRoundRect(color = color, topLeft = Offset(size.width * 0.15f, size.height * 0.22f), size = Size(size.width * 0.7f, size.height * 0.63f), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()), style = Stroke(width = stroke))
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.42f), Offset(size.width * 0.85f, size.height * 0.42f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.32f, size.height * 0.12f), Offset(size.width * 0.32f, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.68f, size.height * 0.12f), Offset(size.width * 0.68f, size.height * 0.28f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun PlusIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(center.x, size.height * 0.2f), Offset(center.x, size.height * 0.8f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.2f, center.y), Offset(size.width * 0.8f, center.y),  stroke, StrokeCap.Round)
    }
}

@Composable
private fun StarIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.5f,  size.height * 0.08f)
            lineTo(size.width * 0.62f, size.height * 0.36f)
            lineTo(size.width * 0.92f, size.height * 0.38f)
            lineTo(size.width * 0.69f, size.height * 0.58f)
            lineTo(size.width * 0.77f, size.height * 0.9f)
            lineTo(size.width * 0.5f,  size.height * 0.72f)
            lineTo(size.width * 0.23f, size.height * 0.9f)
            lineTo(size.width * 0.31f, size.height * 0.58f)
            lineTo(size.width * 0.08f, size.height * 0.38f)
            lineTo(size.width * 0.38f, size.height * 0.36f)
            close()
        }
        drawPath(path = path, color = color)
    }
}

// ── 헬퍼 ──────────────────────────────────────────────────────────────────

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
