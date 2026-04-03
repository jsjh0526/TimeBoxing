package com.example.timeboxing.feature.editor

import android.view.WindowManager
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.example.timeboxing.domain.model.DailyTask
import com.example.timeboxing.domain.model.DailyTaskSource
import com.example.timeboxing.domain.model.RecurrenceRule
import com.example.timeboxing.domain.model.RecurrenceType
import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

private val Overlay = Color(0xCC000000)
private val Panel = Color(0xFF1E1E1E)
private val CardSurface = Color(0xFF1E1E1E)
private val Field = Color(0xFF2A2A2A)
private val FieldDark = Color(0xFF121212)
private val Border = Color(0xFF2A2A2A)
private val BorderStrong = Color(0xFF333333)
private val Accent = Color(0xFF8687E7)
private val TextPrimary = Color.White
private val TextSecondary = Color(0xFF99A1AF)
private val TextMuted = Color(0xFF6A7282)
private val Danger = Color(0xFFFF5F57)
private val TagFill = Color(0xFF4A4970)
private val Success = Color(0xFF7CFF7A)

private val TIME_OPTIONS: List<String> = buildList {
    for (h in 0..23) {
        for (m in listOf(0, 15, 30, 45)) {
            add("%02d:%02d".format(h, m))
        }
    }
}

@Immutable
data class TaskEditorDraft(
    val taskId: String? = null,
    val templateId: String? = null,
    val date: LocalDate,
    val title: String = "",
    val note: String = "",
    val tags: List<String> = emptyList(),
    val tagInput: String = "",
    val isBig3: Boolean = false,
    val recurringEnabled: Boolean = false,
    val recurrenceType: RecurrenceType = RecurrenceType.DAILY,
    val repeatDays: Set<DayOfWeek> = emptySet(),
    val timeBlockEnabled: Boolean = false,
    val startText: String = "09:00",
    val endText: String = "09:30",
    val alertEnabled: Boolean = false
)

fun DailyTask.toEditorDraft(existingRule: RecurrenceRule? = null): TaskEditorDraft {
    val rule = when {
        existingRule != null -> existingRule
        source == DailyTaskSource.RECURRING -> RecurrenceRule(RecurrenceType.DAILY)
        else -> null
    }
    return TaskEditorDraft(
        taskId = id, templateId = templateId, date = date, title = title,
        note = note.orEmpty(), tags = tags, isBig3 = isBig3,
        recurringEnabled = rule != null,
        recurrenceType = rule?.type ?: RecurrenceType.DAILY,
        repeatDays = editorRepeatDays(rule, date.dayOfWeek),
        timeBlockEnabled = schedule != null,
        startText = schedule?.let { formatTime(it.startMinute) } ?: "09:00",
        endText = schedule?.let { formatTime(it.endMinute) } ?: "09:30",
        alertEnabled = schedule?.reminderEnabled == true
    )
}

fun newTaskDraft(date: LocalDate, initialTitle: String = "") = TaskEditorDraft(date = date, title = initialTitle)

private fun applyStartChange(newStart: String, draft: TaskEditorDraft): TaskEditorDraft {
    val startMin = parseTime(newStart)
    val endMin = parseTime(draft.endText)
    return if (endMin <= startMin) {
        val newEnd = (startMin + 30).coerceAtMost(23 * 60 + 45)
        val snappedEnd = TIME_OPTIONS.firstOrNull { parseTime(it) >= newEnd } ?: "23:45"
        draft.copy(startText = newStart, endText = snappedEnd)
    } else {
        draft.copy(startText = newStart)
    }
}

@Composable
fun TaskEditorDialog(
    draft: TaskEditorDraft,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onSave: () -> Unit,
    onChange: (TaskEditorDraft) -> Unit
) {
    val bodyScroll = rememberScrollState()
    val endOptions = remember(draft.startText) {
        val startMin = parseTime(draft.startText)
        TIME_OPTIONS.filter { parseTime(it) > startMin }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        // ?¤ë³´???¬ë¼?€??Dialog ?„ì¹˜ ê³ ì •
        val dialogWindow = (LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {
            dialogWindow?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Overlay)
                .imePadding()
        ) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .heightIn(max = 720.dp)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Panel)
                        .border(0.7.dp, Border, RoundedCornerShape(20.dp))
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (draft.taskId == null) "New Task" else "Edit Task",
                            style = TextStyle(color = TextPrimary, fontSize = 18.sp, lineHeight = 27.sp, fontWeight = FontWeight.SemiBold)
                        )
                        // [Fix] ?¸ì½”??ê¹¨ì§„ "Ã—" ë³µì›
                        Box(
                            modifier = Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onDismiss),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = "\u00D7", style = TextStyle(color = TextSecondary, fontSize = 28.sp, fontWeight = FontWeight.Light, lineHeight = 28.sp))
                        }
                    }

                    Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(BorderStrong))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .verticalScroll(bodyScroll)
                            .padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        EditorLabel("Title")
                        EditorInput(value = draft.title, onValueChange = { onChange(draft.copy(title = it)) }, placeholder = "Task title", autoFocus = draft.taskId == null)

                        EditorLabel("Memo") { MemoIcon(color = TextSecondary, modifier = Modifier.size(14.dp)) }
                        EditorInput(value = draft.note, onValueChange = { onChange(draft.copy(note = it)) }, placeholder = "Details...", minLines = 3)

                        EditorLabel("Tags") { TagLabelIcon(color = TextSecondary, modifier = Modifier.size(14.dp)) }
                        TagEditor(draft = draft, onChange = onChange)

                        SettingSection(
                            title = "Recurring Habit",
                            enabled = draft.recurringEnabled,
                            onToggle = { onChange(draft.copy(recurringEnabled = it)) },
                            icon = { RecurringIcon(color = Accent, modifier = Modifier.size(18.dp)) }
                        ) {
                            val isWeekdaysPreset = draft.recurrenceType == RecurrenceType.WEEKDAYS
                            val isWeekendPreset = draft.recurrenceType == RecurrenceType.CUSTOM && draft.repeatDays == weekendDays()
                            val isCustomPreset = draft.recurrenceType == RecurrenceType.CUSTOM && !isWeekendPreset
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                RecurrenceSegment("Daily", draft.recurrenceType == RecurrenceType.DAILY, { onChange(draft.copy(recurrenceType = RecurrenceType.DAILY, repeatDays = emptySet())) }, Modifier.weight(1f))
                                RecurrenceSegment("Weekdays", isWeekdaysPreset, { onChange(draft.copy(recurrenceType = RecurrenceType.WEEKDAYS, repeatDays = defaultWeeklyDays())) }, Modifier.weight(1f))
                                RecurrenceSegment("Weekend", isWeekendPreset, { onChange(draft.copy(recurrenceType = RecurrenceType.CUSTOM, repeatDays = weekendDays())) }, Modifier.weight(1f))
                                RecurrenceSegment("Custom", isCustomPreset, { onChange(draft.copy(recurrenceType = RecurrenceType.CUSTOM, repeatDays = if (draft.repeatDays.isEmpty() || isWeekendPreset) setOf(draft.date.dayOfWeek) else draft.repeatDays)) }, Modifier.weight(1f))
                            }
                            if (isCustomPreset) {
                                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("SELECT DAYS", style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium))
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                        listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY).forEach { day ->
                                            DayCircle(dayLabel(day), day in draft.repeatDays, {

                                                val next = draft.repeatDays.toMutableSet()
                                                if (!next.add(day) && next.size > 1) next.remove(day)
                                                if (next.isNotEmpty()) onChange(draft.copy(repeatDays = next))
                                            }, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        SettingSection(
                            title = "Time Block",
                            enabled = draft.timeBlockEnabled,
                            onToggle = { onChange(draft.copy(timeBlockEnabled = it)) },
                            icon = { ClockIcon(color = Success, modifier = Modifier.size(18.dp)) }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                TimeDropdown("START", draft.startText, TIME_OPTIONS, { onChange(applyStartChange(it, draft)) }, Modifier.weight(1f))
                                // [Fix] ?¸ì½”??ê¹¨ì§„ "?? ë³µì›
                                Text("\u2192", style = TextStyle(color = Accent, fontSize = 18.sp, lineHeight = 28.sp))
                                TimeDropdown("END", draft.endText, endOptions, { onChange(draft.copy(endText = it)) }, Modifier.weight(1f))
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(FieldDark).border(0.7.dp, Border, RoundedCornerShape(10.dp)).padding(horizontal = 12.dp, vertical = 12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    ClockIcon(color = Accent, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Duration:", style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 19.5.sp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(durationLabel(draft.startText, draft.endText), style = TextStyle(color = Accent, fontSize = 14.sp, lineHeight = 21.sp, fontWeight = FontWeight.SemiBold))
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Alert", style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 19.5.sp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    EditorSwitch(checked = draft.alertEnabled, onToggle = { onChange(draft.copy(alertEnabled = it)) })
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().background(Panel).padding(horizontal = 18.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TrashButton(enabled = onDelete != null, onClick = { onDelete?.invoke() })
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Cancel", modifier = Modifier.clickable(onClick = onDismiss), style = TextStyle(color = TextSecondary, fontSize = 16.sp, lineHeight = 24.sp))
                        Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Accent).clickable(onClick = onSave).padding(horizontal = 26.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
                            Text(if (draft.taskId == null) "Create" else "Save", style = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditorLabel(label: String, icon: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            icon()
            Spacer(modifier = Modifier.width(8.dp))
        }
        Text(label, style = TextStyle(color = TextSecondary, fontSize = 13.sp, lineHeight = 19.5.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun EditorInput(value: String, onValueChange: (String) -> Unit, placeholder: String, autoFocus: Boolean = false, minLines: Int = 1, modifier: Modifier = Modifier) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(autoFocus) { if (autoFocus) focusRequester.requestFocus() }
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).padding(horizontal = 16.dp, vertical = 14.dp)) {
        BasicTextField(
            value = value, onValueChange = onValueChange,
            textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.5.sp),
            minLines = minLines,
            modifier = Modifier.fillMaxWidth().then(if (autoFocus) Modifier.focusRequester(focusRequester) else Modifier),
            decorationBox = { inner ->
                if (value.isEmpty()) Text(placeholder, style = TextStyle(color = TextMuted, fontSize = 15.sp, lineHeight = 22.5.sp))
                inner()
            }
        )
    }
}

@Composable
private fun TagEditor(draft: TaskEditorDraft, onChange: (TaskEditorDraft) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Field).padding(horizontal = 8.dp, vertical = 8.dp)) {
        Row(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            draft.tags.forEachIndexed { index, tag ->
                Box(modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(TagFill).border(0.7.dp, Accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#$tag", style = TextStyle(color = Accent, fontSize = 12.sp, lineHeight = 18.sp))
                        Spacer(modifier = Modifier.width(6.dp))
                        // [Fix] ?¸ì½”??ê¹¨ì§„ "Ã—" ë³µì›
                        Text("\u00D7", modifier = Modifier.clickable { onChange(draft.copy(tags = draft.tags.filterIndexed { i, _ -> i != index })) }, style = TextStyle(color = TextSecondary, fontSize = 11.sp))
                    }
                }
            }
            BasicTextField(
                value = draft.tagInput,
                onValueChange = { input ->
                    onChange(draft.copy(tagInput = input))
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = {
                    val value = draft.tagInput.trim().removePrefix("#")
                    if (value.isNotEmpty()) onChange(draft.copy(tags = draft.tags + value, tagInput = ""))
                }),
                textStyle = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.5.sp),
                modifier = Modifier.width(120.dp),
                decorationBox = { inner ->
                    if (draft.tagInput.isEmpty()) Text("Add tag...", style = TextStyle(color = TextMuted, fontSize = 15.sp))
                    inner()
                }
            )
        }
    }
}

@Composable
private fun SettingSection(title: String, enabled: Boolean, onToggle: (Boolean) -> Unit, icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(CardSurface).border(0.7.dp, Border, RoundedCornerShape(18.dp)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.width(10.dp))
                Text(title, style = TextStyle(color = TextPrimary, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold))
            }
            EditorSwitch(checked = enabled, onToggle = onToggle)
        }
        if (enabled) {
            Box(modifier = Modifier.fillMaxWidth().height(0.7.dp).background(BorderStrong))
            content()
        }
    }
}

@Composable
private fun EditorSwitch(checked: Boolean, onToggle: (Boolean) -> Unit) {
    Box(modifier = Modifier.width(44.dp).height(24.dp).clip(CircleShape).background(if (checked) Accent else Color(0xFF444444)).clickable { onToggle(!checked) }.padding(horizontal = 3.dp, vertical = 3.dp)) {
        Box(modifier = Modifier.align(if (checked) Alignment.CenterEnd else Alignment.CenterStart).size(18.dp).clip(CircleShape).background(Color.White))
    }
}

@Composable
private fun RecurrenceSegment(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(40.dp).shadow(if (selected) 10.dp else 0.dp, RoundedCornerShape(10.dp), ambientColor = Accent.copy(0.3f), spotColor = Accent.copy(0.3f)).clip(RoundedCornerShape(10.dp)).background(if (selected) Accent else FieldDark).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, maxLines = 1, overflow = TextOverflow.Clip, style = TextStyle(color = TextPrimary, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center))
    }
}

@Composable
private fun DayCircle(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.height(38.dp).shadow(if (selected) 10.dp else 0.dp, CircleShape, ambientColor = Accent.copy(0.35f), spotColor = Accent.copy(0.35f)).clip(CircleShape).background(if (selected) Accent else Color(0xFF4A4A4A)).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Text(label, style = TextStyle(color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
    }
}

@Composable
private fun TimeDropdown(label: String, value: String, options: List<String>, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = TextStyle(color = TextMuted, fontSize = 12.sp, lineHeight = 18.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium))
        Box {
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(10.dp)).background(FieldDark).border(0.7.dp, Border, RoundedCornerShape(10.dp)).clickable { expanded = true }.padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                Text(value, style = TextStyle(color = TextPrimary, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Medium))
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.background(Color(0xFF2A2A2A)).heightIn(max = 240.dp)) {
                options.forEach { time ->
                    DropdownMenuItem(
                        text = { Text(time, style = TextStyle(color = if (time == value) Accent else TextPrimary, fontSize = 14.sp, fontWeight = if (time == value) FontWeight.SemiBold else FontWeight.Normal)) },
                        onClick = { onValueChange(time); expanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun TrashButton(enabled: Boolean, onClick: () -> Unit) {
    Box(modifier = Modifier.size(32.dp).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        TrashIcon(color = if (enabled) Danger else Danger.copy(alpha = 0.35f), modifier = Modifier.size(22.dp))
    }
}

// ?€?€ Canvas ?„ì´ì½??€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€

@Composable
private fun ClockIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        drawCircle(color = color, style = Stroke(width = stroke))
        drawLine(color, center, Offset(center.x, size.height * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, center, Offset(size.width * 0.7f, center.y), stroke, StrokeCap.Round)
    }
}

@Composable
private fun RecurringIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.7.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.72f, size.height * 0.18f)
            cubicTo(size.width * 0.95f, size.height * 0.18f, size.width * 0.95f, size.height * 0.82f, size.width * 0.72f, size.height * 0.82f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            cubicTo(size.width * 0.05f, size.height * 0.82f, size.width * 0.05f, size.height * 0.18f, size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.55f, size.height * 0.18f)
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(size.width * 0.44f, size.height * 0.08f), Offset(size.width * 0.55f, size.height * 0.18f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.55f, size.height * 0.18f), Offset(size.width * 0.44f, size.height * 0.28f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun MemoIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        drawLine(color, Offset(size.width * 0.08f, size.height * 0.22f), Offset(size.width * 0.92f, size.height * 0.22f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.08f, size.height * 0.5f), Offset(size.width * 0.92f, size.height * 0.5f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.08f, size.height * 0.78f), Offset(size.width * 0.6f, size.height * 0.78f), stroke, StrokeCap.Round)
    }
}

@Composable
private fun TagLabelIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.5.dp.toPx()
        val path = Path().apply {
            moveTo(size.width * 0.08f, size.height * 0.22f)
            lineTo(size.width * 0.65f, size.height * 0.22f)
            lineTo(size.width * 0.92f, size.height * 0.5f)
            lineTo(size.width * 0.65f, size.height * 0.78f)
            lineTo(size.width * 0.08f, size.height * 0.78f)
            close()
        }
        drawPath(path = path, color = color, style = Stroke(width = stroke, cap = StrokeCap.Round))
        drawCircle(color = color, radius = size.minDimension * 0.09f, center = Offset(size.width * 0.26f, size.height * 0.5f), style = Stroke(width = stroke))
    }
}

@Composable
private fun TrashIcon(color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val stroke = 1.8.dp.toPx()
        drawLine(color, Offset(size.width * 0.38f, size.height * 0.1f), Offset(size.width * 0.62f, size.height * 0.1f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.14f, size.height * 0.24f), Offset(size.width * 0.86f, size.height * 0.24f), stroke, StrokeCap.Round)
        drawRoundRect(color = color, topLeft = Offset(size.width * 0.2f, size.height * 0.24f), size = Size(size.width * 0.6f, size.height * 0.66f), cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()), style = Stroke(width = stroke))
        drawLine(color, Offset(size.width * 0.39f, size.height * 0.38f), Offset(size.width * 0.39f, size.height * 0.78f), stroke, StrokeCap.Round)
        drawLine(color, Offset(size.width * 0.61f, size.height * 0.38f), Offset(size.width * 0.61f, size.height * 0.78f), stroke, StrokeCap.Round)
    }
}

// ?€?€ ?¬í¼ ?¨ìˆ˜ ?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€?€

private fun formatTime(totalMinutes: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)

private fun durationLabel(start: String, end: String): String {
    val duration = parseTime(end) - parseTime(start)
    return if (duration > 0) "$duration min" else "--"
}

private fun dayLabel(day: DayOfWeek): String = when (day) {
    DayOfWeek.SUNDAY -> "S"; DayOfWeek.MONDAY -> "M"; DayOfWeek.TUESDAY -> "T"
    DayOfWeek.WEDNESDAY -> "W"; DayOfWeek.THURSDAY -> "T"; DayOfWeek.FRIDAY -> "F"
    DayOfWeek.SATURDAY -> "S"
}

fun parseTime(value: String): Int {
    val parts = value.split(":")
    if (parts.size != 2) return 0
    val hour = parts[0].toIntOrNull() ?: return 0
    val minute = parts[1].toIntOrNull() ?: return 0
    return (hour.coerceIn(0, 23) * 60) + minute.coerceIn(0, 59)
}



private fun editorRepeatDays(rule: RecurrenceRule?, fallbackDay: DayOfWeek): Set<DayOfWeek> {
    if (rule == null) return emptySet()
    return when {
        rule.repeatDays.isNotEmpty() -> rule.repeatDays
        rule.type == RecurrenceType.WEEKDAYS -> defaultWeeklyDays()
        rule.type == RecurrenceType.CUSTOM -> setOf(fallbackDay)
        else -> emptySet()
    }
}

private fun defaultWeeklyDays(): Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY,
    DayOfWeek.TUESDAY,
    DayOfWeek.WEDNESDAY,
    DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY
)

private fun weekendDays(): Set<DayOfWeek> = setOf(
    DayOfWeek.SATURDAY,
    DayOfWeek.SUNDAY
)
