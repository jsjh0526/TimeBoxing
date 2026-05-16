package dev.jsjh.timebox.feature.timetable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.ScheduleBlock
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

private const val VisibleHours               = 6f
private val CollapsedTrayHeight              = 46.dp
private val ExpandedTrayHeight               = 222.dp
private val ReadOnlyNoticeHeight             = 36.dp
private val BlockHorizontalSpacing           = 8.dp
private val CurrentLineInset                 = 0.dp
private val CurrentLineHorizontalNudge       = (-0.7).dp
private const val AxisWidth                  = 52
private const val SnapMinutes                = 15
private const val MinimumBlockMinutes        = 15
private const val DefaultDropDurationMinutes = 60

private data class PositionedBlock(val task: DailyTask, val column: Int, val columnCount: Int)

private data class DragSession(
    val taskId: String,
    val originalSchedule: ScheduleBlock,
    val snappedSchedule: ScheduleBlock,
    val rawDeltaPx: Float = 0f,
    val pointerYInViewport: Float = 0f
)

private data class PreviewFrame(val topPx: Float, val heightPx: Float)

private data class TrayDragSession(
    val task: DailyTask,
    val pointerYInRoot: Float,
    val snappedSchedule: ScheduleBlock?
)

@Composable
fun TimetableScreen(
    modifier: Modifier = Modifier,
    tasks: List<DailyTask>,
    date: LocalDate,
    currentTime: LocalTime,
    showCurrentTime: Boolean,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onToday: () -> Unit,
    onOpenTask: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
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
    val readOnly = !showCurrentTime
    var trayExpanded by rememberSaveable { mutableStateOf(false) }
    var viewportHeightPx by remember { mutableStateOf(0f) }
    var viewportTopInRootPx by remember { mutableStateOf(0f) }
    var trayHeightPx by remember { mutableStateOf(0f) }

    Column(modifier = modifier.fillMaxSize().background(ScreenBackground)) {
        TopHeader(currentTime = currentTime)
        DateHeader(date = date, showTodayButton = !showCurrentTime, onPreviousDay = onPreviousDay, onNextDay = onNextDay, onToday = onToday)
        if (readOnly) ReadOnlyNotice()
        BoxWithConstraints(modifier = Modifier.weight(1f).background(PanelBackground)) {
            val heightForScale = maxHeight + if (readOnly) ReadOnlyNoticeHeight else 0.dp
            val hourHeight = ((heightForScale - CollapsedTrayHeight).coerceAtLeast(1.dp)) / VisibleHours
            val expandedTrayMaxHeight = minOf(ExpandedTrayHeight, maxHeight * 0.5f)
            val trayBottomPadding = if (trayHeightPx > 0f) with(density) { trayHeightPx.toDp() } else CollapsedTrayHeight
            val initialScrollHour = if (showCurrentTime) maxOf((currentMinute / 60) - 2, 0) else 13
            val pixelsPerHour = with(density) { hourHeight.toPx() }
            val edgeZonePx = with(density) { 56.dp.toPx() }
            val maxAutoScrollStepPx = with(density) { 22.dp.toPx() }
            var trayDragSession by remember { mutableStateOf<TrayDragSession?>(null) }

            fun trayDropSchedule(pointerYInRoot: Float): ScheduleBlock? {
                if (viewportHeightPx <= 0f || pixelsPerHour <= 0f) return null
                val pointerYInViewport = pointerYInRoot - viewportTopInRootPx
                if (pointerYInViewport !in 0f..viewportHeightPx) return null
                val contentY = pointerYInViewport + scrollState.value
                val snappedStart = minuteFromContentY(contentY, pixelsPerHour)
                    .coerceIn(0, (24 * 60) - DefaultDropDurationMinutes)
                return ScheduleBlock(
                    startMinute = snappedStart,
                    endMinute = snappedStart + DefaultDropDurationMinutes,
                    reminderEnabled = true
                )
            }

            fun updateTrayDrag(task: DailyTask, pointerYInRoot: Float) {
                trayDragSession = TrayDragSession(
                    task = task,
                    pointerYInRoot = pointerYInRoot,
                    snappedSchedule = trayDropSchedule(pointerYInRoot)
                )
            }

            fun finishTrayDrag(taskId: String) {
                val session = trayDragSession ?: return
                trayDragSession = null
                val schedule = session.snappedSchedule
                if (session.task.id == taskId && schedule != null) {
                    onUpdateSchedule(taskId, schedule)
                }
            }

            LaunchedEffect(date, showCurrentTime, hourHeight) {
                val initialScrollPx = with(density) { (hourHeight * initialScrollHour.toFloat()).roundToPx() }
                scrollState.scrollTo(initialScrollPx)
            }

            LaunchedEffect(trayDragSession?.task?.id, viewportHeightPx) {
                if (viewportHeightPx <= 0f) return@LaunchedEffect
                while (true) {
                    val session = trayDragSession ?: break
                    if (session.snappedSchedule == null) { delay(16); continue }
                    val pointerYInViewport = session.pointerYInRoot - viewportTopInRootPx
                    val scrollDeltaPx = autoScrollDeltaPx(
                        pointerYInViewport = pointerYInViewport,
                        viewportHeightPx = viewportHeightPx,
                        edgeZonePx = edgeZonePx,
                        maxStepPx = maxAutoScrollStepPx
                    )
                    if (scrollDeltaPx != 0f) {
                        scrollState.scrollBy(scrollDeltaPx)
                        val latest = trayDragSession
                        if (latest?.task?.id == session.task.id) {
                            trayDragSession = latest.copy(snappedSchedule = trayDropSchedule(latest.pointerYInRoot))
                        }
                    }
                    delay(16)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = trayBottomPadding)
                    .onSizeChanged { viewportHeightPx = it.height.toFloat() }
                    .onGloballyPositioned { viewportTopInRootPx = it.positionInRoot().y }
                    .verticalScroll(scrollState)
            ) {
                TimetableGrid(
                    hourHeight = hourHeight,
                    layouts = layouts,
                    currentMinute = currentMinute,
                    showCurrentTime = showCurrentTime,
                    readOnly = readOnly,
                    scrollState = scrollState,
                    viewportHeightPx = viewportHeightPx,
                    viewportTopInRootPx = viewportTopInRootPx,
                    trayPreviewTask = trayDragSession?.task,
                    trayPreviewSchedule = trayDragSession?.snappedSchedule,
                    onOpenTask = onOpenTask,
                    onToggleComplete = onToggleComplete,
                    onMoveToUnscheduled = onMoveToUnscheduled,
                    onUpdateSchedule = onUpdateSchedule
                )
            }
            BottomTray(
                tasks = unscheduled,
                expanded = trayExpanded,
                readOnly = readOnly,
                maxExpandedHeight = expandedTrayMaxHeight,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .onSizeChanged { trayHeightPx = it.height.toFloat() },
                onToggle = { trayExpanded = !trayExpanded },
                onOpenTask = onOpenTask,
                onAddTask = onAddTask,
                onDragStart = { task, pointerYInRoot -> updateTrayDrag(task, pointerYInRoot) },
                onDrag = { task, pointerYInRoot -> updateTrayDrag(task, pointerYInRoot) },
                onDragEnd = { taskId -> finishTrayDrag(taskId) }
            )
        }
    }
}

@Composable
private fun TimetableGrid(
    hourHeight: Dp,
    layouts: List<PositionedBlock>,
    currentMinute: Int,
    showCurrentTime: Boolean,
    readOnly: Boolean,
    scrollState: ScrollState,
    viewportHeightPx: Float,
    viewportTopInRootPx: Float,
    trayPreviewTask: DailyTask?,
    trayPreviewSchedule: ScheduleBlock?,
    onOpenTask: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
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
            dragSession = DragSession(taskId = taskId, originalSchedule = originalSchedule, snappedSchedule = originalSchedule, rawDeltaPx = 0f, pointerYInViewport = pointerYInViewport)
        }

        fun accumulateDrag(taskId: String, deltaPx: Float, pointerYInViewport: Float) {
            val cur = dragSession
            if (cur?.taskId != taskId) return
            val newRaw = cur.rawDeltaPx + deltaPx
            dragSession = cur.copy(rawDeltaPx = newRaw, snappedSchedule = computeSnapped(cur.originalSchedule, newRaw), pointerYInViewport = pointerYInViewport)
        }

        fun commitDrag(taskId: String) {
            val cur = dragSession ?: return
            if (cur.taskId != taskId) return
            dragSession = null
            if (cur.snappedSchedule != cur.originalSchedule) onUpdateSchedule(taskId, cur.snappedSchedule)
        }

        LaunchedEffect(dragSession?.taskId, viewportHeightPx) {
            if (viewportHeightPx <= 0f) return@LaunchedEffect
            while (true) {
                val session = dragSession ?: break
                val scrollDeltaPx = autoScrollDeltaPx(session.pointerYInViewport, viewportHeightPx, edgeZonePx, maxAutoScrollStepPx)
                if (scrollDeltaPx != 0f) {
                    val consumed = scrollState.scrollBy(scrollDeltaPx)
                    if (consumed != 0f) {
                        val latest = dragSession
                        if (latest?.taskId == session.taskId) {
                            val newRaw = latest.rawDeltaPx + consumed
                            dragSession = latest.copy(rawDeltaPx = newRaw, snappedSchedule = computeSnapped(latest.originalSchedule, newRaw))
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
                            Text(text = String.format(Locale.ENGLISH, "%02d:00", hour), modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter), style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center))
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
                        if (showCurrentTime) CurrentLine(minute = currentMinute, hourHeight = hourHeight, horizontalInset = CurrentLineInset, horizontalNudge = CurrentLineHorizontalNudge)
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

                    Box(modifier = Modifier.padding(start = left, top = top).width(width).height(height).zIndex(if (session != null) 10f else 0f)) {
                        ScheduledCard(
                            task = block.task, schedule = renderedSchedule, gestureSchedule = originalSchedule,
                            isOverlapping = block.columnCount > 1,
                            overlapCount = block.columnCount,
                            showNow = showCurrentTime && currentMinute in renderedSchedule.startMinute until renderedSchedule.endMinute,
                            isDragging = session != null, readOnly = readOnly, width = width, viewportTopInRootPx = viewportTopInRootPx,
                            onOpen = { onOpenTask(block.task.id) },
                            onToggleComplete = { onToggleComplete(block.task.id) },
                            onUnschedule = { onMoveToUnscheduled(block.task.id) },
                            onChangeDuration = { durationMinutes ->
                                val newEndMinute = (originalSchedule.startMinute + durationMinutes).coerceAtMost(24 * 60)
                                if (newEndMinute > originalSchedule.startMinute) onUpdateSchedule(block.task.id, originalSchedule.copy(endMinute = newEndMinute))
                            },
                            onDragStart = { pointerY -> beginDrag(block.task.id, originalSchedule, pointerY) },
                            onDrag = { delta, pointerY -> accumulateDrag(block.task.id, delta, pointerY) },
                            onDragEnd = { commitDrag(block.task.id) }
                        )
                    }
                }
            }

            if (trayPreviewTask != null && trayPreviewSchedule != null) {
                val previewFrame = trayPreviewSchedule.previewFrame(pixelsPerHour)
                val top = with(density) { previewFrame.topPx.toDp() }
                val height = with(density) { previewFrame.heightPx.toDp() }
                val spacing = BlockHorizontalSpacing
                Box(modifier = Modifier.padding(start = AxisWidth.dp + spacing, top = top).width(contentWidth - spacing * 2).height(height).zIndex(20f)) {
                    TrayDropPreviewCard(task = trayPreviewTask, schedule = trayPreviewSchedule)
                }
            }
        }
    }
}

@Composable
private fun TrayDropPreviewCard(task: DailyTask, schedule: ScheduleBlock) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { scaleX = 1.01f; scaleY = 1.01f }
            .shadow(18.dp, RoundedCornerShape(8.dp))
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF494962))
            .border(1.4.dp, Accent, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CompletionStub()
        Text(text = task.title, modifier = Modifier.weight(1f), style = TextStyle(color = TextPrimary, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(text = "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}", style = TextStyle(color = Color.White.copy(alpha = 0.72f), fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
        Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.12f)).padding(horizontal = 8.dp, vertical = 3.dp)) {
            Text(durationLabel(schedule.durationMinutes), style = TextStyle(color = TextPrimary.copy(alpha = 0.76f), fontSize = 10.sp, lineHeight = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun TopHeader(currentTime: LocalTime) {
    Row(modifier = Modifier.fillMaxWidth().height(69.dp).background(HeaderBackground).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text("Time Table", style = TextStyle(color = TextPrimary, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
        Text(currentTime.format(DateTimeFormatter.ofPattern("HH:mm")), style = TextStyle(color = Accent, fontSize = 16.sp, lineHeight = 24.sp))
    }
}

@Composable
private fun DateHeader(date: LocalDate, showTodayButton: Boolean, onPreviousDay: () -> Unit, onNextDay: () -> Unit, onToday: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(HeaderBackground)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GridLineHalf))
        Row(modifier = Modifier.fillMaxWidth().height(77.dp).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            CircleArrow(direction = -1, onClick = onPreviousDay)
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(date.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")), style = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Bold))
                Text(date.format(DateTimeFormatter.ofPattern("EEEE", Locale.ENGLISH)), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.3.sp))
                if (showTodayButton) TodayPill(onClick = onToday)
            }
            CircleArrow(direction = 1, onClick = onNextDay)
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(GridLineHalf))
    }
}

@Composable
private fun TodayPill(onClick: () -> Unit) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Accent.copy(alpha = 0.18f)).border(0.7.dp, Accent.copy(alpha = 0.45f), RoundedCornerShape(999.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("Today", style = TextStyle(color = Accent, fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp))
    }
}

@Composable
private fun ReadOnlyNotice() {
    Row(modifier = Modifier.fillMaxWidth().height(ReadOnlyNoticeHeight).background(Color(0xFF191919)).padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(TextSecondary))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Past and future days are view only.", style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp))
    }
}

@Composable
private fun CurrentLine(minute: Int, hourHeight: Dp, horizontalInset: Dp, horizontalNudge: Dp) {
    val top = hourHeight * (minute.toFloat() / 60f)
    Box(modifier = Modifier.offset(x = horizontalNudge, y = top - 5.dp).fillMaxWidth().padding(horizontal = horizontalInset).height(10.dp)) {
        Box(modifier = Modifier.align(Alignment.Center).fillMaxWidth().height(2.dp).background(Accent))
        Box(modifier = Modifier.align(Alignment.CenterStart).size(10.dp).clip(CircleShape).background(Accent).shadow(10.dp, CircleShape, ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f)))
        Box(modifier = Modifier.align(Alignment.CenterEnd).size(10.dp).clip(CircleShape).background(Accent).shadow(10.dp, CircleShape, ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f)))
    }
}

@Composable
private fun ScheduledCard(
    task: DailyTask, schedule: ScheduleBlock, gestureSchedule: ScheduleBlock,
    isOverlapping: Boolean, overlapCount: Int, showNow: Boolean, isDragging: Boolean, readOnly: Boolean,
    width: Dp, viewportTopInRootPx: Float,
    onOpen: () -> Unit, onToggleComplete: () -> Unit, onUnschedule: () -> Unit, onChangeDuration: (Int) -> Unit,
    onDragStart: (Float) -> Unit, onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit
) {
    var cardTopInRootPx by remember(task.id, gestureSchedule.startMinute, gestureSchedule.endMinute) { mutableStateOf(0f) }
    var durationExpanded by remember(task.id, gestureSchedule.startMinute, gestureSchedule.endMinute) { mutableStateOf(false) }
    val narrow = width < 140.dp
    val durationMinutes = schedule.durationMinutes
    val durationOptions = remember(schedule.startMinute) { durationMinuteOptions(schedule.startMinute) }
    val timeText = "${formatClock(schedule.startMinute)} - ${formatClock(schedule.endMinute)}"
    val hideTimeText = durationMinutes <= 30
    val prefersExpandedLayout = durationMinutes >= 45
    val showFullTimeRow = durationMinutes >= 60
    val overlapCompact = isOverlapping
    val completed = task.isCompleted
    val targetTitleColor = if (completed) TextMuted.copy(alpha = 0.72f) else TextPrimary
    val targetDetailColor = if (completed) TextMuted.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.75f)
    val targetTagTextColor = if (completed) TextMuted.copy(alpha = 0.62f) else Color.White.copy(alpha = 0.72f)
    val targetCardFill = when {
        isDragging -> Color(0xFF494962)
        completed  -> Color(0xFF2D2D36)
        else       -> CardBackground
    }

    val (targetBorderWidth, targetBorderColor) = when {
        completed && !isDragging    -> 0.7.dp to Accent.copy(alpha = 0.12f)
        showNow && !isDragging     -> 1.4.dp to Accent
        task.isBig3 && !isDragging -> 1.4.dp to Color.White.copy(alpha = 0.9f)
        isDragging                 -> 0.7.dp to Accent.copy(alpha = 0.45f)
        else                       -> 0.7.dp to Accent.copy(alpha = 0.2f)
    }
    val titleColor by animateColorAsState(targetTitleColor, tween(180), label = "timetableTitleColor")
    val detailColor by animateColorAsState(targetDetailColor, tween(180), label = "timetableDetailColor")
    val tagTextColor by animateColorAsState(targetTagTextColor, tween(180), label = "timetableTagColor")
    val cardFill by animateColorAsState(targetCardFill, tween(180), label = "timetableCardFill")
    val borderColor by animateColorAsState(targetBorderColor, tween(180), label = "timetableBorderColor")
    val borderWidth by animateDpAsState(targetBorderWidth, tween(180), label = "timetableBorderWidth")

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { cardTopInRootPx = it.positionInRoot().y }
            .graphicsLayer { if (isDragging) { scaleX = 1.015f; scaleY = 1.015f } }
            .then(if (showNow && !isDragging) Modifier.shadow(0.dp, RoundedCornerShape(8.dp), ambientColor = Accent.copy(0.5f), spotColor = Accent.copy(0.5f)) else if (isDragging) Modifier.shadow(20.dp, RoundedCornerShape(8.dp)) else Modifier)
            .clip(RoundedCornerShape(8.dp))
            .background(cardFill)
            .border(borderWidth, borderColor, RoundedCornerShape(8.dp))
            .clickable(enabled = !readOnly && !isDragging, onClick = onOpen)
            .then(
                if (readOnly) Modifier else Modifier.pointerInput(task.id, gestureSchedule.startMinute, gestureSchedule.endMinute, viewportTopInRootPx) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset -> onDragStart(cardTopInRootPx + offset.y - viewportTopInRootPx) },
                        onDragCancel = onDragEnd, onDragEnd = onDragEnd,
                        onDrag = { change, dragAmount -> change.consume(); onDrag(dragAmount.y, cardTopInRootPx + change.position.y - viewportTopInRootPx) }
                    )
                }
            )
    ) {
        val cardH = maxHeight

        if (overlapCompact) {
            if (durationMinutes <= 15) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 7.dp, end = 7.dp)
                ) {
                    if (width >= 82.dp) {
                        Text(
                            text = task.title,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(end = if (readOnly) 0.dp else 20.dp),
                            style = TextStyle(color = titleColor, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    if (!readOnly) {
                        CloseIcon(Color.White.copy(alpha = 0.52f), onClick = onUnschedule, modifier = Modifier.align(Alignment.CenterEnd))
                    }
                }
                return@BoxWithConstraints
            }
            val useTallOverlapLayout = durationMinutes >= 60 && cardH >= 60.dp
            if (useTallOverlapLayout) {
                val showOverlapDuration = width >= 150.dp
                val showOverlapCheckbox = overlapCount <= 3 && width >= 84.dp
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    if (showOverlapCheckbox) {
                        CompletionStub(
                            completed = task.isCompleted,
                            enabled = !readOnly && !isDragging,
                            onClick = onToggleComplete,
                            modifier = Modifier.align(Alignment.TopStart)
                        )
                    }
                    Row(
                        modifier = Modifier.align(Alignment.TopEnd),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (showOverlapDuration) {
                            DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = false)
                        }
                        if (!readOnly) {
                            CloseIcon(Color.White.copy(alpha = 0.55f), onClick = onUnschedule)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(top = 34.dp, end = 4.dp)
                            .fillMaxSize(),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = task.title,
                            style = TextStyle(color = titleColor, fontSize = if (width < 130.dp) 13.sp else 15.sp, lineHeight = if (width < 130.dp) 16.sp else 19.sp, fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            } else {
                val wrapOverlapTitle = cardH >= 80.dp && width >= 110.dp
                val showOverlapDuration = width >= 120.dp
                Column(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(text = task.title, modifier = Modifier.weight(1f), style = TextStyle(color = titleColor, fontSize = if (width < 130.dp) 12.sp else 13.sp, lineHeight = if (width < 130.dp) 15.sp else 16.sp, fontWeight = FontWeight.SemiBold), maxLines = if (wrapOverlapTitle) 2 else 1, overflow = TextOverflow.Ellipsis)
                        if (showOverlapDuration) {
                            DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = width < 150.dp)
                        }
                        if (!readOnly) CloseIcon(Color.White.copy(alpha = 0.55f), onClick = onUnschedule)
                    }
                }
            }
        } else when {
            cardH < 36.dp -> {
                Row(modifier = Modifier.fillMaxSize().padding(start = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (durationMinutes >= 30) {
                        CompletionStub(completed = task.isCompleted, enabled = !readOnly && !isDragging, onClick = onToggleComplete)
                    }
                    Text(text = task.title, modifier = Modifier.weight(1f), style = TextStyle(color = titleColor, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (!hideTimeText) Text(text = timeText, style = TextStyle(color = detailColor, fontSize = 9.sp, lineHeight = 12.sp, fontFamily = FontFamily.Monospace), maxLines = 1)
                    DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = true)
                    if (!readOnly) CloseIcon(Color.White.copy(alpha = 0.5f), onClick = onUnschedule)
                }
            }
            cardH < 60.dp && !prefersExpandedLayout -> {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        CompletionStub(completed = task.isCompleted, enabled = !readOnly && !isDragging, onClick = onToggleComplete)
                        Spacer(Modifier.width(7.dp))
                        Text(text = task.title, modifier = Modifier.weight(1f), style = TextStyle(color = titleColor, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.width(8.dp))
                        DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = true)
                    }
                    if (!hideTimeText) Text(text = timeText, style = TextStyle(color = detailColor, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    else Spacer(Modifier.height(1.dp))
                }
            }
            cardH < 70.dp && !prefersExpandedLayout -> {
                Column(modifier = Modifier.fillMaxSize().padding(8.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CompletionStub(completed = task.isCompleted, enabled = !readOnly && !isDragging, onClick = onToggleComplete)
                        Text(text = task.title, modifier = Modifier.weight(1f), style = TextStyle(color = titleColor, fontSize = if (narrow) 12.sp else 13.sp, lineHeight = if (narrow) 15.sp else 16.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = true)
                        if (!readOnly) CloseIcon(Color.White.copy(alpha = 0.5f), onClick = onUnschedule)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        if (!hideTimeText) Text(text = timeText, style = TextStyle(color = detailColor, fontSize = 10.sp, lineHeight = 14.sp, fontFamily = FontFamily.Monospace), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        else Spacer(Modifier.width(1.dp))
                        if (showNow) NowBadge()
                    }
                }
            }
            else -> {
                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
                    val showTagRow = durationMinutes > 45 && task.tags.isNotEmpty()
                    val wrapTitle = durationMinutes >= 90 && width >= 180.dp
                    var titleLineCount by remember(task.id, width, durationMinutes, wrapTitle) { mutableStateOf(1) }
                    val titleActuallyWrapped = wrapTitle && titleLineCount > 1
                    Column(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .fillMaxWidth()
                            .padding(bottom = if (showFullTimeRow) 18.dp else 0.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CompletionStub(completed = task.isCompleted, enabled = !readOnly && !isDragging, onClick = onToggleComplete)
                            Spacer(Modifier.width(8.dp))
                            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(text = task.title, modifier = Modifier.weight(1f, fill = false), style = TextStyle(color = titleColor, fontSize = if (narrow) 12.sp else 14.sp, lineHeight = if (narrow) 15.sp else 17.5.sp, fontWeight = FontWeight.SemiBold), maxLines = if (wrapTitle) 2 else 1, overflow = TextOverflow.Ellipsis, onTextLayout = { result ->
                                    val nextLineCount = result.lineCount
                                    if (titleLineCount != nextLineCount) titleLineCount = nextLineCount
                                })
                                if (task.isBig3) Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Big3Badge).padding(horizontal = 6.dp, vertical = 2.dp)) { Text("Big 3", style = TextStyle(color = Big3Text, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.45.sp)) }
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                DurationChip(durationMinutes = durationMinutes, expanded = durationExpanded, readOnly = readOnly || isDragging, options = durationOptions, onExpandedChange = { durationExpanded = it }, onSelect = onChangeDuration, compact = narrow)
                                if (!readOnly) CloseIcon(Color.White.copy(alpha = 0.6f), onClick = onUnschedule)
                            }
                        }
                    }
                    if (showTagRow) {
                        TimetableTagRow(
                            tags = task.tags,
                            color = tagTextColor,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 32.dp, top = if (titleActuallyWrapped) 48.dp else if (durationMinutes <= 60) 27.dp else 30.dp, end = 10.dp)
                                .fillMaxWidth()
                                .heightIn(max = 28.dp)
                                .clipToBounds()
                        )
                    }
                    if (showFullTimeRow) {
                        Row(modifier = Modifier.align(Alignment.BottomStart), verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(24.dp))
                            Text(text = timeText, style = TextStyle(color = detailColor, fontSize = if (narrow) 10.sp else 11.sp, lineHeight = 16.5.sp, fontFamily = FontFamily.Monospace))
                            if (showNow) {
                                Spacer(Modifier.width(8.dp))
                                NowBadge()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TimetableTagRow(tags: List<String>, color: Color, modifier: Modifier = Modifier) {
    val visibleTags = if (tags.size > 4) tags.take(3) else tags.take(4)
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        visibleTags.forEach { tag -> TimetableTagChip("#$tag", color) }
        if (tags.size > visibleTags.size) TimetableTagChip("...", color)
    }
}

@Composable
private fun TimetableTagChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(Color.Black.copy(alpha = 0.1f))
            .padding(horizontal = 6.dp, vertical = 1.dp)
    ) {
        Text(label, style = TextStyle(color = color, fontSize = 9.sp, lineHeight = 12.sp), maxLines = 1)
    }
}

@Composable
private fun NowBadge() {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(Accent.copy(alpha = 0.3f)).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text("Now", style = TextStyle(color = Accent, fontSize = 9.sp, lineHeight = 13.5.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.45.sp))
    }
}

@Composable
private fun DurationChip(durationMinutes: Int, expanded: Boolean, readOnly: Boolean, options: List<Int>, onExpandedChange: (Boolean) -> Unit, onSelect: (Int) -> Unit, compact: Boolean = false) {
    Box {
        Box(modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(Color.Black.copy(alpha = 0.12f)).clickable(enabled = !readOnly) { onExpandedChange(true) }.padding(horizontal = if (compact) 6.dp else 14.dp, vertical = if (compact) 3.dp else 6.dp), contentAlignment = Alignment.Center) {
            Text(text = durationLabel(durationMinutes, compact), style = TextStyle(color = TextPrimary.copy(alpha = 0.76f), fontSize = if (compact) 9.sp else 13.sp, lineHeight = if (compact) 12.sp else 19.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onExpandedChange(false) }, modifier = Modifier.heightIn(max = 280.dp).clip(RoundedCornerShape(12.dp)).background(HeaderBackground)) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(durationLabel(option), style = TextStyle(color = if (option == durationMinutes) Accent else TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontFamily = FontFamily.Monospace)) }, onClick = { onExpandedChange(false); onSelect(option) })
            }
        }
    }
}

@Composable
private fun CompletionStub(
    completed: Boolean = false,
    enabled: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {}
) {
    val fillColor by animateColorAsState(
        targetValue = if (completed) Accent else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "timetableCompletionFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (completed) Accent.copy(alpha = 0f) else Color.White.copy(alpha = 0.7f),
        animationSpec = tween(durationMillis = 160),
        label = "timetableCompletionBorder"
    )

    Box(
        modifier = modifier
            .size(16.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(fillColor)
            .border(1.4.dp, borderColor, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (completed) {
            Canvas(modifier = Modifier.size(9.dp)) {
                val stroke = 1.5.dp.toPx()
                drawLine(Color.White, Offset(size.width * 0.18f, size.height * 0.55f), Offset(size.width * 0.42f, size.height * 0.78f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(size.width * 0.42f, size.height * 0.78f), Offset(size.width * 0.84f, size.height * 0.25f), stroke, StrokeCap.Round)
            }
        }
    }
}

@Composable
private fun BottomTray(
    tasks: List<DailyTask>, expanded: Boolean, readOnly: Boolean, maxExpandedHeight: Dp,
    modifier: Modifier = Modifier, onToggle: () -> Unit, onOpenTask: (String) -> Unit, onAddTask: () -> Unit,
    onDragStart: (DailyTask, Float) -> Unit, onDrag: (DailyTask, Float) -> Unit, onDragEnd: (String) -> Unit
) {
    val trayScroll = rememberScrollState()
    Column(modifier = modifier.fillMaxWidth().then(if (expanded) Modifier.heightIn(max = maxExpandedHeight) else Modifier).background(Color(0xFF1E1E1E)).animateContentSize()) {
        Row(modifier = Modifier.fillMaxWidth().height(44.dp).clickable(onClick = onToggle).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ClockMiniIcon(TextSecondary); Spacer(modifier = Modifier.width(8.dp))
                Text("Unscheduled", style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)); Spacer(modifier = Modifier.width(8.dp))
                Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(Color(0xFF2A2A2A)).padding(horizontal = 7.dp, vertical = 2.dp)) { Text(tasks.size.toString(), style = TextStyle(color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp)) }
            }
            ChevronUpIcon(TextSecondary, expanded = expanded)
        }
        if (expanded) {
            Column(modifier = Modifier.fillMaxWidth().weight(1f, fill = false).verticalScroll(trayScroll).padding(start = 16.dp, end = 16.dp, bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (tasks.isEmpty()) {
                    Text("All tasks are scheduled.", style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 18.sp))
                } else {
                    tasks.forEach { task ->
                        var itemTopInRootPx by remember(task.id) { mutableStateOf(0f) }
                        val updatedItemTop by rememberUpdatedState(itemTopInRootPx)
                        val updatedOnDragStart by rememberUpdatedState(onDragStart)
                        val updatedOnDrag by rememberUpdatedState(onDrag)
                        val updatedOnDragEnd by rememberUpdatedState(onDragEnd)
                        val completed = task.isCompleted
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onGloballyPositioned { itemTopInRootPx = it.positionInRoot().y }
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (completed) Color(0xFF242424) else Color(0xFF2A2A2A))
                                .clickable(enabled = !readOnly) { onOpenTask(task.id) }
                                .then(
                                    if (readOnly) Modifier else Modifier.pointerInput(task.id) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = { offset -> updatedOnDragStart(task, updatedItemTop + offset.y) },
                                            onDragCancel = { updatedOnDragEnd(task.id) },
                                            onDragEnd    = { updatedOnDragEnd(task.id) },
                                            onDrag = { change, _ ->
                                                change.consume()
                                                updatedOnDrag(task, updatedItemTop + change.position.y)
                                            }
                                        )
                                    }
                                )
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(Accent.copy(alpha = if (completed) 0.18f else 0.35f)))
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(
                                    task.title,
                                    style = TextStyle(
                                        color = if (completed) TextSecondary else TextPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                                    ),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (task.tags.isNotEmpty()) {
                                    Text(
                                        task.tags.joinToString("  ") { "#$it" },
                                        style = TextStyle(
                                            color = if (completed) TextSecondary.copy(alpha = 0.65f) else TextSecondary,
                                            fontSize = 11.sp,
                                            lineHeight = 15.sp,
                                            textDecoration = if (completed) TextDecoration.LineThrough else TextDecoration.None
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
                if (!readOnly) {
                    Row(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Accent).clickable(onClick = onAddTask).padding(vertical = 14.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
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
            if (direction < 0) { drawLine(Color.White, Offset(size.width * 0.62f, size.height * 0.2f), Offset(size.width * 0.38f, size.height * 0.5f), stroke, StrokeCap.Round); drawLine(Color.White, Offset(size.width * 0.38f, size.height * 0.5f), Offset(size.width * 0.62f, size.height * 0.8f), stroke, StrokeCap.Round) }
            else { drawLine(Color.White, Offset(size.width * 0.38f, size.height * 0.2f), Offset(size.width * 0.62f, size.height * 0.5f), stroke, StrokeCap.Round); drawLine(Color.White, Offset(size.width * 0.62f, size.height * 0.5f), Offset(size.width * 0.38f, size.height * 0.8f), stroke, StrokeCap.Round) }
        }
    }
}

@Composable
private fun CloseIcon(color: Color, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.size(17.dp).clickable(onClick = onClick)) {
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
        if (expanded) { drawLine(color, Offset(size.width * 0.25f, size.height * 0.62f), Offset(size.width * 0.5f, size.height * 0.38f), stroke, StrokeCap.Round); drawLine(color, Offset(size.width * 0.5f, size.height * 0.38f), Offset(size.width * 0.75f, size.height * 0.62f), stroke, StrokeCap.Round) }
        else { drawLine(color, Offset(size.width * 0.25f, size.height * 0.38f), Offset(size.width * 0.5f, size.height * 0.62f), stroke, StrokeCap.Round); drawLine(color, Offset(size.width * 0.5f, size.height * 0.62f), Offset(size.width * 0.75f, size.height * 0.38f), stroke, StrokeCap.Round) }
    }
}

private fun snappedMinuteDelta(deltaPx: Float, pixelsPerHour: Float): Int {
    val rawSteps = ((deltaPx / pixelsPerHour) * 60f) / SnapMinutes
    return rawSteps.roundToInt() * SnapMinutes
}

private fun minuteFromContentY(contentYPx: Float, pixelsPerHour: Float): Int {
    val rawSteps = ((contentYPx / pixelsPerHour) * 60f) / SnapMinutes
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
    return PreviewFrame(topPx = (startMinute / 60f) * pixelsPerHour, heightPx = ((durationMinutes / 60f) * pixelsPerHour).coerceAtLeast(minH))
}

private fun autoScrollDeltaPx(pointerYInViewport: Float, viewportHeightPx: Float, edgeZonePx: Float, maxStepPx: Float): Float {
    if (viewportHeightPx <= 0f) return 0f
    val topPressure    = ((edgeZonePx - pointerYInViewport) / edgeZonePx).coerceIn(0f, 1f)
    val bottomPressure = ((edgeZonePx - (viewportHeightPx - pointerYInViewport)) / edgeZonePx).coerceIn(0f, 1f)
    return when { bottomPressure > 0f -> maxStepPx * bottomPressure; topPressure > 0f -> -maxStepPx * topPressure; else -> 0f }
}

private fun buildLayouts(tasks: List<DailyTask>): List<PositionedBlock> {
    if (tasks.isEmpty()) return emptyList()
    val clusters = mutableListOf<List<DailyTask>>(); var current = mutableListOf<DailyTask>(); var clusterEnd = -1
    tasks.forEach { task ->
        val s = task.schedule ?: return@forEach
        if (current.isEmpty() || s.startMinute < clusterEnd) { current += task; clusterEnd = maxOf(clusterEnd, s.endMinute) }
        else { clusters += current.toList(); current = mutableListOf(task); clusterEnd = s.endMinute }
    }
    if (current.isNotEmpty()) clusters += current.toList()
    return clusters.flatMap { cluster ->
        val active = mutableListOf<PositionedBlock>(); val assigned = mutableListOf<PositionedBlock>(); var maxColumns = 1
        cluster.forEach { task ->
            val s = task.schedule ?: return@forEach
            active.removeAll { it.task.schedule!!.endMinute <= s.startMinute }
            var col = 0; val used = active.map { it.column }.toSet(); while (col in used) col++
            val p = PositionedBlock(task = task, column = col, columnCount = 1)
            active += p; assigned += p; maxColumns = maxOf(maxColumns, active.size)
        }
        assigned.map { it.copy(columnCount = maxColumns) }
    }
}

private fun formatClock(totalMinutes: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)
private fun durationLabel(durationMinutes: Int, compact: Boolean = false): String = "${durationMinutes}m"
private fun durationMinuteOptions(startMinute: Int): List<Int> {
    val maxDuration = (24 * 60 - startMinute).coerceAtLeast(MinimumBlockMinutes)
    return generateSequence(MinimumBlockMinutes) { current -> val next = current + SnapMinutes; if (next <= maxDuration) next else null }.toList()
}
