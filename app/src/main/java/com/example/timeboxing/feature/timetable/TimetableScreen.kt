package com.example.timeboxing.feature.timetable

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.ScheduleBlock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

private val ScreenBackground = Color(0xFF121212)
private val HeaderBackground = Color(0xFF121212)
private val PanelBackground = Color(0xFF1A1A1A)
private val AxisBackground = Color(0xFF0F0F0F)
private val Divider = Color(0xFF2A2A2A)
private val GridLine = Color(0xFF252525)
private val Accent = Color(0xFF8687E7)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF99A1AF)
private val TextMuted = Color(0xFFD1D5DC)
private val CardBackground = Color(0xFF3A3A4A)
private val Big3Badge = Color(0x33FF9680)
private val Big3Text = Color(0xFFFF9680)
private const val HourHeight = 64
private const val AxisWidth = 64
private const val SnapMinutes = 15
private const val MinimumBlockMinutes = 15

private data class PositionedBlock(
    val task: DailyTask,
    val column: Int,
    val columnCount: Int
)

private enum class DragMode {
    MOVE,
    RESIZE_START,
    RESIZE_END
}

@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    tasks: List<DailyTask>,
    date: LocalDate,
    currentTime: LocalTime,
    showCurrentTime: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenTask: (String) -> Unit,
    onMoveToUnscheduled: (String) -> Unit,
    onUpdateSchedule: (String, ScheduleBlock) -> Unit,
    onAddTask: () -> Unit
) {
    val currentMinute = currentTime.hour * 60 + currentTime.minute
    val scheduled = tasks.filter { it.schedule != null }.sortedBy { it.schedule!!.startMinute }
    val unscheduled = tasks.filter { it.schedule == null }
    val layouts = remember(scheduled) { buildLayouts(scheduled) }
    val initialScrollHour = if (showCurrentTime) maxOf((currentMinute / 60) - 2, 0) else 13
    val scrollState = rememberScrollState(initial = initialScrollHour * HourHeight)
    val selectedId = scheduled.firstOrNull { it.isBig3 }?.id.orEmpty()
    val readOnly = !showCurrentTime
    var trayExpanded by rememberSaveable { mutableStateOf(false) }

    Column(modifier = modifier.fillMaxSize().background(ScreenBackground)) {
        TopHeader(currentTime = currentTime)
        DateHeader(date = date, onPreviousDay = onPreviousDay, onNextDay = onNextDay)
        if (readOnly) {
            ReadOnlyNotice()
        }
        Box(modifier = Modifier.weight(1f).background(PanelBackground)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (trayExpanded) 220.dp else 52.dp)
                    .verticalScroll(scrollState)
            ) {
                TimetableGrid(
                    layouts = layouts,
                    currentMinute = currentMinute,
                    selectedId = selectedId,
                    showCurrentTime = showCurrentTime,
                    readOnly = readOnly,
                    onOpenTask = onOpenTask,
                    onMoveToUnscheduled = onMoveToUnscheduled,
                    onUpdateSchedule = onUpdateSchedule
                )
            }
            BottomTray(
                tasks = unscheduled,
                expanded = trayExpanded,
                readOnly = readOnly,
                modifier = Modifier.align(Alignment.BottomCenter),
                onToggle = { trayExpanded = !trayExpanded },
                onOpenTask = onOpenTask,
                onAddTask = onAddTask
            )
        }
    }
}

@Composable
private fun TopHeader(currentTime: LocalTime) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(69.dp)
            .background(HeaderBackground)
            .border(0.7.dp, Divider)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Time Blocks",
            style = TextStyle(
                color = TextPrimary,
                fontSize = 20.sp,
                lineHeight = 28.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.5).sp
            )
        )
        Text(
            text = currentTime.format(DateTimeFormatter.ofPattern("HH:mm")),
            style = TextStyle(color = Accent, fontSize = 16.sp, lineHeight = 24.sp)
        )
    }
}

@Composable
private fun DateHeader(date: LocalDate, onPreviousDay: () -> Unit, onNextDay: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(77.dp)
            .background(HeaderBackground)
            .border(0.7.dp, Divider)
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        CircleArrow(direction = -1, onClick = onPreviousDay)
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),
                style = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold)
            )
            Text(
                text = date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)),
                style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp)
            )
        }
        CircleArrow(direction = 1, onClick = onNextDay)
    }
}

@Composable
private fun ReadOnlyNotice() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF191919))
            .padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TextSecondary))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "Past and future days are view only.",
            style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)
        )
    }
}

@Composable
private fun TimetableGrid(
    layouts: List<PositionedBlock>,
    currentMinute: Int,
    selectedId: String,
    showCurrentTime: Boolean,
    readOnly: Boolean,
    onOpenTask: (String) -> Unit,
    onMoveToUnscheduled: (String) -> Unit,
    onUpdateSchedule: (String, ScheduleBlock) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height((24 * HourHeight).dp)
            .background(PanelBackground)
    ) {
        val contentWidth = maxWidth - AxisWidth.dp - 24.dp
        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .width(AxisWidth.dp)
                        .fillMaxSize()
                        .background(AxisBackground)
                ) {
                    repeat(24) { hour ->
                        Box(modifier = Modifier.height(HourHeight.dp).fillMaxWidth()) {
                            Text(
                                text = String.format(Locale.ENGLISH, "%02d:00", hour),
                                modifier = Modifier.padding(start = 8.dp),
                                style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(8.dp)
                                    .height(0.7.dp)
                                    .background(GridLine)
                            )
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Column(modifier = Modifier.fillMaxSize()) {
                        repeat(24) {
                            Box(modifier = Modifier.height(HourHeight.dp).fillMaxWidth().border(0.35.dp, GridLine))
                        }
                    }
                    if (showCurrentTime) CurrentLine(minute = currentMinute)
                }
            }

            layouts.forEach { block ->
                val schedule = block.task.schedule ?: return@forEach
                val top = (schedule.startMinute.toFloat() / 60f * HourHeight).dp
                val height = ((schedule.endMinute - schedule.startMinute).toFloat() / 60f * HourHeight).dp
                val spacing = 8.dp
                val columns = block.columnCount.coerceAtLeast(1)
                val width = ((contentWidth - spacing * (columns + 1)) / columns)
                val left = AxisWidth.dp + spacing + (width + spacing) * block.column
                Box(
                    modifier = Modifier
                        .padding(start = left, top = top)
                        .width(width)
                        .height(height)
                ) {
                    ScheduledCard(
                        task = block.task,
                        selected = block.task.id == selectedId,
                        showNow = showCurrentTime && currentMinute in schedule.startMinute until schedule.endMinute,
                        readOnly = readOnly,
                        width = width,
                        onOpen = { onOpenTask(block.task.id) },
                        onUnschedule = { onMoveToUnscheduled(block.task.id) },
                        onUpdateSchedule = { onUpdateSchedule(block.task.id, it) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CurrentLine(minute: Int) {
    val top = (minute.toFloat() / 60f * HourHeight).dp
    Row(modifier = Modifier.padding(start = AxisWidth.dp, top = top), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(Accent))
        Box(modifier = Modifier.height(2.dp).fillMaxWidth().background(Accent))
    }
}

@Composable
private fun ScheduledCard(
    task: DailyTask,
    selected: Boolean,
    showNow: Boolean,
    readOnly: Boolean,
    width: Dp,
    onOpen: () -> Unit,
    onUnschedule: () -> Unit,
    onUpdateSchedule: (ScheduleBlock) -> Unit
) {
    val schedule = task.schedule ?: return
    val borderColor = if (selected) Color.White else Accent.copy(alpha = 0.2f)
    val borderWidth = if (selected) 1.4.dp else 0.7.dp
    val narrow = width < 140.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(CardBackground)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !readOnly, onClick = onOpen)
            .then(
                if (readOnly) Modifier else Modifier.pointerInput(task.id) {
                    var dragY = 0f
                    detectDragGesturesAfterLongPress(
                        onDragStart = { dragY = 0f },
                        onDragCancel = { dragY = 0f },
                        onDragEnd = {
                            onUpdateSchedule(schedule.applyDraggedMinutes(dragY, DragMode.MOVE))
                            dragY = 0f
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            dragY += dragAmount.y
                        }
                    )
                }
            )
            .padding(10.dp)
    ) {
        if (!readOnly) {
            ResizeHandle(
                modifier = Modifier.align(Alignment.TopCenter),
                onDragEnd = { delta -> onUpdateSchedule(schedule.applyDraggedMinutes(delta, DragMode.RESIZE_START)) }
            )
            ResizeHandle(
                modifier = Modifier.align(Alignment.BottomCenter),
                onDragEnd = { delta -> onUpdateSchedule(schedule.applyDraggedMinutes(delta, DragMode.RESIZE_END)) }
            )
        }

        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.Top) {
                    CompletionStub()
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = task.title,
                                modifier = Modifier.weight(1f, fill = false),
                                style = TextStyle(color = TextPrimary, fontSize = if (narrow) 12.sp else 14.sp, lineHeight = if (narrow) 15.sp else 17.5.sp, fontWeight = FontWeight.SemiBold),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (task.isBig3) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Big3Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text(text = "Big 3", style = TextStyle(color = Big3Text, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.45.sp))
                                }
                            }
                        }
                        if (!narrow && task.tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                task.tags.take(3).forEach { tag ->
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text(text = "#$tag", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, lineHeight = 15.sp))
                                    }
                                }
                            }
                        }
                    }
                    if (!readOnly) {
                        CloseIcon(Color.White.copy(alpha = 0.6f), onClick = onUnschedule)
                    }
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}",
                    style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = if (narrow) 10.sp else 11.sp, lineHeight = 16.5.sp, fontFamily = FontFamily.Monospace)
                )
                if (showNow) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text(text = "Now", style = TextStyle(color = Accent, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResizeHandle(
    modifier: Modifier = Modifier,
    onDragEnd: (Float) -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(18.dp)
            .pointerInput(Unit) {
                var dragY = 0f
                detectDragGestures(
                    onDragStart = { dragY = 0f },
                    onDragCancel = { dragY = 0f },
                    onDragEnd = {
                        onDragEnd(dragY)
                        dragY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount.y
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.32f))
        )
    }
}

@Composable
private fun CompletionStub() {
    Box(modifier = Modifier.size(16.dp).border(1.4.dp, Color.White.copy(alpha = 0.7f), RoundedCornerShape(4.dp)))
}

@Composable
private fun BottomTray(
    tasks: List<DailyTask>,
    expanded: Boolean,
    readOnly: Boolean,
    modifier: Modifier = Modifier,
    onToggle: () -> Unit,
    onOpenTask: (String) -> Unit,
    onAddTask: () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .border(1.4.dp, Color(0xFF404040), RoundedCornerShape(0.dp))
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockMiniIcon(TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = "Unscheduled", style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF2A2A2A)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(text = tasks.size.toString(), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp))
                }
            }
            ChevronUpIcon(TextSecondary, expanded = expanded)
        }
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (tasks.isEmpty()) {
                    Text(
                        text = "All tasks are scheduled.",
                        style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                    )
                } else {
                    tasks.forEach { task ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF2A2A2A))
                                .clickable(enabled = !readOnly) { onOpenTask(task.id) }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Accent.copy(alpha = 0.35f)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = task.title, style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (task.tags.isNotEmpty()) {
                                    Text(text = task.tags.joinToString("  ") { "#$it" }, style = TextStyle(color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (!readOnly) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(Accent)
                            .clickable(onClick = onAddTask)
                            .padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "Add Task", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
                    }
                }
            }
        }
    }
}

@Composable
private fun CircleArrow(direction: Int, onClick: () -> Unit) {
    Box(modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(20.dp)) {
            val stroke = 1.8.dp.toPx()
            if (direction < 0) {
                drawLine(Color.White, Offset(size.width * 0.62f, size.height * 0.2f), Offset(size.width * 0.38f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * 0.38f, size.height * 0.5f), Offset(size.width * 0.62f, size.height * 0.8f), stroke, StrokeCap.Round)
            } else {
                drawLine(Color.White, Offset(size.width * 0.38f, size.height * 0.2f), Offset(size.width * 0.62f, size.height * 0.5f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * 0.62f, size.height * 0.5f), Offset(size.width * 0.38f, size.height * 0.8f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun CloseIcon(color: Color, onClick: () -> Unit) {
    Canvas(modifier = Modifier.size(17.dp).clickable(onClick = onClick)) {
        val stroke = 1.4.dp.toPx()
        drawLine(color, Offset(size.width * 0.28f, size.height * 0.28f), Offset(size.width * 0.72f, size.height * 0.72f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.72f, size.height * 0.28f), Offset(size.width * 0.28f, size.height * 0.72f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ClockMiniIcon(color: Color) {
    Canvas(modifier = Modifier.size(16.dp)) {
        val stroke = 1.5.dp.toPx()
        drawCircle(color = color, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, size.height * 0.28f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(size.width * 0.68f, center.y), stroke, StrokeCap.Round)
    }
}

@Composable
private fun ChevronUpIcon(color: Color, expanded: Boolean) {
    Canvas(modifier = Modifier.size(18.dp)) {
        val stroke = 1.7.dp.toPx()
        if (expanded) {
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.62f), Offset(size.width * 0.5f, size.height * 0.38f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.5f, size.height * 0.38f), Offset(size.width * 0.75f, size.height * 0.62f), stroke, StrokeCap.Round)
        } else {
            drawLine(color, Offset(size.width * 0.25f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.62f), stroke, StrokeCap.Round)
            drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.75f, size.height * 0.38f), stroke, StrokeCap.Round)
        }
    }
}

private fun ScheduleBlock.applyDraggedMinutes(deltaPx: Float, mode: DragMode): ScheduleBlock {
    val minuteDelta = (((deltaPx / HourHeight.toFloat()) * 60f) / SnapMinutes).roundToInt() * SnapMinutes
    val duration = durationMinutes
    return when (mode) {
        DragMode.MOVE -> {
            val newStart = (startMinute + minuteDelta).coerceIn(0, (24 * 60) - duration)
            copy(startMinute = newStart, endMinute = newStart + duration)
        }
        DragMode.RESIZE_START -> {
            val newStart = (startMinute + minuteDelta).coerceIn(0, endMinute - MinimumBlockMinutes)
            copy(startMinute = newStart)
        }
        DragMode.RESIZE_END -> {
            val newEnd = (endMinute + minuteDelta).coerceIn(startMinute + MinimumBlockMinutes, 24 * 60)
            copy(endMinute = newEnd)
        }
    }
}

private fun buildLayouts(tasks: List<DailyTask>): List<PositionedBlock> {
    if (tasks.isEmpty()) return emptyList()
    val clusters = mutableListOf<List<DailyTask>>()
    var current = mutableListOf<DailyTask>()
    var clusterEnd = -1
    tasks.forEach { task ->
        val schedule = task.schedule ?: return@forEach
        if (current.isEmpty() || schedule.startMinute < clusterEnd) {
            current += task
            clusterEnd = maxOf(clusterEnd, schedule.endMinute)
        } else {
            clusters += current.toList()
            current = mutableListOf(task)
            clusterEnd = schedule.endMinute
        }
    }
    if (current.isNotEmpty()) clusters += current.toList()

    return clusters.flatMap { cluster ->
        val active = mutableListOf<PositionedBlock>()
        val assigned = mutableListOf<PositionedBlock>()
        var maxColumns = 1
        cluster.forEach { task ->
            val schedule = task.schedule ?: return@forEach
            active.removeAll { it.task.schedule!!.endMinute <= schedule.startMinute }
            val used = active.map { it.column }.toSet()
            var column = 0
            while (column in used) column += 1
            val positioned = PositionedBlock(task = task, column = column, columnCount = 1)
            active += positioned
            assigned += positioned
            maxColumns = maxOf(maxColumns, active.size)
        }
        assigned.map { it.copy(columnCount = maxColumns) }
    }
}

private fun formatClock(totalMinutes: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
