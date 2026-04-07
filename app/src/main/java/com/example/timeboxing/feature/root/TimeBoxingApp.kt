package com.example.timeboxing.feature.root

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.data.local.database.TaskDatabase
import com.example.timeboxing.data.repository.RoomTaskRepository
import com.example.timeboxing.feature.editor.TaskEditorDialog
import com.example.timeboxing.feature.home.HomeScreen
import com.example.timeboxing.feature.settings.SettingsScreen
import com.example.timeboxing.feature.timetable.TimetableScreen
import com.example.timeboxing.feature.todo.TodoScreen
import java.time.LocalDate

private val NavBackground = Color(0xFF1E1E1E)
private val NavDivider    = Color(0xFF2A2A2A)
private val NavActive     = Color(0xFF8687E7)
private val NavInactive   = Color(0xFF99A1AF)

@Composable
fun TimeBoxingApp() {
    val context = LocalContext.current
    val repository = remember(context) {
        val database = TaskDatabase.get(context)
        RoomTaskRepository(
            templateDao  = database.taskTemplateDao(),
            dailyTaskDao = database.dailyTaskDao()
        )
    }
    val appState = rememberTimeBoxingAppState(repository)

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                AppBottomBar(currentTab = appState.currentTab, onTabSelected = appState::selectTab)
            }
        ) { innerPadding ->
            val contentModifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding)

            when (appState.currentTab) {
                AppTab.HOME -> HomeScreen(
                    modifier          = contentModifier,
                    tasks             = appState.todayTasks,
                    date              = LocalDate.now(),
                    currentTime       = appState.currentTime,
                    onOpenTimetable   = appState::openTimetable,
                    onMarkTaskComplete = { appState.toggleCompleted(it, LocalDate.now()) },
                    onOpenTask        = { appState.openTaskEditor(it, LocalDate.now()) },
                    onAddTask         = { appState.openNewTaskEditor(date = LocalDate.now()) },
                    onNotificationsClick = {}
                )

                AppTab.TODO -> TodoScreen(
                    modifier                = contentModifier,
                    tasks                   = appState.todayTodoTasks,
                    date                    = LocalDate.now(),
                    recurrenceByTemplateId  = appState.recurrenceByTemplateId,
                    // [Fix 11] AppState 캐시 사용 — 매 recompose마다 getTemplates() 호출 제거
                    otherHabits             = appState.otherHabits,
                    onQuickAddTask          = { appState.quickAddTask(it, LocalDate.now()) },
                    onOpenAddTaskEditor     = { appState.openNewTaskEditor(date = LocalDate.now(), initialTitle = it) },
                    onToggleBig3            = appState::toggleBig3,
                    onToggleComplete        = { appState.toggleCompleted(it, LocalDate.now()) },
                    onOpenTask              = { appState.openTaskEditor(it, LocalDate.now()) },
                    onReorderTask           = { id, toIndex -> appState.reorderTodayTodoTask(id, toIndex) }
                )

                AppTab.TIMETABLE -> TimetableScreen(
                    modifier         = contentModifier,
                    tasks            = appState.selectedDateTasks,
                    date             = appState.selectedDate,
                    currentTime      = appState.currentTime,
                    showCurrentTime  = appState.selectedDate == LocalDate.now(),
                    onPreviousDay    = { appState.moveSelectedDateBy(-1) },
                    onNextDay        = { appState.moveSelectedDateBy(1) },
                    onOpenTask       = { appState.openTaskEditor(it, appState.selectedDate) },
                    onMoveToUnscheduled = { appState.moveToUnscheduled(it, appState.selectedDate) },
                    onUpdateSchedule = { taskId, schedule -> appState.updateSchedule(taskId, appState.selectedDate, schedule) },
                    onAddTask        = { appState.openNewTaskEditor(appState.selectedDate) }
                )

                AppTab.SETTINGS -> SettingsScreen(modifier = contentModifier)
            }
        }

        appState.editorDraft?.let { draft ->
            TaskEditorDialog(
                draft     = draft,
                onDismiss = appState::dismissEditor,
                onDelete  = if (draft.taskId != null) appState::deleteEditingTask else null,
                onSave    = appState::saveEditor,
                onChange  = { updated -> appState.updateEditor { updated } }
            )
        }
    }
}

@Composable
private fun AppBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(NavBackground)) {
        Box(modifier = Modifier.fillMaxWidth().background(NavBackground).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(78.dp).background(NavBackground),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    BottomBarItem(tab = tab, selected = currentTab == tab, onClick = { onTabSelected(tab) })
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NavDivider).align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun BottomBarItem(tab: AppTab, selected: Boolean, onClick: () -> Unit) {
    val color = if (selected) NavActive else NavInactive
    Column(
        modifier              = Modifier.width(76.dp).clickable(onClick = onClick).padding(top = 12.dp, bottom = 10.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.spacedBy(8.dp)
    ) {
        TabIcon(tab = tab, color = color)
        Text(text = tab.label, style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TabIcon(tab: AppTab, color: Color) {
    Canvas(modifier = Modifier.size(22.dp)) {
        val stroke = 1.9.dp.toPx()
        when (tab) {
            AppTab.HOME -> {
                val path = Path().apply {
                    moveTo(size.width * 0.18f, size.height * 0.48f); lineTo(size.width * 0.5f,  size.height * 0.18f)
                    lineTo(size.width * 0.82f, size.height * 0.48f); lineTo(size.width * 0.82f, size.height * 0.82f)
                    lineTo(size.width * 0.18f, size.height * 0.82f); close()
                }
                drawPath(path = path, color = color, style = Stroke(width = stroke))
                drawLine(color, Offset(size.width * 0.42f, size.height * 0.82f), Offset(size.width * 0.42f, size.height * 0.58f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.58f, size.height * 0.82f), Offset(size.width * 0.58f, size.height * 0.58f), stroke, StrokeCap.Round)
            }
            AppTab.TODO -> {
                drawCheckRow(color, stroke, 0.28f)
                drawCheckRow(color, stroke, 0.68f)
            }
            AppTab.TIMETABLE -> {
                drawRoundRect(color = color, topLeft = Offset(size.width * 0.16f, size.height * 0.18f), size = Size(size.width * 0.58f, size.height * 0.54f), style = Stroke(width = stroke), cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()))
                drawLine(color, Offset(size.width * 0.16f, size.height * 0.36f), Offset(size.width * 0.74f, size.height * 0.36f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.3f,  size.height * 0.1f),  Offset(size.width * 0.3f,  size.height * 0.26f), stroke, StrokeCap.Round)
                drawLine(color, Offset(size.width * 0.6f,  size.height * 0.1f),  Offset(size.width * 0.6f,  size.height * 0.26f), stroke, StrokeCap.Round)
                val clockCenter = Offset(size.width * 0.73f, size.height * 0.7f)
                drawCircle(color = color, radius = size.minDimension * 0.16f, center = clockCenter, style = Stroke(width = stroke))
                drawLine(color, clockCenter, Offset(clockCenter.x, size.height * 0.61f), stroke, StrokeCap.Round)
                drawLine(color, clockCenter, Offset(size.width * 0.8f, size.height * 0.7f), stroke, StrokeCap.Round)
            }
            AppTab.SETTINGS -> {
                val c = center
                drawCircle(color, size.minDimension * 0.22f, c, style = Stroke(stroke))
                drawCircle(color, size.minDimension * 0.09f, c, style = Stroke(stroke))
                listOf(0f, 60f, 120f, 180f, 240f, 300f).forEach { angle ->
                    val rad   = Math.toRadians(angle.toDouble())
                    val start = Offset(c.x + kotlin.math.cos(rad).toFloat() * size.minDimension * 0.33f, c.y + kotlin.math.sin(rad).toFloat() * size.minDimension * 0.33f)
                    val end   = Offset(c.x + kotlin.math.cos(rad).toFloat() * size.minDimension * 0.43f, c.y + kotlin.math.sin(rad).toFloat() * size.minDimension * 0.43f)
                    drawLine(color, start, end, stroke, StrokeCap.Round)
                }
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCheckRow(color: Color, stroke: Float, yFactor: Float) {
    drawLine(color, Offset(size.width * 0.16f, size.height * (yFactor - 0.02f)), Offset(size.width * 0.24f, size.height * (yFactor + 0.07f)), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.24f, size.height * (yFactor + 0.07f)), Offset(size.width * 0.34f, size.height * (yFactor - 0.05f)), stroke, StrokeCap.Round)
    drawLine(color, Offset(size.width * 0.44f, size.height * yFactor),           Offset(size.width * 0.84f, size.height * yFactor),           stroke, StrokeCap.Round)
}
