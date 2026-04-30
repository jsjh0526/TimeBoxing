package com.example.timeboxing.feature.root

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.timeboxing.auth.AuthRepository
import com.example.timeboxing.auth.AuthState
import com.example.timeboxing.auth.LoginScreen
import com.example.timeboxing.data.local.database.TaskDatabase
import com.example.timeboxing.data.remote.SupabaseSync
import com.example.timeboxing.data.remote.SyncManager
import com.example.timeboxing.data.repository.RoomTaskRepository
import com.example.timeboxing.data.repository.SyncedTaskRepository
import com.example.timeboxing.domain.repository.TaskRepository
import com.example.timeboxing.feature.editor.TaskEditorDialog
import com.example.timeboxing.feature.home.HomeScreen
import com.example.timeboxing.feature.settings.SettingsScreen
import com.example.timeboxing.feature.timetable.TimetableScreen
import com.example.timeboxing.feature.todo.TodoScreen
import com.example.timeboxing.notification.ReminderScheduler
import com.example.timeboxing.notification.ReminderSettings
import com.example.timeboxing.notification.ReminderSettingsStore
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NavBackground = Color(0xFF1E1E1E)
private val NavDivider    = Color(0xFF2A2A2A)
private val NavActive     = Color(0xFF8687E7)
private val NavInactive   = Color(0xFF99A1AF)

@Composable
fun TimeBoxingApp(
    onRequestBatteryOptimizationExemption: () -> Unit = {},
    onLoginScreenVisible: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()
    LaunchedEffect(Unit) { AuthRepository.restoreSession(context) }

    val authState by AuthRepository.authState.collectAsState()
    val loginScreenVisible = authState is AuthState.SignedOut || authState is AuthState.Error

    LaunchedEffect(loginScreenVisible) {
        onLoginScreenVisible(loginScreenVisible)
    }

    var showMigrationDialog by remember { mutableStateOf(false) }
    var migrationCheckedFor by remember { mutableStateOf("") }
    var migrationReloadKey  by remember { mutableStateOf(0) }

    when (val state = authState) {
        AuthState.Loading -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)))

        AuthState.SignedOut, is AuthState.Error -> LoginScreen(onLoginSuccess = {})

        AuthState.Guest -> MainApp(
            context = context,
            userId = "guest",
            isGuest = true,
            reloadKey = migrationReloadKey,
            onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption
        )

        is AuthState.LoggedIn -> {
            // Fix 2: LaunchedEffect는 조건문 밖에서 무조건 호출 — 내부에서 조건 체크
            LaunchedEffect(state.userId) {
                if (AuthRepository.hadGuestSessionBeforeLogin && migrationCheckedFor != state.userId) {
                    migrationCheckedFor = state.userId
                    val hasData = withContext(Dispatchers.IO) { TaskDatabase.hasGuestData(context) }
                    if (hasData) showMigrationDialog = true
                }
            }

            if (showMigrationDialog) {
                MigrationDialog(
                    onMigrate = {
                        showMigrationDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                TaskDatabase.migrateGuestData(context, state.userId)
                                runCatching {
                                    val db = TaskDatabase.get(context, state.userId)
                                    SupabaseSync.syncAll(userId = state.userId, templateDao = db.taskTemplateDao(), dailyTaskDao = db.dailyTaskDao())
                                }
                            }
                            migrationReloadKey++
                        }
                    },
                    onStartFresh = {
                        showMigrationDialog = false
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                runCatching {
                                    val db = TaskDatabase.get(context, state.userId)
                                    SupabaseSync.pull(userId = state.userId, templateDao = db.taskTemplateDao(), dailyTaskDao = db.dailyTaskDao())
                                }
                            }
                            migrationReloadKey++
                        }
                    }
                )
            }

            MainApp(
                context = context,
                userId = state.userId,
                isGuest = false,
                reloadKey = migrationReloadKey,
                onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption
            )
        }
    }
}

@Composable
private fun MainApp(
    context: android.content.Context,
    userId: String,
    isGuest: Boolean,
    reloadKey: Int,
    onRequestBatteryOptimizationExemption: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var restoreReady by remember(userId, isGuest, reloadKey) { mutableStateOf(isGuest) }

    LaunchedEffect(userId, isGuest, reloadKey) {
        if (!isGuest) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val database = TaskDatabase.get(context, userId)
                    val templateDao = database.taskTemplateDao()
                    val dailyTaskDao = database.dailyTaskDao()
                    val hasLocalData = templateDao.count() > 0 || dailyTaskDao.count() > 0

                    if (!hasLocalData) {
                        SupabaseSync.pull(userId = userId, templateDao = templateDao, dailyTaskDao = dailyTaskDao)
                    }
                }
            }
            restoreReady = true
        }
    }

    if (!restoreReady) {
        Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)))
        return
    }

    val repository: TaskRepository = remember(userId, isGuest, reloadKey) {
        val database = TaskDatabase.get(context, userId)
        val room = RoomTaskRepository(
            templateDao = database.taskTemplateDao(),
            dailyTaskDao = database.dailyTaskDao(),
            seedInitialData = isGuest
        )
        if (isGuest) room
        else SyncedTaskRepository(local = room, templateDao = database.taskTemplateDao(), dailyTaskDao = database.dailyTaskDao(), userId = userId)
    }

    val appState = rememberTimeBoxingAppState(repository)
    val reminderSettingsStore = remember(context) { ReminderSettingsStore(context) }
    var reminderSettings by remember { mutableStateOf(reminderSettingsStore.read()) }
    var showBatteryOptimizationPrompt by remember { mutableStateOf(false) }

    fun updateReminderSettings(next: ReminderSettings) {
        val enablingNotifications = !reminderSettings.notificationsEnabled && next.notificationsEnabled
        reminderSettingsStore.write(next); reminderSettings = next
        ReminderScheduler.createChannels(context)
        if (!next.notificationsEnabled) ReminderScheduler.cancelAll(context)
        if (enablingNotifications) showBatteryOptimizationPrompt = true
    }

    LaunchedEffect(appState.todayTasks, reminderSettings) {
        ReminderScheduler.syncTasks(context, LocalDate.now(), appState.todayTasks, reminderSettings)
    }
    LaunchedEffect(appState.selectedDate, appState.selectedDateTasks, reminderSettings) {
        if (appState.selectedDate != LocalDate.now())
            ReminderScheduler.syncTasks(context, appState.selectedDate, appState.selectedDateTasks, reminderSettings)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = { AppBottomBar(currentTab = appState.currentTab, onTabSelected = appState::selectTab) }
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
            when (appState.currentTab) {
                AppTab.HOME -> HomeScreen(
                    modifier = contentModifier, tasks = appState.todayTasks, date = LocalDate.now(),
                    currentTime = appState.currentTime, onOpenTimetable = appState::openTimetable,
                    onMarkTaskComplete = { appState.toggleCompleted(it, LocalDate.now()) },
                    onOpenTask = { appState.openTaskEditor(it, LocalDate.now()) },
                    onAddTask = { appState.openNewTaskEditor(date = LocalDate.now()) },
                    onNotificationsClick = {}
                )
                AppTab.TODO -> TodoScreen(
                    modifier = contentModifier, tasks = appState.todayTodoTasks, date = LocalDate.now(),
                    recurrenceByTemplateId = appState.recurrenceByTemplateId, otherHabits = appState.otherHabits,
                    yesterdayIncompleteTasks = appState.yesterdayIncompleteTasks,
                    onQuickAddTask = { appState.quickAddTask(it, LocalDate.now()) },
                    onOpenAddTaskEditor = { appState.openNewTaskEditor(date = LocalDate.now(), initialTitle = it) },
                    onCarryOverYesterday = appState::carryOverYesterdayIncompleteTasks,
                    onDismissYesterdayTask = appState::dismissYesterdayTask,
                    onToggleBig3 = appState::toggleBig3,
                    onToggleComplete = { appState.toggleCompleted(it, LocalDate.now()) },
                    onOpenTask = { appState.openTaskEditor(it, LocalDate.now()) },
                    onReorderTask = { id, toIndex -> appState.reorderTodayTodoTask(id, toIndex) }
                )
                AppTab.TIMETABLE -> TimetableScreen(
                    modifier = contentModifier, tasks = appState.selectedDateTasks, date = appState.selectedDate,
                    currentTime = appState.currentTime, showCurrentTime = appState.selectedDate == LocalDate.now(),
                    onPreviousDay = { appState.moveSelectedDateBy(-1) }, onNextDay = { appState.moveSelectedDateBy(1) },
                    onToday = appState::goToToday,
                    onOpenTask = { appState.openTaskEditor(it, appState.selectedDate) },
                    onToggleComplete = { appState.toggleCompleted(it, appState.selectedDate) },
                    onMoveToUnscheduled = { appState.moveToUnscheduled(it, appState.selectedDate) },
                    onUpdateSchedule = { taskId, schedule -> appState.updateSchedule(taskId, appState.selectedDate, schedule) },
                    onAddTask = { appState.openNewTaskEditor(appState.selectedDate) }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = contentModifier,
                    reminderSettings = reminderSettings,
                    onReminderSettingsChange = ::updateReminderSettings,
                    onSignIn  = { scope.launch { AuthRepository.signInWithGoogle(context) } },
                    onSignOut = { scope.launch { AuthRepository.signOut(context) } },
                    onSyncNow = {
                        scope.launch {
                            val database = TaskDatabase.get(context, userId)
                            SyncManager.syncAll(userId = userId, templateDao = database.taskTemplateDao(), dailyTaskDao = database.dailyTaskDao())
                            appState.refreshAll()
                        }
                    },
                    onRefreshStatus = { uid ->
                        scope.launch {
                            SyncManager.refreshRemoteStatus(userId = uid)
                        }
                    }
                )
            }
        }

        appState.editorDraft?.let { draft ->
            TaskEditorDialog(
                draft = draft, onDismiss = appState::dismissEditor,
                onDelete = if (draft.taskId != null) appState::deleteEditingTask else null,
                onSave = appState::saveEditor,
                onChange = { updated -> appState.updateEditor { updated } }
            )
        }

        if (showBatteryOptimizationPrompt) {
            BatteryOptimizationDialog(
                onConfirm = {
                    showBatteryOptimizationPrompt = false
                    onRequestBatteryOptimizationExemption()
                },
                onDismiss = { showBatteryOptimizationPrompt = false }
            )
        }
    }
}

@Composable
private fun BatteryOptimizationDialog(onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val CardBg      = Color(0xFF1E1E1E)
    val Accent      = Color(0xFF8687E7)
    val TextPrimary = Color.White
    val TextMuted   = Color(0xFF99A1AF)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor   = CardBg,
        title = {
            Text(
                "Keep reminders reliable",
                style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Text(
                "Time block reminders can be delayed if Android puts the app to sleep. Allowing a battery optimization exception helps alerts fire at the scheduled time.",
                style = TextStyle(color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Accent)
                    .clickable(onClick = onConfirm)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Open Settings", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.7.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                    .clickable(onClick = onDismiss)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("Not now", style = TextStyle(color = TextMuted, fontSize = 14.sp))
            }
        }
    )
}

// ── 마이그레이션 다이얼로그 ──────────────────────────────────────────────────────

@Composable
private fun MigrationDialog(onMigrate: () -> Unit, onStartFresh: () -> Unit) {
    val CardBg      = Color(0xFF1E1E1E)
    val Accent      = Color(0xFF8687E7)
    val TextPrimary = Color.White
    val TextMuted   = Color(0xFF99A1AF)
    AlertDialog(
        onDismissRequest = {},
        containerColor   = CardBg,
        title = { Text("이전 데이터 이전", style = TextStyle(color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)) },
        text  = { Text("게스트로 작성한 할일 데이터를\n구글 계정으로 옮길까요?\n\n취소하면 계정에 저장된\n클라우드 데이터를 불러옵니다.", style = TextStyle(color = TextMuted, fontSize = 14.sp, lineHeight = 21.sp)) },
        confirmButton = {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(Accent).clickable(onClick = onMigrate).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("옮기기", style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        },
        dismissButton = {
            Box(modifier = Modifier.clip(RoundedCornerShape(10.dp)).border(0.7.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp)).clickable(onClick = onStartFresh).padding(horizontal = 20.dp, vertical = 10.dp)) {
                Text("새로 시작", style = TextStyle(color = TextMuted, fontSize = 14.sp))
            }
        }
    )
}

// ── 하단 바 ──────────────────────────────────────────────────────────────────

@Composable
private fun AppBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(NavBackground)) {
        Box(modifier = Modifier.fillMaxWidth().background(NavBackground).navigationBarsPadding()) {
            Row(modifier = Modifier.fillMaxWidth().height(78.dp).background(NavBackground), verticalAlignment = Alignment.CenterVertically) {
                AppTab.entries.forEach { tab ->
                    BottomBarItem(tab = tab, selected = currentTab == tab, modifier = Modifier.weight(1f), onClick = { onTabSelected(tab) })
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NavDivider).align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun BottomBarItem(tab: AppTab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) NavActive else NavInactive
    Column(modifier = modifier.fillMaxHeight().clickable(onClick = onClick).padding(top = 12.dp, bottom = 10.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        TabIcon(tab = tab, color = color)
        Text(text = tab.label, style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium))
    }
}

@Composable
private fun TabIcon(tab: AppTab, color: Color) {
    if (tab == AppTab.TIMETABLE) {
        TimetableTabIcon(color = color, modifier = Modifier.size(24.dp))
        return
    }

    val icon = when (tab) {
        AppTab.HOME -> Icons.Filled.Home
        AppTab.TODO -> Icons.AutoMirrored.Filled.FormatListBulleted
        AppTab.SETTINGS -> Icons.Filled.Settings
        AppTab.TIMETABLE -> error("Handled above")
    }
    Icon(
        imageVector = icon,
        contentDescription = tab.label,
        tint = color,
        modifier = Modifier.size(24.dp)
    )
}

@Composable
private fun TimetableTabIcon(color: Color, modifier: Modifier = Modifier) {
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
