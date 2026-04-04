package com.example.timeboxing.feature.timetable

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.ScheduleBlock
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private val ScreenBackground = Color(0xFF121212)
private val HeaderBackground = Color(0xFF121212)
private val PanelBackground  = Color(0xFF1A1A1A)
private val AxisBackground   = Color(0xFF0F0F0F)
private val AxisBorderColor  = Color(0xFF404040)
private val Divider          = Color(0xFF2A2A2A)
private val GridLineHour     = Color(0xFF353535)
private val GridLineHalf     = Color(0xFF2A2A2A)
private val Accent           = Color(0xFF8687E7)
private val TextPrimary      = Color.White
private val TextSecondary    = Color(0xFF99A1AF)
private val TextMuted        = Color(0xFFD1D5DC)
private val CardBackground   = Color(0xFF3A3A4A)
private val Big3Badge        = Color(0x33FF9680)
private val Big3Text         = Color(0xFFFF9680)

private const val VisibleHours        = 6f
private val CollapsedTrayHeight       = 46.dp
private val ExpandedTrayHeight        = 222.dp
private val BlockHorizontalSpacing    = 8.dp
private val CurrentLineInset          = 0.dp
private val CurrentLineHorizontalNudge = (-0.7).dp
private const val AxisWidth           = 52
private const val SnapMinutes         = 15
private const val MinimumBlockMinutes = 15

private data class PositionedBlock(val task: DailyTask, val column: Int, val columnCount: Int)

// DragSession: MOVE만 존재 — resize 제거
private data class DragSession(
    val taskId: String,
    val originalSchedule: ScheduleBlock,
    val snappedSchedule: ScheduleBlock,
    val rawDeltaPx: Float = 0f,
    val pointerYInViewport: Float = 0f
)

private data class PreviewFrame(val topPx: Float, val heightPx: Float)

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
    val density = LocalDensity.current
    val currentMinute = currentTime.hour * 60 + currentTime.minute
    val scheduled = tasks.filter { it.schedule != null }.sortedBy { it.schedule!!.startMinute }
    val unscheduled = tasks.filter { it.schedule == null }
    val layouts = remember(scheduled) { buildLayouts(scheduled) }
    val scrollState = rememberScrollState()
    val selectedId = scheduled.firstOrNull { it.isBig3 }?.id.orEmpty()
    val readOnly = !showCurrentTime
    var trayExpanded by rememberSaveable { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var viewportTopInRootPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxSize().background(ScreenBackground)) {
        TopHeader(currentTime = currentTime)
        DateHeader(date = date, onPreviousDay = onPreviousDay, onNextDay = onNextDay)
        if (readOnly) ReadOnlyNotice()
        BoxWithConstraints(modifier = Modifier.weight(1f).background(PanelBackground)) {
            val hourHeight = ((maxHeight - CollapsedTrayHeight).coerceAtLeast(1.dp)) / VisibleHours
            val initialScrollHour = if (showCurrentTime) maxOf((currentMinute / 60) - 2, 0) else 13

            LaunchedEffect(date, showCurrentTime, hourHeight) {
                val initialScrollPx = with(density) { (hourHeight * initialScrollHour.toFloat()).roundToPx() }
                scrollState.scrollTo(initialScrollPx)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = if (trayExpanded) ExpandedTrayHeight else CollapsedTrayHeight)
                    .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                    .onGloballyPositioned { viewportTopInRootPx = it.positionInRoot().y }
                    .verticalScroll(scrollState)
            ) {
                TimetableGrid(
                    hourHeight = hourHeight,
                    layouts = layouts,
                    currentMinute = currentMinute,
                    selectedId = selectedId,
                    showCurrentTime = showCurrentTime,
                    readOnly = readOnly,
                    scrollState = scrollState,
                    viewportHeightPx = viewportHeightPx,
                    viewportTopInRootPx = viewportTopInRootPx,
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
private fun TimetableGrid(
    hourHeight: Dp,
    layouts: List<PositionedBlock>,
    currentMinute: Int,
    selectedId: String,
    showCurrentTime: Boolean,
    readOnly: Boolean,
    scrollState: ScrollState,
    viewportHeightPx: Float,
    viewportTopInRootPx: Float,
    onOpenTask: (String) -> Unit,
    onMoveToUnscheduled: (String) -> Unit,
    onUpdateSchedule: (String, ScheduleBlock) -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxWidth().height(hourHeight * 24f).background(PanelBackground)
    ) {
        val density = LocalDensity.current
        val pixelsPerHour = with(density) { hourHeight.toPx() }
        val edgeZonePx = with(density) { 56.dp.toPx() }
        val maxAutoScrollStepPx = with(density) { 22.dp.toPx() }
        val contentWidth = maxWidth - AxisWidth.dp

        var dragSession by remember(layouts.map { it.task.id }) { mutableStateOf<DragSession?>(null) }

        fun computeSnapped(original: ScheduleBlock, rawDeltaPx: Float): ScheduleBlock {
            val clamped = original.clampMoveDeltaPx(rawDeltaPx, pixelsPerHour)
            val minuteDelta = snappedMinuteDelta(clamped, pixelsPerHour)
            return original.applyMoveDelta(minuteDelta)
        }

        fun beginDrag(taskId: String, originalSchedule: ScheduleBlock, pointerYInViewport: Float) {
            dragSession = DragSession(
                taskId = taskId,
                originalSchedule = originalSchedule,
                snappedSchedule = originalSchedule,
                rawDeltaPx = 0f,
                pointerYInViewport = pointerYInViewport
            )
        }

        fun accumulateDrag(taskId: String, deltaPx: Float, pointerYInViewport: Float) {
            val cur = dragSession
            if (cur?.taskId != taskId) return
            val newRaw = cur.rawDeltaPx + deltaPx
            dragSession = cur.copy(
                rawDeltaPx = newRaw,
                snappedSchedule = computeSnapped(cur.originalSchedule, newRaw),
                pointerYInViewport = pointerYInViewport
            )
        }

        fun commitDrag(taskId: String) {
            val cur = dragSession ?: return
            if (cur.taskId != taskId) return
            dragSession = null
            if (cur.snappedSchedule != cur.originalSchedule) {
                onUpdateSchedule(taskId, cur.snappedSchedule)
            }
        }

        LaunchedEffect(dragSession?.taskId, viewportHeightPx) {
            if (viewportHeightPx <= 0f) return@LaunchedEffect
            while (true) {
                val session = dragSession ?: break
                val scrollDeltaPx = autoScrollDeltaPx(
                    pointerYInViewport = session.pointerYInViewport,
                    viewportHeightPx = viewportHeightPx,
                    edgeZonePx = edgeZonePx,
                    maxStepPx = maxAutoScrollStepPx
                )
                if (scrollDeltaPx != 0f) {
                    val consumed = scrollState.scrollBy(scrollDeltaPx)
                    if (consumed != 0f) {
                        val latest = dragSession
                        if (latest?.taskId == session.taskId) {
                            val newRaw = latest.rawDeltaPx + consumed
                            dragSession = latest.copy(
                                rawDeltaPx = newRaw,
                                snappedSchedule = computeSnapped(latest.originalSchedule, newRaw)
                            )
                        }
                    }
                }
                delay(16)
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.width(AxisWidth.dp).fillMaxSize().background(AxisBackground)) {
                    repeat(24) { hour ->
                        Box(modifier = Modifier.height(hourHeight).fillMaxWidth().padding(end = 1.4.dp)) {
                            Text(
                                text = String.format(Locale.ENGLISH, "%02d:00", hour),
                                modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                                style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                            )
                            Box(modifier = Modifier.align(Alignment.CenterEnd).width(8.dp).height(0.7.dp).background(GridLineHalf))
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                    Box(modifier = Modifier.width(1.4.dp).fillMaxSize().background(AxisBorderColor))
                    Box(modifier = Modifier.padding(start = 1.4.dp).fillMaxSize()) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            repeat(24) {
                                Box(modifier = Modifier.height(hourHeight).fillMaxWidth()) {
                                    Box(modifier = Modifier.align(Alignment.TopCenter).padding(top = hourHeight / 2f).fillMaxWidth().height(0.7.dp).background(GridLineHalf))
                                    Box(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(0.7.dp).background(GridLineHour))
                                }
                            }
                        }
                        if (showCurrentTime) CurrentLine(
                            minute = currentMinute,
                            hourHeight = hourHeight,
                            horizontalInset = CurrentLineInset,
                            horizontalNudge = CurrentLineHorizontalNudge
                        )
                    }
                }
            }

            layouts.forEach { block ->
                val originalSchedule = block.task.schedule ?: return@forEach
                key(block.task.id, originalSchedule.startMinute, originalSchedule.endMinute) {
                    val session = dragSession?.takeIf { it.taskId == block.task.id }
                    val renderedSchedule = session?.snappedSchedule ?: originalSchedule
                    val previewFrame = renderedSchedule.previewFrame(pixelsPerHour)
                    val top    = with(density) { previewFrame.topPx.toDp() }
                    val height = with(density) { previewFrame.heightPx.toDp() }
                    val spacing = BlockHorizontalSpacing
                    val renderedColumns = block.columnCount.coerceAtLeast(1)
                    val width = (contentWidth - spacing * (renderedColumns + 1)) / renderedColumns
                    val left  = AxisWidth.dp + spacing + (width + spacing) * block.column

                    Box(
                        modifier = Modifier
                            .padding(start = left, top = top)
                            .width(width).height(height)
                            .zIndex(if (session != null) 10f else 0f)
                    ) {
                        ScheduledCard(
                            task = block.task,
                            schedule = renderedSchedule,
                            gestureSchedule = originalSchedule,
                            selected = block.task.id == selectedId && dragSession == null,
                            showNow = showCurrentTime && currentMinute in renderedSchedule.startMinute until renderedSchedule.endMinute,
                            isDragging = session != null,
                            readOnly = readOnly,
                            width = width,
                            viewportTopInRootPx = viewportTopInRootPx,
                            onOpen = { onOpenTask(block.task.id) },
                            onUnschedule = { onMoveToUnscheduled(block.task.id) },
                            onDragStart = { pointerY -> beginDrag(block.task.id, originalSchedule, pointerY) },
                            onDrag = { delta, pointerY -> accumulateDrag(block.task.id, delta, pointerY) },
                            onDragEnd = { commitDrag(block.task.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopHeader(currentTime: LocalTime) {
    Row(
        modifier = Modifier.fillMaxWidth().height(69.dp).background(HeaderBackground).padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Text("Time Blocks", style = TextStyle(color = TextPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
        Text(currentTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(color = Accent, fontSize = 16.sp, lineHeight = 24.sp))
    }
}

@Composable
private fun DateHeader(date: LocalDate, onPreviousDay: () -> Unit, onNextDay: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(HeaderBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GridLineHalf))
        Row(
            modifier = Modifier.fillMaxWidth().height(77.dp).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            CircleArrow(direction = -1, onClick = onPreviousDay)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), style = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold))
                Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp))
            }
            CircleArrow(direction = 1, onClick = onNextDay)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GridLineHalf))
    }
}

@Composable
private fun ReadOnlyNotice() {
    Row(
        modifier = Modifier.fillMaxWidth().background(Color(0xFF191919)).padding(horizontal = 24.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TextSecondary))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Past and future days are view only.", style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp))
    }
}

@Composable
private fun CurrentLine(minute: Int, hourHeight: Dp, horizontalInset: Dp, horizontalNudge: Dp) {
    val top = hourHeight * (minute.toFloat() / 60f)
    Box(
        modifier = Modifier
            .offset(x = horizontalNudge, y = top - 5.dp)
            .fillMaxWidth()
            .padding(horizontal = horizontalInset)
            .height(10.dp)
    ) {
        Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Accent))
        Box(modifier = Modifier.align(Alignment.CenterStart).size(10.dp).clip(CircleShape).background(Accent).shadow(10.dp, CircleShape, ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f)))
        Box(modifier = Modifier.align(Alignment.CenterEnd).size(10.dp).clip(CircleShape).background(Accent).shadow(10.dp, CircleShape, ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f)))
    }
}

// ResizeHandle 제거 — 시간 조정은 에디터에서
@Composable
private fun ScheduledCard(
    task: DailyTask,
    schedule: ScheduleBlock,
    gestureSchedule: ScheduleBlock,
    selected: Boolean,
    showNow: Boolean,
    isDragging: Boolean,
    readOnly: Boolean,
    width: Dp,
    viewportTopInRootPx: Float,
    onOpen: () -> Unit,
    onUnschedule: () -> Unit,
    onDragStart: (Float) -> Unit,
    onDrag: (Float, Float) -> Unit,
    onDragEnd: () -> Unit
) {
    var cardTopInRootPx by remember(task.id, gestureSchedule.startMinute, gestureSchedule.endMinute) { mutableStateOf(0f) }
    val narrow = width < 140.dp

    val (borderWidth, borderColor) = when {
        selected && !isDragging -> 1.4.dp to Accent
        isDragging              -> 0.7.dp to Accent.copy(alpha = 0.45f)
        else                    -> 0.7.dp to Accent.copy(alpha = 0.2f)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { cardTopInRootPx = it.positionInRoot().y }
            .graphicsLayer { if (isDragging) { scaleX = 1.015f; scaleY = 1.015f } }
            .then(
                if (selected && !isDragging) Modifier.shadow(0.dp, RoundedCornerShape(8.dp), ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f))
                else if (isDragging) Modifier.shadow(20.dp, RoundedCornerShape(8.dp))
                else Modifier
            )
            .clip(RoundedCornerShape(8.dp))
            .background(if (isDragging) Color(0xFF494962) else CardBackground)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !readOnly && !isDragging, onClick = onOpen)
            .then(
                if (readOnly) Modifier else Modifier.pointerInput(task.id, gestureSchedule.startMinute, gestureSchedule.endMinute, viewportTopInRootPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            onDragStart(cardTopInRootPx + offset.y - viewportTopInRootPx)
                        },
                        onDragCancel = onDragEnd,
                        onDragEnd    = onDragEnd,
                        onDrag = { change, dragAmount ->
                            change.consume()
                            onDrag(dragAmount.y, cardTopInRootPx + change.position.y - viewportTopInRootPx)
                        }
                    )
                }
            )
            .padding(10.dp)
    ) {
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
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                            if (task.isBig3) {
                                Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Big3Badge).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                    Text("Big 3", style = TextStyle(color = Big3Text, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.45.sp))
                                }
                            }
                        }
                        if (!narrow && task.tags.isNotEmpty()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                task.tags.take(3).forEach { tag ->
                                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Color.Black.copy(alpha = 0.1f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                                        Text("#$tag", style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = 10.sp, lineHeight = 15.sp))
                                    }
                                }
                            }
                        }
                    }
                    if (!readOnly) CloseIcon(Color.White.copy(alpha = 0.6f), onClick = onUnschedule)
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}",
                    style = TextStyle(color = Color.White.copy(alpha = 0.75f), fontSize = if (narrow) 10.sp else 11.sp, lineHeight = 16.5.sp, fontFamily = FontFamily.Monospace)
                )
                if (showNow) {
                    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
                        Text("Now", style = TextStyle(color = Accent, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp))
                    }
                }
            }
        }
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
    Column(modifier = modifier.fillMaxWidth().background(Color(0xFF1E1E1E)).animateContentSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(2.dp).background(AxisBackground))
        Row(
            modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onToggle).padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockMiniIcon(TextSecondary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Unscheduled", style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold))
                Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF2A2A2A)).padding(horizontal = 7.dp, vertical = 2.dp)) {
                    Text(tasks.size.toString(), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp))
                }
            }
            ChevronUpIcon(TextSecondary, expanded = expanded)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tasks.isEmpty()) {
                    Text("All tasks are scheduled.", style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp))
                } else {
                    tasks.forEach { task ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A2A))
                                .clickable(enabled = !readOnly) { onOpenTask(task.id) }.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Accent.copy(alpha = 0.35f)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(task.title, style = TextStyle(color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                if (task.tags.isNotEmpty()) {
                                    Text(task.tags.joinToString("  ") { "#$it" }, style = TextStyle(color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
                if (!readOnly) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accent).clickable(onClick = onAddTask).padding(vertical = 14.dp),
                        horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Add Task", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
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

// ── 헬퍼 ────────────────────────────────────────────────────────────────────

private fun snappedMinuteDelta(deltaPx: Float, pixelsPerHour: Float): Int {
    val rawSteps = ((deltaPx / pixelsPerHour) * 60f) / SnapMinutes
    return rawSteps.roundToInt() * SnapMinutes
}

private fun ScheduleBlock.clampMoveDeltaPx(deltaPx: Float, pixelsPerHour: Float): Float {
    val maxUpPx   = (startMinute / 60f) * pixelsPerHour
    val maxDownPx = ((24 * 60 - endMinute) / 60f) * pixelsPerHour
    return deltaPx.coerceIn(-maxUpPx, maxDownPx)
}

private fun ScheduleBlock.applyMoveDelta(minuteDelta: Int): ScheduleBlock {
    val duration = durationMinutes
    val newStart = (startMinute + minuteDelta).coerceIn(0, (24 * 60) - duration)
    return copy(startMinute = newStart, endMinute = newStart + duration)
}

private fun ScheduleBlock.previewFrame(pixelsPerHour: Float): PreviewFrame {
    val minH = (MinimumBlockMinutes / 60f) * pixelsPerHour
    return PreviewFrame(
        topPx    = (startMinute / 60f) * pixelsPerHour,
        heightPx = ((durationMinutes / 60f) * pixelsPerHour).coerceAtLeast(minH)
    )
}

private fun autoScrollDeltaPx(pointerYInViewport: Float, viewportHeightPx: Float, edgeZonePx: Float, maxStepPx: Float): Float {
    if (viewportHeightPx <= 0f) return 0f
    val topPressure    = ((edgeZonePx - pointerYInViewport) / edgeZonePx).coerceIn(0f, 1f)
    val bottomPressure = ((edgeZonePx - (viewportHeightPx - pointerYInViewport)) / edgeZonePx).coerceIn(0f, 1f)
    return when {
        bottomPressure > 0f -> maxStepPx * bottomPressure
        topPressure > 0f    -> -maxStepPx * topPressure
        else -> 0f
    }
}

private fun buildLayouts(tasks: List<DailyTask>): List<PositionedBlock> {
    if (tasks.isEmpty()) return emptyList()
    val clusters = mutableListOf<List<DailyTask>>()
    var current = mutableListOf<DailyTask>()
    var clusterEnd = -1
    tasks.forEach { task ->
        val s = task.schedule ?: return@forEach
        if (current.isEmpty() || s.startMinute < clusterEnd) {
            current += task; clusterEnd = maxOf(clusterEnd, s.endMinute)
        } else {
            clusters += current.toList(); current = mutableListOf(task); clusterEnd = s.endMinute
        }
    }
    if (current.isNotEmpty()) clusters += current.toList()
    return clusters.flatMap { cluster ->
        val active = mutableListOf<PositionedBlock>()
        val assigned = mutableListOf<PositionedBlock>()
        var maxColumns = 1
        cluster.forEach { task ->
            val s = task.schedule ?: return@forEach
            active.removeAll { it.task.schedule!!.endMinute <= s.startMinute }
            var col = 0; val used = active.map { it.column }.toSet()
            while (col in used) col++
            val p = PositionedBlock(task = task, column = col, columnCount = 1)
            active += p; assigned += p; maxColumns = maxOf(maxColumns, active.size)
        }
        assigned.map { it.copy(columnCount = maxColumns) }
    }
}

private fun formatClock(totalMinutes: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
