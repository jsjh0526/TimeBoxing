package dev.jsjh.timebox.feature.todo

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import dev.jsjh.timebox.R
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.model.DailyTaskSource
import dev.jsjh.timebox.domain.model.RecurrenceRule
import dev.jsjh.timebox.domain.model.RecurrenceType
import dev.jsjh.timebox.domain.model.occursOn
import dev.jsjh.timebox.feature.tutorial.TutorialTarget
import dev.jsjh.timebox.feature.tutorial.TutorialTargetRegistry
import dev.jsjh.timebox.feature.tutorial.TutorialTaskIds
import dev.jsjh.timebox.feature.tutorial.tutorialTarget
import dev.jsjh.timebox.ui.format.formatClockRange
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
    pastIncompleteTasks: List<DailyTask> = emptyList(),
    carryOverInProgress: Boolean = false,
    carryOverFailed: Boolean = false,
    recurrenceByTemplateId: Map<String, RecurrenceRule?> = emptyMap(),
    onQuickAddTask: (String) -> Unit,
    onOpenAddTaskEditor: (String) -> Unit,
    onCarryOverPastTasks: (List<String>) -> Unit,
    onDismissPastTask: (String) -> Unit = {},
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    // Move a task to its final index after drag ends.
    onReorderTask: (String, Int) -> Unit,
    tutorialTargetRegistry: TutorialTargetRegistry? = null,
    tutorialFocusTarget: TutorialTarget? = null
) {
    var otherHabitsExpanded by remember { mutableStateOf(false) }
    var pastTasksExpanded by remember { mutableStateOf(false) }
    var completedExpanded by remember(date) { mutableStateOf(false) }
    var carryOverConfirmation by remember(date) { mutableStateOf<List<String>?>(null) }
    // Disable LazyColumn scrolling while any section is being dragged.
    var globalDragging by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    var listViewportBounds by remember { mutableStateOf<Rect?>(null) }

    val big3 = tasks.filter { it.isBig3 }
    val brainDumpTasks = tasks.filter { !it.isBig3 && it.source != DailyTaskSource.RECURRING }
    val brainDump = brainDumpTasks.filterNot { it.isCompleted }
    val completed = brainDumpTasks.filter { it.isCompleted }
    LaunchedEffect(completed.isEmpty()) {
        if (completed.isEmpty()) completedExpanded = false
    }
    val recurring = tasks.filter { task ->
        task.source == DailyTaskSource.RECURRING && !task.isBig3 && run {
            val rule = task.templateId?.let { tid -> recurrenceByTemplateId[tid] }
            rule?.occursOn(date.dayOfWeek) ?: true
        }
    }

    val brainDumpItemIndex = 10 + if (pastIncompleteTasks.isNotEmpty()) 2 else 0
    LaunchedEffect(tutorialFocusTarget, brainDumpItemIndex, listViewportBounds) {
        when (tutorialFocusTarget) {
            TutorialTarget.BRAIN_DUMP_INPUT -> listState.animateScrollToItem(0)
            TutorialTarget.MARK_BIG3 -> {
                val viewport = listViewportBounds ?: return@LaunchedEffect
                val targetCenterWithinItemPx = with(density) { (CARD_MIN_H / 2).toPx() }
                val desiredItemTopPx = (
                    viewport.height * 0.46f - targetCenterWithinItemPx
                ).coerceAtLeast(0f)
                listState.animateScrollToItem(
                    index = brainDumpItemIndex,
                    scrollOffset = -desiredItemTopPx.roundToInt()
                )
            }
            else -> Unit
        }
    }

    LazyColumn(
        modifier = modifier
            .testTag("todo_list")
            .fillMaxSize()
            .background(ScreenBackground)
            .onGloballyPositioned { listViewportBounds = it.boundsInRoot() },
        state = listState,
        userScrollEnabled = !globalDragging,
        contentPadding = PaddingValues(start = SCREEN_PAD, end = SCREEN_PAD, top = 8.dp, bottom = 120.dp)
    ) {
        item {
            Text(
                stringResource(R.string.todo_title),
                style = TextStyle(color = TextPrimary, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            )
        }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            InputRow(
                onQuickAddTask = onQuickAddTask,
                onOpenAddTaskEditor = onOpenAddTaskEditor,
                modifier = Modifier.tutorialTarget(
                    tutorialTargetRegistry,
                    TutorialTarget.BRAIN_DUMP_INPUT
                )
            )
        }
        if (pastIncompleteTasks.isNotEmpty()) {
            item { Spacer(Modifier.height(HEADER_GAP)) }
            item {
                PastIncompleteSection(
                    tasks = pastIncompleteTasks,
                    expanded = pastTasksExpanded,
                    busy = carryOverInProgress,
                    failed = carryOverFailed,
                    onToggle = { pastTasksExpanded = !pastTasksExpanded },
                    onDismissTask = onDismissPastTask,
                    onCarryOver = {
                        carryOverConfirmation = pastIncompleteTasks.map { it.id }
                    }
                )
            }
        }

        // TODAY'S BIG 3
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { Big3Header() }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (big3.isEmpty()) EmptySectionHint(stringResource(R.string.todo_hint_big3_empty))
            else DraggableSection(
                tasks = big3,
                bordered = true,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask,
                tutorialTargetRegistry = tutorialTargetRegistry
            )
        }

        // BRAIN DUMP
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader(stringResource(R.string.todo_brain_dump), TextSecondary, brainDump.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (brainDump.isEmpty()) EmptySectionHint(stringResource(R.string.todo_hint_braindump_empty))
            else DraggableSection(
                tasks = brainDump,
                bordered = false,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask,
                tutorialTargetRegistry = tutorialTargetRegistry
            )
        }

        if (completed.isNotEmpty()) {
            item(key = "completed_header") {
                Spacer(Modifier.height(HEADER_GAP))
                CollapsibleSectionHeader(
                    title = stringResource(R.string.todo_completed),
                    count = completed.size,
                    expanded = completedExpanded,
                    onToggle = { if (!globalDragging) completedExpanded = !completedExpanded },
                    modifier = Modifier.testTag("completed_tasks_header")
                )
            }
            if (completedExpanded) {
                items(completed, key = { "completed-${it.id}" }) { task ->
                    Box(Modifier.padding(top = ITEM_GAP)) {
                        TaskCard(
                            task = task,
                            bordered = false,
                            isDragging = false,
                            recurrenceRule = null,
                            onToggleBig3 = if (globalDragging) ({}) else onToggleBig3,
                            onToggleComplete = if (globalDragging) ({}) else onToggleComplete,
                            onOpenTask = if (globalDragging) ({}) else onOpenTask,
                            onDragStart = {},
                            onDrag = {},
                            onDragEnd = {},
                            onDragCancel = {},
                            tutorialTargetRegistry = null,
                            dragEnabled = false
                        )
                    }
                }
            }
        }

        // RECURRING HABITS
        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader(stringResource(R.string.todo_today_habits), RecurringSection, recurring.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            if (recurring.isEmpty()) EmptySectionHint(stringResource(R.string.todo_hint_habits_empty))
            else DraggableSection(
                tasks = recurring,
                bordered = false,
                recurrenceByTemplateId = recurrenceByTemplateId,
                onToggleBig3 = onToggleBig3,
                onToggleComplete = onToggleComplete,
                onOpenTask = onOpenTask,
                onSetDragging = { globalDragging = it },
                onReorder = onReorderTask,
                tutorialTargetRegistry = tutorialTargetRegistry
            )
        }

        // OTHER HABITS
        if (otherHabits.isNotEmpty()) {
            item { Spacer(Modifier.height(HEADER_GAP)) }
            item {
                CollapsibleSectionHeader(
                    title = stringResource(R.string.todo_other_habits),
                    count = otherHabits.size, expanded = otherHabitsExpanded,
                    onToggle = { otherHabitsExpanded = !otherHabitsExpanded }
                )
            }
            item {
                AnimatedVisibility(
                    visible = otherHabitsExpanded,
                    enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
                    exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(150)) + fadeOut(animationSpec = tween(90))
                ) {
                    Column(
                        modifier = Modifier.padding(top = HEADER_GAP),
                        verticalArrangement = Arrangement.spacedBy(ITEM_GAP)
                    ) {
                        otherHabits.forEach { task ->
                            key(task.id) {
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
    carryOverConfirmation?.let { confirmedIds ->
        val title = stringResource(R.string.todo_carry_over_title)
        val message = stringResource(R.string.todo_carry_over_message, confirmedIds.size)
        val confirmLabel = stringResource(R.string.todo_move_all_today)
        val cancelLabel = stringResource(R.string.editor_cancel)
        AlertDialog(
            onDismissRequest = { carryOverConfirmation = null },
            containerColor = CardBackground,
            shape = RoundedCornerShape(20.dp),
            title = {
                Text(title, color = TextPrimary)
            },
            text = {
                Text(
                    message,
                    color = TextSecondary
                )
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("carry_over_confirm"),
                    enabled = !carryOverInProgress,
                    onClick = {
                        carryOverConfirmation = null
                        onCarryOverPastTasks(confirmedIds)
                    }
                ) {
                    Text(confirmLabel, color = Accent)
                }
            },
            dismissButton = {
                TextButton(
                    modifier = Modifier.testTag("carry_over_cancel"),
                    onClick = { carryOverConfirmation = null }
                ) {
                    Text(cancelLabel, color = TextSecondary)
                }
            }
        )
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
    onReorder: (taskId: String, toIndex: Int) -> Unit,
    tutorialTargetRegistry: TutorialTargetRegistry?
) {
    val density = LocalDensity.current
    val fallbackHeightPx = with(density) { CARD_MIN_H.toPx() }
    val itemGapPx = with(density) { ITEM_GAP.toPx() }
    val dragShadowPx = with(density) { 24.dp.toPx() }
    val measuredHeights = remember(tasks.map { it.id }) { mutableStateMapOf<String, Int>() }

    var draggingIndex by remember { mutableStateOf(-1) }
    var dragTasksSnapshot by remember { mutableStateOf<List<DailyTask>?>(null) }
    var dragTotalY by remember { mutableStateOf(0f) }
    val draggingFrom = if (dragTasksSnapshot == tasks) draggingIndex else -1

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

    // 理쒖떊 ?쒕옒洹?肄쒕갚 李몄“瑜??좎??⑸땲??
    val latestOnSetDragging by rememberUpdatedState(onSetDragging)
    val latestOnReorder by rememberUpdatedState(onReorder)
    val latestTasks by rememberUpdatedState(tasks)
    LaunchedEffect(tasks) {
        if (draggingIndex >= 0 && dragTasksSnapshot != tasks) {
            draggingIndex = -1
            dragTasksSnapshot = null
            dragTotalY = 0f
            latestOnSetDragging(false)
        }
    }
    DisposableEffect(Unit) {
        onDispose {
            if (draggingIndex >= 0) {
                draggingIndex = -1
                dragTasksSnapshot = null
                dragTotalY = 0f
                latestOnSetDragging(false)
            }
        }
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        Column {
            tasks.forEachIndexed { index, task ->
                val isDragging = draggingFrom == index

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
                    val interactionsEnabled = draggingFrom < 0
                    TaskCard(
                        task = task,
                        bordered = bordered,
                        isDragging = isDragging,
                        recurrenceRule = task.templateId?.let { recurrenceByTemplateId[it] },
                        onToggleBig3 = if (interactionsEnabled) onToggleBig3 else ({ }),
                        onToggleComplete = if (interactionsEnabled) onToggleComplete else ({ }),
                        onOpenTask = if (interactionsEnabled) onOpenTask else ({ }),
                        onDragStart = {
                            dragTasksSnapshot = tasks
                            draggingIndex = index
                            dragTotalY = 0f
                            latestOnSetDragging(true)
                        },
                        onDrag = { delta -> dragTotalY += delta },
                        onDragEnd = {
                            val from = draggingFrom
                            if (from in tasks.indices && dragTasksSnapshot == latestTasks) {
                                val to = targetIndex
                                if (to in tasks.indices && from != to) latestOnReorder(tasks[from].id, to)
                            }
                            draggingIndex = -1
                            dragTasksSnapshot = null
                            dragTotalY = 0f
                            latestOnSetDragging(false)
                        },
                        onDragCancel = {
                            draggingIndex = -1
                            dragTasksSnapshot = null
                            dragTotalY = 0f
                            latestOnSetDragging(false)
                        },
                        tutorialTargetRegistry = tutorialTargetRegistry
                    )
                }
            }
        }

        if (draggingFrom >= 0 && targetIndex >= 0 && targetIndex != draggingFrom) {
            val indicatorY = when {
                targetIndex < draggingFrom -> cardTops[targetIndex] - itemGapPx / 2f
                else -> cardTops[targetIndex] + cardHeights[targetIndex] + itemGapPx / 2f
            }.coerceIn(0f, totalHeightPx)
            InsertionIndicator(
                modifier = Modifier
                    .testTag("todo_insertion_indicator")
                    .fillMaxWidth()
                    .graphicsLayer { translationY = indicatorY }
                    .zIndex(20f)
            )
        }
    }
}

@Composable
private fun InsertionIndicator(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.height(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Accent))
        Box(modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(999.dp)).background(Accent.copy(alpha = 0.82f)))
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(Accent))
    }
}

@Composable
private fun InputRow(
    onQuickAddTask: (String) -> Unit,
    onOpenAddTaskEditor: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var input by remember { mutableStateOf("") }
    fun consume(action: (String) -> Unit) {
        val t = input.trim(); if (t.isNotEmpty()) { action(t); input = "" }
    }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
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
                    if (input.isEmpty()) Text(stringResource(R.string.todo_input_placeholder), style = TextStyle(color = TextMuted, fontSize = 16.sp))
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
private fun PastIncompleteSection(
    tasks: List<DailyTask>,
    expanded: Boolean,
    busy: Boolean,
    failed: Boolean,
    onToggle: () -> Unit,
    onDismissTask: (String) -> Unit,
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
                .heightIn(min = 48.dp)
                .testTag("past_tasks_header")
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(modifier = Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(Accent))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.todo_past_incomplete),
                    modifier = Modifier.weight(1f).padding(vertical = 8.dp),
                    style = TextStyle(color = Accent, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                )
                Spacer(Modifier.width(8.dp))
                CountPill(tasks.size)
            }
            if (expanded) ChevronUpIcon(TextSecondary) else ChevronDownIcon(TextSecondary)
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(expandFrom = Alignment.Top, animationSpec = tween(180)) + fadeIn(animationSpec = tween(120)),
            exit = shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(150)) + fadeOut(animationSpec = tween(90))
        ) {
            Column {
                Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(Divider))
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    LazyColumn(
                        modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).testTag("past_tasks_list"),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(tasks, key = { it.id }) { task ->
                            PastTaskPreview(task, enabled = !busy, onDismiss = { onDismissTask(task.id) })
                        }
                    }
                    if (failed) {
                        Text(
                            stringResource(R.string.todo_carry_over_failed),
                            color = Color(0xFFFF7575),
                            fontSize = 13.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 44.dp)
                            .testTag("past_tasks_move")
                            .clip(RoundedCornerShape(10.dp))
                            .background(Accent)
                            .clickable(enabled = !busy, onClick = onCarryOver)
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            stringResource(if (busy) R.string.todo_carry_over_progress else R.string.todo_move_all_today),
                            style = TextStyle(color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PastTaskPreview(task: DailyTask, enabled: Boolean, onDismiss: () -> Unit) {
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
            Text(
                task.date.toString(),
                style = TextStyle(color = TextSecondary, fontSize = 11.sp, lineHeight = 15.sp)
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
        Spacer(Modifier.width(8.dp))
        // X 踰꾪듉 ?????쒖뒪?щ쭔 由ъ뒪?몄뿉???쒓굅
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.07f))
                .clickable(enabled = enabled, onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

// Section headers

@Composable
private fun Big3Header() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Big3Label))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.todo_big3), style = TextStyle(color = Big3Label, fontSize = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.5.sp))
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
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    tutorialTargetRegistry: TutorialTargetRegistry?,
    dragEnabled: Boolean = true
) {
    val context = LocalContext.current
    val isRecurring = task.source == DailyTaskSource.RECURRING
    val cardColor by animateColorAsState(
        targetValue = when {
            isDragging -> CardDragging
            task.isCompleted -> Color(0xFF242424)
            else -> CardBackground
        },
        animationSpec = tween(durationMillis = 180),
        label = "todoCardBackground"
    )
    val titleColor by animateColorAsState(
        targetValue = if (task.isCompleted) TextMuted else TextPrimary,
        animationSpec = tween(durationMillis = 180),
        label = "todoTitleColor"
    )
    val scheduleColor by animateColorAsState(
        targetValue = if (task.isCompleted) TextMuted else TextSecondary,
        animationSpec = tween(durationMillis = 180),
        label = "todoScheduleColor"
    )

    Box(
        modifier = Modifier
            .testTag("todo_task_${task.id}")
            .fillMaxWidth()
            .heightIn(min = CARD_MIN_H)
            .then(
                if (bordered && !isDragging) Modifier.shadow(
                    6.dp, RoundedCornerShape(CARD_RADIUS),
                    ambientColor = Accent.copy(0.12f), spotColor = Accent.copy(0.12f)
                ) else Modifier
            )
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(cardColor)
            .then(if (bordered) Modifier.border(1.dp, Accent, RoundedCornerShape(CARD_RADIUS)) else Modifier)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = CARD_PAD_H, vertical = CARD_PAD_V)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (dragEnabled) {
                DragHandle(
                    onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd,
                    onDragCancel = onDragCancel,
                    modifier = Modifier.testTag("todo_drag_${task.id}")
                )
            } else {
                Spacer(Modifier.width(DRAG_HANDLE_W))
            }
            Spacer(Modifier.width(6.dp))
            CompletionCircle(
                completed = task.isCompleted, onClick = { onToggleComplete(task.id) },
                modifier = Modifier.testTag("todo_complete_${task.id}")
            )
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .then(
                        if (task.id == TutorialTaskIds.OPEN_EDITOR) {
                            Modifier.tutorialTarget(
                                tutorialTargetRegistry,
                                TutorialTarget.OPEN_EDITOR_TASK
                            )
                        } else {
                            Modifier
                        }
                    ),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = titleColor,
                        fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
                )
                if (task.tags.isNotEmpty()) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        task.tags.forEach { tag -> TagChip("#$tag") }
                    }
                }
                if (isRecurring) RecurringBadge(rule = recurrenceRule)
                task.schedule?.let { sch ->
                    Text(
                        formatClockRange(context, sch.startMinute, sch.endMinute),
                        style = TextStyle(color = scheduleColor, fontSize = 10.sp, lineHeight = 15.sp)
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Big3Toggle(
                selected = task.isBig3,
                onClick = { onToggleBig3(task.id) },
                modifier = Modifier.testTag("todo_big3_${task.id}").then(
                    if (task.id == TutorialTaskIds.MARK_BIG3) {
                        Modifier.tutorialTarget(tutorialTargetRegistry, TutorialTarget.MARK_BIG3)
                    } else Modifier
                )
            )
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
    val cardAlpha by animateFloatAsState(
        targetValue = if (task.isCompleted) 0.52f else 0.5f,
        animationSpec = tween(durationMillis = 180),
        label = "todoCompactCardAlpha"
    )
    val titleColor by animateColorAsState(
        targetValue = if (task.isCompleted) TextMuted else TextPrimary,
        animationSpec = tween(durationMillis = 180),
        label = "todoCompactTitleColor"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_COMPACT_MIN_H)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CardBackground.copy(alpha = cardAlpha))
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
                        color = titleColor,
                        fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 2, overflow = TextOverflow.Ellipsis
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
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDrag      by rememberUpdatedState(onDrag)
    val latestOnDragEnd   by rememberUpdatedState(onDragEnd)
    val latestOnDragCancel by rememberUpdatedState(onDragCancel)

    Box(
        modifier = modifier
            .size(width = DRAG_HANDLE_W, height = 44.dp)
            .pointerInput(Unit) {
                detectDragGesturesAfterLongPress(
                    onDragStart  = { latestOnDragStart() },
                    onDragEnd    = { latestOnDragEnd() },
                    onDragCancel = { latestOnDragCancel() },
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
private fun Big3Toggle(selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Icon(
        imageVector = if (selected) Icons.Filled.Star else Icons.Outlined.StarBorder,
        contentDescription = null,
        tint = if (selected) Priority else TextSecondary,
        modifier = modifier.size(20.dp).clickable(onClick = onClick)
    )
}

@Composable
private fun CompletionCircle(completed: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val fillColor by animateColorAsState(
        targetValue = if (completed) Accent else Color.Transparent,
        animationSpec = tween(durationMillis = 160),
        label = "todoCompletionFill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (completed) Accent.copy(alpha = 0f) else Accent,
        animationSpec = tween(durationMillis = 160),
        label = "todoCompletionBorder"
    )

    Box(
        modifier = modifier.size(24.dp).clip(CircleShape)
            .background(fillColor)
            .border(1.5.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (completed) CheckIcon(Color.White)
    }
}

@Composable
private fun CollapsibleSectionHeader(
    title: String, count: Int, expanded: Boolean, onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Divider))
        Row(
            modifier = modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(onClick = onToggle),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (expanded) ChevronUpIcon(TextMuted) else ChevronDownIcon(TextMuted)
            Spacer(Modifier.width(6.dp))
            Text(title, modifier = Modifier.weight(1f), style = TextStyle(color = TextMuted, fontSize = 14.sp, lineHeight = 20.sp))
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
    Canvas(modifier = Modifier.size(15.dp)) {
        val stroke = 2.6.dp.toPx()
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

@Composable
private fun recurrenceLabel(rule: RecurrenceRule?): String {
    if (rule == null) return stringResource(R.string.recurrence_recurring)
    return when (rule.type) {
        RecurrenceType.DAILY -> stringResource(R.string.recurrence_daily)
        RecurrenceType.WEEKDAYS -> recurrenceDaysLabel(
            rule.repeatDays,
            emptyLabel = stringResource(R.string.recurrence_weekdays),
            dailyLabel = stringResource(R.string.recurrence_daily),
            weekdaysLabel = stringResource(R.string.recurrence_weekdays),
            weekendLabel = stringResource(R.string.recurrence_weekend)
        )
        RecurrenceType.CUSTOM -> recurrenceDaysLabel(
            rule.repeatDays,
            emptyLabel = stringResource(R.string.recurrence_custom),
            dailyLabel = stringResource(R.string.recurrence_daily),
            weekdaysLabel = stringResource(R.string.recurrence_weekdays),
            weekendLabel = stringResource(R.string.recurrence_weekend)
        )
    }
}

private fun recurrenceDaysLabel(
    days: Set<DayOfWeek>,
    emptyLabel: String,
    dailyLabel: String,
    weekdaysLabel: String,
    weekendLabel: String
): String {
    val ordered = listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY
    )
    val selected = ordered.filter { it in days }
    return when {
        selected.isEmpty() -> emptyLabel
        selected.size == 7 -> dailyLabel
        selected == ordered.take(5) -> weekdaysLabel
        selected == listOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY) -> weekendLabel
        else -> selected.joinToString(" ") { dayShort(it) }
    }
}

private fun dayShort(day: DayOfWeek): String =
    day.getDisplayName(java.time.format.TextStyle.SHORT, Locale.getDefault())
