package com.example.timeboxing.feature.todo

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.DailyTaskSource
import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import com.example.timeboxing.domain.model.occursOn
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale
import kotlin.math.roundToInt

// Colors
private val ScreenBackground = Color(0xFF121212)
private val CardBackground   = Color(0xFF2A2A2A)
private val CardDragging     = Color(0xFF3A3A3A)
private val CardMuted        = Color(0xFF363636)
private val Accent           = Color(0xFF8687E7)
private val TextPrimary      = Color.White
private val TextSecondary    = Color(0xFF99A1AF)
private val TextMuted        = Color(0xFF6A7282)
private val Divider          = Color(0xFF333333)
private val TagBackground    = Color(0xFF444444)
private val Priority         = Color(0xFFFFC300)
private val Big3Label        = Color(0xFFFF9680)
private val RecurringSection = Color(0xFFE5DDA8)
private val RecurringFill    = Color(0x1A8687E7)
private val RecurringText    = Color(0xFF8687E7)

// Layout tokens
private val SCREEN_PAD         = 24.dp
private val SECTION_GAP        = 32.dp
private val HEADER_GAP         = 16.dp
private val ITEM_GAP           = 12.dp
private val CARD_RADIUS        = 10.dp
private val CARD_PAD_H         = 12.dp
private val CARD_PAD_V         = 14.dp
private val CARD_MIN_H         = 72.dp
private val CARD_COMPACT_MIN_H = 56.dp
private val DRAG_HANDLE_W      = 16.dp

@Composable
fun TodoScreen(
    modifier: Modifier = Modifier,
    tasks: List<DailyTask>,
    date: LocalDate,
    otherHabits: List<DailyTask> = emptyList(),
    yesterdayIncompleteTasks: List<DailyTask> = emptyList(),
    recurrenceByTemplateId: Map<String, RecurrenceRule?> = emptyMap(),
    onQuickAddTask: (String) -> Unit,
    onOpenAddTaskEditor: (String) -> Unit,
    onCarryOverYesterday: () -> Unit,
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    // Move a task to its final index after drag ends.
    onReorderTask: (String, Int) -> Unit
) {
    var otherHabitsExpanded by remember { mutableStateOf(false) }
    var yesterdayExpanded by remember { mutableStateOf(false) }
    // Disable LazyColumn scrolling while any section is being dragged.
    var globalDragging by remember { mutableStateOf(false) }

    val big3 = tasks.filter { it.isBig3 }
    val brainDump = tasks.filter { !it.isBig3 && it.source != DailyTaskSource.RECURRING }
    val recurring = tasks.filter { task ->
        task.source == DailyTaskSource.RECURRING && !task.isBig3 && run {
            val rule = task.templateId?.let { tid -> recurrenceByTemplateId[tid] }
            rule?.occursOn(date.dayOfWeek) ?: false
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().background(ScreenBackground),
        userScrollEnabled = !globalDragging,
        contentPadding = PaddingValues(start = SCREEN_PAD, end = SCREEN_PAD, top = 8.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                "Tasks & Habits",
                style = TextStyle(color = TextPrimary, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            )
        }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item { InputRow(onQuickAddTask = onQuickAddTask, onOpenAddTaskEditor = onOpenAddTaskEditor) }
        if (yesterdayIncompleteTasks.isNotEmpty()) {
            item { Spacer(Modifier.height(HEADER_GAP)) }
            item {
                YesterdayIncompleteSection(
                    tasks = yesterdayIncompleteTasks,
                    expanded = yesterdayExpanded,
                    onToggle = { yesterdayExpanded = !yesterdayExpanded },
                    onCarryOver = {
                        onCarryOverYesterday()
                        yesterdayExpanded = false
                    }
                )
            }
        }

        // TODAY'S BIG 3
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { Big3Header() }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (big3.isEmpty()) EmptySectionHint("Star tasks below to prioritize")
            else DraggableSection(
                tasks = big3,
                bordered = true,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask
            )
        }

        // BRAIN DUMP
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader("BRAIN DUMP", TextSecondary, brainDump.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (brainDump.isEmpty()) EmptySectionHint("No tasks yet. Add one above!")
            else DraggableSection(
                tasks = brainDump,
                bordered = false,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask
            )
        }

        // RECURRING HABITS
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader("RECURRING HABITS", RecurringSection, recurring.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (recurring.isEmpty()) EmptySectionHint("No recurring habits for today.")
            else DraggableSection(
                tasks = recurring,
                bordered = false,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask
            )
        }

        // OTHER HABITS
        if (otherHabits.isNotEmpty()) {
            item { Spacer(Modifier.height(HEADER_GAP)) }
            item {
                OtherHabitsHeader(
                    count = otherHabits.size, expanded = otherHabitsExpanded,
                    onToggle = { otherHabitsExpanded = !otherHabitsExpanded }
                )
            }
            if (otherHabitsExpanded) {
                item { Spacer(Modifier.height(HEADER_GAP)) }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                        otherHabits.forEach { task ->
                            CompactCard(
                                task = task,
                                recurrenceRule = task.templateId?.let { recurrenceByTemplateId[it] },
                                onToggleComplete = onToggleComplete,
                                onOpenTask = onOpenTask
                            )
                        }
                    }
                }
            }
        }
    }
}

// Draggable task section.
// Cards lift while dragging and reorder once on drag release.
@Composable
private fun DraggableSection(
    tasks: List<DailyTask>,
    bordered: Boolean,
    recurrenceByTemplateId: Map<String, RecurrenceRule?>,
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onSetDragging: (Boolean) -> Unit,
    onReorder: (taskId: String, toIndex: Int) -> Unit
) {
    val density = LocalDensity.current
    val fallbackHeightPx = with(density) { CARD_MIN_H.toPx() }
    val itemGapPx = with(density) { ITEM_GAP.toPx() }
    val dragShadowPx = with(density) { 24.dp.toPx() }
    val measuredHeights = remember(tasks.map { it.id }) { mutableStateMapOf<String, Int>() }

    var draggingFrom by remember { mutableStateOf(-1) }
    var dragTotalY by remember { mutableStateOf(0f) }

    val cardHeights = tasks.map { task -> (measuredHeights[task.id]?.toFloat() ?: fallbackHeightPx) }
    val cardTops = buildList(tasks.size) {
        var currentTop = 0f
        tasks.forEachIndexed { index, _ ->
            add(currentTop)
            currentTop += cardHeights[index]
            if (index < tasks.lastIndex) currentTop += itemGapPx
        }
    }
    val totalHeightPx = if (tasks.isEmpty()) 0f else cardTops.last() + cardHeights.last()
    val draggedHeightPx = if (draggingFrom in tasks.indices) cardHeights[draggingFrom] else 0f
    val draggedSlotPx = if (draggingFrom >= 0) draggedHeightPx + itemGapPx else 0f

    val clampedDragY = if (draggingFrom in tasks.indices) {
        val minY = -cardTops[draggingFrom]
        val maxY = (totalHeightPx - draggedHeightPx) - cardTops[draggingFrom]
        dragTotalY.coerceIn(minY, maxY)
    } else 0f

    val targetIndex = if (draggingFrom in tasks.indices) {
        val centers = cardTops.mapIndexed { index, top -> top + cardHeights[index] / 2f }
        val draggedCenterY = centers[draggingFrom] + clampedDragY
        var candidate = draggingFrom

        while (candidate < tasks.lastIndex) {
            val boundary = (centers[candidate] + centers[candidate + 1]) / 2f
            if (draggedCenterY > boundary) candidate++ else break
        }
        while (candidate > 0) {
            val boundary = (centers[candidate - 1] + centers[candidate]) / 2f
            if (draggedCenterY < boundary) candidate-- else break
        }

        candidate.coerceIn(0, tasks.lastIndex)
    } else -1

    // 최신 드래그 콜백 참조를 유지합니다.
    val latestOnSetDragging by rememberUpdatedState(onSetDragging)
    val latestOnReorder by rememberUpdatedState(onReorder)
    Column {
        tasks.forEachIndexed { index, task ->
            val isDragging = draggingFrom == index

            // 드래그 중 주변 카드 이동량 계산
            val displacedY = when {
                draggingFrom < 0 || isDragging -> 0f
                draggingFrom < targetIndex && index in (draggingFrom + 1)..targetIndex -> -draggedSlotPx
                draggingFrom > targetIndex && index in targetIndex until draggingFrom -> draggedSlotPx
                else -> 0f
            }
            if (index > 0) {
                Spacer(Modifier.height(ITEM_GAP))
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        measuredHeights[task.id] = coordinates.size.height
                    }
                    .zIndex(if (isDragging) 10f else if (displacedY != 0f) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            translationY = clampedDragY
                            scaleX = 1.03f
                            scaleY = 1.03f
                            shadowElevation = dragShadowPx
                            shape = RoundedCornerShape(CARD_RADIUS)
                            clip = true
                        } else if (displacedY != 0f) {
                            translationY = displacedY
                        }
                    }
            ) {
                TaskCard(
                    task = task,
                    bordered = bordered,
                    isDragging = isDragging,
                    recurrenceRule = task.templateId?.let { recurrenceByTemplateId[it] },
                    onToggleBig3 = onToggleBig3,
                    onToggleComplete = onToggleComplete,
                    // 드래그 중에는 다른 카드를 열지 않습니다.
                    onOpenTask = if (draggingFrom >= 0 && !isDragging) ({}) else onOpenTask,
                    onDragStart = {
                        draggingFrom = index
                        dragTotalY = 0f
                        latestOnSetDragging(true)
                    },
                    onDrag = { delta -> dragTotalY += delta },
                    onDragEnd = {
                        val from = draggingFrom
                        if (from >= 0) {
                            val to = targetIndex
                            if (from != to) latestOnReorder(tasks[from].id, to)
                        }
                        draggingFrom = -1
                        dragTotalY = 0f
                        latestOnSetDragging(false)
                    }
                )
            }

            // 삽입 indicator는 나중에 실제 렌더링에 연결할 예정입니다.
        }
    }
}

@Composable
private fun InsertionIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(Accent.copy(alpha = 0.7f), RoundedCornerShape(1.dp))
    )
}

// 빠른 입력

@Composable
private fun InputRow(onQuickAddTask: (String) -> Unit, onOpenAddTaskEditor: (String) -> Unit) {
    var input by remember { mutableStateOf("") }
    fun consume(action: (String) -> Unit) {
        val t = input.trim(); if (t.isNotEmpty()) { action(t); input = "" }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier.weight(1f).height(56.dp)
                .clip(RoundedCornerShape(14.dp)).background(CardBackground).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = input, onValueChange = { input = it },
                modifier = Modifier.fillMaxWidth(),
                textStyle = TextStyle(color = TextPrimary, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { consume(onQuickAddTask) }),
                singleLine = true,
                decorationBox = { inner ->
                    if (input.isEmpty()) Text("Add a new task...", style = TextStyle(color = TextMuted, fontSize = 16.sp))
                    inner()
                }
            )
        }
        Box(
            modifier = Modifier.size(56.dp)
                .shadow(10.dp, RoundedCornerShape(14.dp), ambientColor = Accent.copy(0.3f), spotColor = Accent.copy(0.3f))
                .clip(RoundedCornerShape(14.dp)).background(Accent)
                .clickable { val t = input.trim(); onOpenAddTaskEditor(t); if (t.isNotEmpty()) input = "" },
            contentAlignment = Alignment.Center
        ) { PlusIcon(Color.White) }
    }
}

@Composable
private fun YesterdayIncompleteSection(
    tasks: List<DailyTask>,
    expanded: Boolean,
    onToggle: () -> Unit,
    onCarryOver: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CardBackground)
            .border(0.7.dp, Divider, RoundedCornerShape(CARD_RADIUS))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    "YESTERDAY LEFTOVER",
                    style = TextStyle(color = Accent, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp)
                )
                Spacer(Modifier.width(8.dp))
                CountPill(tasks.size)
            }
            if (expanded) ChevronUpIcon(TextSecondary) else ChevronDownIcon(TextSecondary)
        }
        if (expanded) {
            Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(Divider))
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                tasks.forEach { task ->
                    YesterdayTaskPreview(task)
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Accent)
                        .clickable(onClick = onCarryOver),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Move all to today",
                        style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }
    }
}

@Composable
private fun YesterdayTaskPreview(task: DailyTask) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(9.dp))
            .background(Color.Black.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(13.dp).clip(CircleShape).border(1.4.dp, TextSecondary, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                task.title,
                style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (task.tags.isNotEmpty()) {
                Text(
                    task.tags.take(3).joinToString("  ") { "#$it" },
                    style = TextStyle(color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Section headers

@Composable
private fun Big3Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Big3Label))
        Spacer(Modifier.width(8.dp))
        Text("TODAY'S BIG 3", style = TextStyle(color = Big3Label, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp))
    }
}

@Composable
private fun SectionHeader(title: String, color: Color, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = TextStyle(color = color, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp))
        Spacer(Modifier.width(8.dp))
        CountPill(count)
    }
}

@Composable
private fun EmptySectionHint(message: String) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
        Text(text = message, style = TextStyle(color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp))
    }
}

// Task cards

@Composable
private fun TaskCard(
    task: DailyTask,
    bordered: Boolean,
    isDragging: Boolean,
    recurrenceRule: RecurrenceRule?,
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val isRecurring = task.source == DailyTaskSource.RECURRING

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_MIN_H)
            .then(
                if (bordered && !isDragging) Modifier.shadow(
                    6.dp, RoundedCornerShape(CARD_RADIUS),
                    ambientColor = Accent.copy(0.12f), spotColor = Accent.copy(0.12f)
                ) else Modifier
            )
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(if (isDragging) CardDragging else CardBackground)
            .then(if (bordered) Modifier.border(1.dp, Accent, RoundedCornerShape(CARD_RADIUS)) else Modifier)
            .alpha(if (task.isCompleted) 0.52f else 1f)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = CARD_PAD_H, vertical = CARD_PAD_V)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd)
            Spacer(Modifier.width(6.dp))
            CompletionCircle(completed = task.isCompleted, onClick = { onToggleComplete(task.id) })
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = if (task.isCompleted) TextMuted else TextPrimary,
                        fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                if (task.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.tags.forEach { tag -> TagChip("#$tag") }
                    }
                }
                if (isRecurring) RecurringBadge(rule = recurrenceRule)
                task.schedule?.let { sch ->
                    Text(
                        "${formatClock(sch.startMinute)} - ${formatClock(sch.endMinute)}",
                        style = TextStyle(color = TextSecondary, fontSize = 10.sp, lineHeight = 15.sp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Big3Toggle(selected = task.isBig3, onClick = { onToggleBig3(task.id) })
        }
    }
}

// Compact cards for Other Habits

@Composable
private fun CompactCard(
    task: DailyTask,
    recurrenceRule: RecurrenceRule?,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_COMPACT_MIN_H)
            .alpha(if (task.isCompleted) 0.52f else 0.5f)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CardBackground)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = CARD_PAD_H, vertical = CARD_PAD_V)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(DRAG_HANDLE_W))
            Spacer(Modifier.width(6.dp))
            CompletionCircle(completed = task.isCompleted, onClick = { onToggleComplete(task.id) })
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = if (task.isCompleted) TextMuted else TextPrimary,
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(recurrenceLabel(recurrenceRule), style = TextStyle(color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp))
            }
        }
    }
}

// Drag handle only reports long-press drag deltas.
// DraggableSection decides the final reorder target.

@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag      by rememberUpdatedState(onDrag)
    val latestOnDragEnd   by rememberUpdatedState(onDragEnd)

    Box(
        modifier = Modifier
            .size(width = DRAG_HANDLE_W, height = 44.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart  = { latestOnDragStart() },
                    onDragEnd    = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragEnd() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        latestOnDrag(dragAmount.y)
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.DragIndicator,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(20.dp)
        )
    }
}

// Small UI components

@Composable
private fun Big3Toggle(selected: Boolean, onClick: () -> Unit) {
    Icon(
        imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
        contentDescription = null,
        tint = if (selected) Priority else TextSecondary,
        modifier = Modifier.size(20.dp).clickable(onClick = onClick)
    )
}

@Composable
private fun CompletionCircle(completed: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(24.dp).clip(CircleShape)
            .then(if (completed) Modifier.background(Accent) else Modifier.border(1.5.dp, Accent, CircleShape))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { if (completed) CheckIcon(Color.White) }
}

@Composable
private fun OtherHabitsHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (expanded) ChevronUpIcon(TextMuted) else ChevronDownIcon(TextMuted)
            Spacer(Modifier.width(6.dp))
            Text("Other Habits", style = TextStyle(color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp))
            Spacer(Modifier.width(8.dp))
            Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(CardBackground).padding(horizontal = 7.dp, vertical = 2.dp)) {
                Text(count.toString(), style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp))
            }
        }
    }
}

@Composable
private fun CountPill(count: Int) {
    Box(modifier = Modifier.clip(RoundedCornerShape(999.dp)).background(CardMuted).padding(horizontal = 7.dp, vertical = 2.dp)) {
        Text(count.toString(), style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 16.sp))
    }
}

@Composable
private fun TagChip(label: String, background: Color = TagBackground) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(background).padding(horizontal = 6.dp, vertical = 2.dp)) {
        Text(label, style = TextStyle(color = Color(0xFFD1D5DC), fontSize = 10.sp, lineHeight = 15.sp))
    }
}

@Composable
private fun RecurringBadge(rule: RecurrenceRule?) {
    Box(modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(RecurringFill).padding(horizontal = 8.dp, vertical = 2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CalendarMiniIcon(RecurringText)
            Spacer(Modifier.width(4.dp))
            Text(recurrenceLabel(rule), style = TextStyle(color = RecurringText, fontSize = 10.sp, lineHeight = 15.sp))
        }
    }
}

@Composable
private fun CheckIcon(color: Color) {
    Icon(Icons.Filled.Check, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
}

@Composable
private fun PlusIcon(color: Color) {
    Icon(Icons.Filled.Add, contentDescription = null, tint = color, modifier = Modifier.size(24.dp))
}

@Composable
private fun CalendarMiniIcon(color: Color) {
    Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = color, modifier = Modifier.size(10.dp))
}

@Composable
private fun ChevronUpIcon(color: Color) {
    Icon(Icons.Filled.KeyboardArrowUp, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
}

@Composable
private fun ChevronDownIcon(color: Color) {
    Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
}

// Formatting helpers

private fun formatClock(totalMinutes: Int): String =
    String.format(Locale.ENGLISH, "%d:%02d", totalMinutes / 60, totalMinutes % 60)

private fun recurrenceLabel(rule: RecurrenceRule?): String {
    if (rule == null) return "Every Day"
    return when (rule.type) {
        RecurrenceType.DAILY    -> "Every Day"
        RecurrenceType.WEEKDAYS -> "Weekdays"
        RecurrenceType.CUSTOM   -> {
            val ordered = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            val selected = ordered.filter { it in rule.repeatDays }
            when {
                selected.isEmpty()                                        -> "Custom"
                selected.size == 7                                        -> "Every Day"
                selected == ordered.take(5)                               -> "Weekdays"
                selected == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> "Weekend"
                else -> selected.joinToString(" ") { dayShort(it) }
            }
        }
    }
}

private fun dayShort(day: DayOfWeek): String = when (day) {
    DayOfWeek.MONDAY    -> "Mon"; DayOfWeek.TUESDAY   -> "Tue"; DayOfWeek.WEDNESDAY -> "Wed"
    DayOfWeek.THURSDAY  -> "Thu"; DayOfWeek.FRIDAY    -> "Fri"
    DayOfWeek.SATURDAY  -> "Sat"; DayOfWeek.SUNDAY    -> "Sun"
}
