package com.example.timeboxing.feature.todo

import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.DailyTaskSource
import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

// ?�?� ?�상 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�
private val ScreenBackground = Color(0xFF121212)
private val CardBackground   = Color(0xFF2A2A2A)
private val CardMuted        = Color(0xFF363636)
private val Accent           = Color(0xFF8687E7)
private val TextPrimary      = Color.White
private val TextSecondary    = Color(0xFF99A1AF)
private val TextMuted        = Color(0xFF6A7282)
private val Divider          = Color(0xFF333333)
private val TagBackground    = Color(0xFF444444)
private val RecurringTagBg   = Color(0xFF2A2A2A)
private val Priority         = Color(0xFFFFC300)
private val Big3Label        = Color(0xFFFF9680)
private val RecurringSection = Color(0xFFE5DDA8)
private val RecurringFill    = Color(0x1A8687E7)
private val RecurringText    = Color(0xFF8687E7)

// ?�?� ?�자???�큰 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�
private val SCREEN_PAD   = 24.dp
private val SECTION_GAP  = 32.dp
private val HEADER_GAP   = 16.dp
private val ITEM_GAP     = 12.dp
private val CARD_RADIUS  = 10.dp
private val CARD_PAD_H   = 12.dp
private val CARD_PAD_V   = 14.dp
private val CARD_MIN_H         = 72.dp
private val CARD_COMPACT_MIN_H = 56.dp
private val DRAG_HANDLE_W     = 16.dp

// Drag handle width is intentionally slimmer than the checkbox.`nprivate val DRAG_HANDLE_W = 16.dp

@Composable
fun TodoScreen(
    modifier: Modifier = Modifier,
    tasks: List<DailyTask>,
    date: LocalDate,
    otherHabits: List<DailyTask> = emptyList(),
    recurrenceByTemplateId: Map<String, RecurrenceRule?> = emptyMap(),
    onQuickAddTask: (String) -> Unit,
    onOpenAddTaskEditor: (String) -> Unit,
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onReorderTask: (String, String) -> Unit
) {
    var otherHabitsExpanded by remember { mutableStateOf(false) }
    var dragInProgress by remember { mutableStateOf(false) }

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
        contentPadding = PaddingValues(start = SCREEN_PAD, end = SCREEN_PAD, top = 8.dp, bottom = 120.dp),
        userScrollEnabled = !dragInProgress
    ) {
        item {
            Text(
                "Tasks & Habits",
                style = TextStyle(color = TextPrimary, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp)
            )
        }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item { InputRow(onQuickAddTask = onQuickAddTask, onOpenAddTaskEditor = onOpenAddTaskEditor) }

        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { Big3Header() }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                if (big3.isEmpty()) EmptySectionHint("Star tasks below to prioritize")
                else big3.forEachIndexed { index, task ->
                    TaskCard(
                        task = task, bordered = true,
                        recurrenceRule = task.templateId?.let { recurrenceByTemplateId[it] },
                        onToggleBig3 = onToggleBig3, onToggleComplete = onToggleComplete, onOpenTask = onOpenTask,
                        onDragStart = { dragInProgress = true }, onDragEnd = { dragInProgress = false },
                        onMoveUp   = { if (index > 0)             onReorderTask(task.id, big3[index - 1].id) },
                        onMoveDown = { if (index < big3.lastIndex) onReorderTask(big3[index + 1].id, task.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader("BRAIN DUMP", TextSecondary, brainDump.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                if (brainDump.isEmpty()) EmptySectionHint("No tasks yet. Add one above!")
                else brainDump.forEachIndexed { index, task ->
                    TaskCard(
                        task = task, bordered = false, recurrenceRule = null,
                        onToggleBig3 = onToggleBig3, onToggleComplete = onToggleComplete, onOpenTask = onOpenTask,
                        onDragStart = { dragInProgress = true }, onDragEnd = { dragInProgress = false },
                        onMoveUp   = { if (index > 0)                 onReorderTask(task.id, brainDump[index - 1].id) },
                        onMoveDown = { if (index < brainDump.lastIndex) onReorderTask(brainDump[index + 1].id, task.id) }
                    )
                }
            }
        }

        item { Spacer(Modifier.height(SECTION_GAP)) }
        item { SectionHeader("RECURRING HABITS", RecurringSection, recurring.size) }
        item { Spacer(Modifier.height(HEADER_GAP)) }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(ITEM_GAP)) {
                if (recurring.isEmpty()) EmptySectionHint("No recurring habits for today.")
                else recurring.forEachIndexed { index, task ->
                    TaskCard(
                        task = task, bordered = false,
                        recurrenceRule = task.templateId?.let { recurrenceByTemplateId[it] },
                        onToggleBig3 = onToggleBig3, onToggleComplete = onToggleComplete, onOpenTask = onOpenTask,
                        onDragStart = { dragInProgress = true }, onDragEnd = { dragInProgress = false },
                        onMoveUp   = { if (index > 0)                  onReorderTask(task.id, recurring[index - 1].id) },
                        onMoveDown = { if (index < recurring.lastIndex) onReorderTask(recurring[index + 1].id, task.id) }
                    )
                }
            }
        }

        if (otherHabits.isNotEmpty()) {
            item { Spacer(Modifier.height(HEADER_GAP)) }
            item {
                OtherHabitsHeader(
                    count = otherHabits.size,
                    expanded = otherHabitsExpanded,
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

// ?�?� ?�력�??�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

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

// ?�?� ?�션 ?�더 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

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

// ?�?� ?�스??카드 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

@Composable
private fun TaskCard(
    task: DailyTask,
    bordered: Boolean,
    recurrenceRule: RecurrenceRule?,
    onToggleBig3: (String) -> Unit,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit,
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val isRecurring = task.source == DailyTaskSource.RECURRING
    val cardAlpha = if (task.isCompleted) 0.52f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_MIN_H)
            .then(
                if (bordered) Modifier.shadow(6.dp, RoundedCornerShape(CARD_RADIUS), ambientColor = if (task.isCompleted) Accent.copy(0.04f) else Accent.copy(0.12f), spotColor = if (task.isCompleted) Accent.copy(0.04f) else Accent.copy(0.12f))
                else Modifier
            )
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CardBackground)
            .then(if (bordered) Modifier.border(1.dp, if (task.isCompleted) Accent.copy(0.55f) else Accent, RoundedCornerShape(CARD_RADIUS)) else Modifier)
            .alpha(cardAlpha)
            .clickable { onOpenTask(task.id) }
            .padding(horizontal = CARD_PAD_H, vertical = CARD_PAD_V)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            DragHandle(onDragStart = onDragStart, onDragEnd = onDragEnd, onMoveUp = onMoveUp, onMoveDown = onMoveDown)
            Spacer(Modifier.width(6.dp))
            CompletionCircle(completed = task.isCompleted, onClick = { onToggleComplete(task.id) })
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = task.title,
                    style = TextStyle(
                        color = if (task.isCompleted) TextMuted else TextPrimary,
                        fontSize = 16.sp,
                        lineHeight = 24.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (task.tags.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        task.tags.forEach { tag ->
                            TagChip("#$tag", TagBackground)
                        }
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

// ?�?� 컴팩??카드 (Other Habits) ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

@Composable
private fun CompactCard(
    task: DailyTask,
    recurrenceRule: RecurrenceRule?,
    onToggleComplete: (String) -> Unit,
    onOpenTask: (String) -> Unit
) {
    val cardAlpha = if (task.isCompleted) 0.52f else 1f
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = CARD_COMPACT_MIN_H)
            .clip(RoundedCornerShape(CARD_RADIUS))
            .background(CardBackground)
            .alpha(cardAlpha)
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
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textDecoration = TextDecoration.None
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    recurrenceLabel(recurrenceRule),
                    style = TextStyle(color = TextMuted, fontSize = 10.sp, lineHeight = 15.sp)
                )
            }
        }
    }
}

// ?�?� ?�래�??�들 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

@Composable
private fun DragHandle(
    onDragStart: () -> Unit,
    onDragEnd: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val density = LocalDensity.current
    val threshold = with(density) { 20.dp.toPx() }

    val latestOnDragStart by rememberUpdatedState(onDragStart)
    val latestOnDragEnd   by rememberUpdatedState(onDragEnd)
    val latestOnMoveUp    by rememberUpdatedState(onMoveUp)
    val latestOnMoveDown  by rememberUpdatedState(onMoveDown)

    Box(
        modifier = Modifier
            .size(width = DRAG_HANDLE_W, height = 44.dp)  // ?�비 ?�큰 ?�용 (16dp)
            .pointerInput(Unit) {
                var dragY = 0f
                detectDragGesturesAfterLongPress(
                    onDragStart  = { latestOnDragStart(); dragY = 0f },
                    onDragEnd    = { latestOnDragEnd();   dragY = 0f },
                    onDragCancel = { latestOnDragEnd();   dragY = 0f },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragY += dragAmount.y
                        while (dragY >  threshold) { latestOnMoveDown(); dragY -= threshold }
                        while (dragY < -threshold) { latestOnMoveUp();   dragY += threshold }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(width = 10.dp, height = 16.dp)) {
            val dot = 1.dp.toPx()
            listOf(size.height * 0.22f, size.height * 0.5f, size.height * 0.78f).forEach { y ->
                listOf(size.width * 0.25f, size.width * 0.75f).forEach { x ->
                    drawCircle(TextMuted, radius = dot, center = Offset(x, y))
                }
            }
        }
    }
}

// ?�?� 기�? 컴포?�트 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

@Composable
private fun Big3Toggle(selected: Boolean, onClick: () -> Unit) {
    Canvas(modifier = Modifier.size(20.dp).clickable(onClick = onClick)) {
        val path = Path().apply {
            moveTo(size.width * 0.5f,  size.height * 0.08f)
            lineTo(size.width * 0.62f, size.height * 0.36f); lineTo(size.width * 0.92f, size.height * 0.38f)
            lineTo(size.width * 0.69f, size.height * 0.58f); lineTo(size.width * 0.77f, size.height * 0.9f)
            lineTo(size.width * 0.5f,  size.height * 0.72f); lineTo(size.width * 0.23f, size.height * 0.9f)
            lineTo(size.width * 0.31f, size.height * 0.58f); lineTo(size.width * 0.08f, size.height * 0.38f)
            lineTo(size.width * 0.38f, size.height * 0.36f); close()
        }
        if (selected) drawPath(path, Priority)
        else drawPath(path, TextSecondary, style = Stroke(1.5.dp.toPx(), join = StrokeJoin.Round))
    }
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
        Row(modifier = Modifier.clickable(onClick = onToggle), verticalAlignment = Alignment.CenterVertically) {
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

// ?�?� Canvas ?�이�??�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

@Composable
private fun CheckIcon(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val s = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * 0.2f,  size.height * 0.55f), Offset(size.width * 0.45f, size.height * 0.78f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.45f, size.height * 0.78f), Offset(size.width * 0.82f, size.height * 0.3f),  s, StrokeCap.Round)
    }
}

@Composable
private fun PlusIcon(color: Color) {
    Canvas(modifier = Modifier.size(24.dp)) {
        val s = 2.dp.toPx()
        drawLine(color, Offset(center.x, size.height * 0.25f), Offset(center.x, size.height * 0.75f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.25f, center.y), Offset(size.width * 0.75f, center.y), s, StrokeCap.Round)
    }
}

@Composable
private fun CalendarMiniIcon(color: Color) {
    Canvas(modifier = Modifier.size(10.dp)) {
        val s = 1.dp.toPx()
        drawRoundRect(color, cornerRadius = CornerRadius(2.dp.toPx()), style = Stroke(s))
        drawLine(color, Offset(size.width * 0.15f, size.height * 0.36f), Offset(size.width * 0.85f, size.height * 0.36f), s, StrokeCap.Round)
    }
}

@Composable
private fun ChevronUpIcon(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val s = 1.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.25f, size.height * 0.65f), Offset(size.width * 0.5f,  size.height * 0.35f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f,  size.height * 0.35f), Offset(size.width * 0.75f, size.height * 0.65f), s, StrokeCap.Round)
    }
}

@Composable
private fun ChevronDownIcon(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val s = 1.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.25f, size.height * 0.35f), Offset(size.width * 0.5f,  size.height * 0.65f), s, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.5f,  size.height * 0.65f), Offset(size.width * 0.75f, size.height * 0.35f), s, StrokeCap.Round)
    }
}

// ?�?� ?�퍼 ?�수 ?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�?�

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
                selected.isEmpty()          -> "Custom"
                selected.size == 7          -> "Every Day"
                selected == ordered.take(5) -> "Weekdays"
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

private fun RecurrenceRule.occursOn(dayOfWeek: DayOfWeek): Boolean = when (type) {
    RecurrenceType.DAILY    -> true
    RecurrenceType.WEEKDAYS -> dayOfWeek !in setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    RecurrenceType.CUSTOM   -> dayOfWeek in repeatDays
}



