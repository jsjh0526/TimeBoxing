package dev.jsjh.timebox.feature.root

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jsjh.timebox.auth.AuthRepository
import dev.jsjh.timebox.auth.AuthState
import dev.jsjh.timebox.auth.LoginScreen
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.remote.SupabaseSync
import dev.jsjh.timebox.data.remote.SyncManager
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.data.repository.SyncedTaskRepository
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.editor.TaskEditorDialog
import dev.jsjh.timebox.feature.home.HomeScreen
import dev.jsjh.timebox.feature.settings.AppSettings
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.feature.settings.SettingsScreen
import dev.jsjh.timebox.feature.settings.effectiveToday
import dev.jsjh.timebox.feature.timetable.TimetableScreen
import dev.jsjh.timebox.feature.todo.TodoScreen
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.notification.ReminderRefreshBus
import dev.jsjh.timebox.notification.ReminderSettings
import dev.jsjh.timebox.notification.ReminderSettingsStore
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val NavBackground = Color(0xFF1E1E1E)
private val NavDivider = Color(0xFF2A2A2A)
private val NavActive = Color(0xFF8687E7)
private val NavInactive = Color(0xFF99A1AF)

@Composable
fun TimeBoxingApp(
    onRequestNotificationPermission: () -> Unit = {},
    onRequestBatteryOptimizationExemption: () -> Unit = {},
    onLoginScreenVisible: (Boolean) -> Unit = {},
    initialShowSystemNavigationBar: Boolean = false,
    onSystemNavigationBarPreferenceChange: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { AuthRepository.restoreSession(context) }

    val authState by AuthRepository.authState.collectAsState()
    val loginScreenVisible = authState is AuthState.SignedOut || authState is AuthState.Error

    LaunchedEffect(loginScreenVisible) {
        onLoginScreenVisible(loginScreenVisible)
    }

    var showMigrationDialog by remember { mutableStateOf(false) }
    var migrationCheckedFor by remember { mutableStateOf("") }
    var migrationReloadKey by remember { mutableStateOf(0) }

    when (val state = authState) {
        AuthState.Loading -> Box(modifier = Modifier.fillMaxSize().background(Color(0xFF0D0D0D)))

        AuthState.SignedOut, is AuthState.Error -> LoginScreen(onLoginSuccess = {})

        AuthState.Guest -> MainApp(
            context = context,
            userId = "guest",
            isGuest = true,
            reloadKey = migrationReloadKey,
            onRequestNotificationPermission = onRequestNotificationPermission,
            onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
            initialShowSystemNavigationBar = initialShowSystemNavigationBar,
            onSystemNavigationBarPreferenceChange = onSystemNavigationBarPreferenceChange
        )

        is AuthState.LoggedIn -> {
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
                                runCatching {
                                    val db = TaskDatabase.get(context, state.userId)
                                    SupabaseSync.pull(
                                        userId = state.userId,
                                        templateDao = db.taskTemplateDao(),
                                        dailyTaskDao = db.dailyTaskDao()
                                    )
                                    TaskDatabase.migrateGuestData(context, state.userId)
                                    SupabaseSync.syncAll(
                                        userId = state.userId,
                                        templateDao = db.taskTemplateDao(),
                                        dailyTaskDao = db.dailyTaskDao()
                                    )
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
                                    SupabaseSync.pull(
                                        userId = state.userId,
                                        templateDao = db.taskTemplateDao(),
                                        dailyTaskDao = db.dailyTaskDao(),
                                        replaceLocal = true
                                    )
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
                onRequestNotificationPermission = onRequestNotificationPermission,
                onRequestBatteryOptimizationExemption = onRequestBatteryOptimizationExemption,
                initialShowSystemNavigationBar = initialShowSystemNavigationBar,
                onSystemNavigationBarPreferenceChange = onSystemNavigationBarPreferenceChange
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
    onRequestNotificationPermission: () -> Unit,
    onRequestBatteryOptimizationExemption: () -> Unit,
    initialShowSystemNavigationBar: Boolean,
    onSystemNavigationBarPreferenceChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    var restoreReady by remember(userId, isGuest, reloadKey) { mutableStateOf(isGuest) }

    LaunchedEffect(userId, isGuest, reloadKey) {
        if (!isGuest) {
            runCatching {
                withContext(Dispatchers.IO) {
                    val hadLocalDatabase = TaskDatabase.exists(context, userId)
                    val database = TaskDatabase.get(context, userId)
                    val templateDao = database.taskTemplateDao()
                    val dailyTaskDao = database.dailyTaskDao()
                    val hasLocalData = templateDao.count() > 0 || dailyTaskDao.count() > 0

                    if (!hasLocalData) {
                        val remoteStatus = SupabaseSync.pull(
                            userId = userId,
                            templateDao = templateDao,
                            dailyTaskDao = dailyTaskDao
                        )
                        val hasDataAfterPull = templateDao.count() > 0 || dailyTaskDao.count() > 0
                        if (!hadLocalDatabase && !hasDataAfterPull && remoteStatus.templateCount == 0 && remoteStatus.taskCount == 0) {
                            RoomTaskRepository(
                                templateDao = templateDao,
                                dailyTaskDao = dailyTaskDao,
                                seedInitialData = true
                            )
                            SupabaseSync.syncAll(
                                userId = userId,
                                templateDao = templateDao,
                                dailyTaskDao = dailyTaskDao
                            )
                        }
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

    LaunchedEffect(userId, reloadKey) {
        onRequestNotificationPermission()
        onRequestBatteryOptimizationExemption()
    }

    val repository: TaskRepository = remember(userId, isGuest, reloadKey) {
        val shouldSeedGuest = isGuest && !TaskDatabase.exists(context, userId)
        val database = TaskDatabase.get(context, userId)
        val room = RoomTaskRepository(
            templateDao = database.taskTemplateDao(),
            dailyTaskDao = database.dailyTaskDao(),
            seedInitialData = shouldSeedGuest
        )
        if (isGuest) {
            room
        } else {
            SyncedTaskRepository(
                local = room,
                templateDao = database.taskTemplateDao(),
                dailyTaskDao = database.dailyTaskDao(),
                userId = userId
            )
        }
    }

    val appSettingsStore = remember(context) { AppSettingsStore(context) }
    var appSettings by remember {
        mutableStateOf(appSettingsStore.read().copy(showSystemNavigationBar = initialShowSystemNavigationBar))
    }
    val nowForDayBoundary by produceState(initialValue = LocalDateTime.now(), appSettings.dayStartHour) {
        while (true) {
            value = LocalDateTime.now()
            delay(60_000)
        }
    }
    val appToday = effectiveToday(appSettings.dayStartHour, nowForDayBoundary)
    val actualToday = nowForDayBoundary.toLocalDate()
    val appState = rememberTimeBoxingAppState(repository, appToday)
    LaunchedEffect(appToday) {
        appState.updateTodayDate(appToday)
    }
    LaunchedEffect(appState.scheduleLimitMessage) {
        val message = appState.scheduleLimitMessage ?: return@LaunchedEffect
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        appState.clearScheduleLimitMessage()
    }
    LaunchedEffect(appState) {
        ReminderRefreshBus.events.collect {
            appState.refreshAll()
        }
    }

    fun updateAppSettings(next: AppSettings) {
        val sanitized = next.copy(dayStartHour = next.dayStartHour.coerceIn(0, 6))
        appSettingsStore.write(sanitized)
        appSettings = sanitized
        onSystemNavigationBarPreferenceChange(sanitized.showSystemNavigationBar)
    }

    val reminderSettingsStore = remember(context) { ReminderSettingsStore(context) }
    var reminderSettings by remember { mutableStateOf(reminderSettingsStore.read()) }

    fun updateReminderSettings(next: ReminderSettings) {
        val enablingNotifications = !reminderSettings.notificationsEnabled && next.notificationsEnabled
        reminderSettingsStore.write(next)
        reminderSettings = next
        ReminderScheduler.createChannels(context)
        if (!next.notificationsEnabled) ReminderScheduler.cancelAll(context)
        if (enablingNotifications) onRequestNotificationPermission()
    }

    LaunchedEffect(appState.today, appState.todayTasks, reminderSettings) {
        ReminderScheduler.syncTasks(context, appState.today, appState.todayTasks, reminderSettings)
    }
    LaunchedEffect(appState.selectedDate, appState.selectedDateTasks, reminderSettings) {
        if (appState.selectedDate != appState.today) {
            ReminderScheduler.syncTasks(context, appState.selectedDate, appState.selectedDateTasks, reminderSettings)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            bottomBar = {
                AppBottomBar(
                    currentTab = appState.currentTab,
                    onTabSelected = { tab ->
                        if (tab == AppTab.TIMETABLE) appState.openTimetable(actualToday) else appState.selectTab(tab)
                    }
                )
            }
        ) { innerPadding ->
            val contentModifier = Modifier.fillMaxSize().padding(innerPadding).consumeWindowInsets(innerPadding)
            when (appState.currentTab) {
                AppTab.HOME -> HomeScreen(
                    modifier = contentModifier,
                    tasks = appState.todayTasks,
                    date = appState.today,
                    currentTime = appState.currentTime,
                    onOpenTimetable = { appState.openTimetable(actualToday) },
                    onMarkTaskComplete = { appState.toggleCompleted(it, appState.today) },
                    onOpenTask = { appState.openTaskEditor(it, appState.today) },
                    onAddTask = { appState.openNewTaskEditor(date = appState.today) },
                    onNotificationsClick = {}
                )
                AppTab.TODO -> TodoScreen(
                    modifier = contentModifier,
                    tasks = appState.todayTodoTasks,
                    date = appState.today,
                    recurrenceByTemplateId = appState.recurrenceByTemplateId,
                    otherHabits = appState.otherHabits,
                    yesterdayIncompleteTasks = appState.yesterdayIncompleteTasks,
                    onQuickAddTask = { appState.quickAddTask(it, appState.today) },
                    onOpenAddTaskEditor = { appState.openNewTaskEditor(date = appState.today, initialTitle = it) },
                    onCarryOverYesterday = appState::carryOverYesterdayIncompleteTasks,
                    onDismissYesterdayTask = appState::dismissYesterdayTask,
                    onToggleBig3 = appState::toggleBig3,
                    onToggleComplete = { appState.toggleCompleted(it, appState.today) },
                    onOpenTask = { appState.openTaskEditor(it, appState.today) },
                    onReorderTask = { id, toIndex -> appState.reorderTodayTodoTask(id, toIndex) }
                )
                AppTab.TIMETABLE -> TimetableScreen(
                    modifier = contentModifier,
                    tasks = appState.selectedDateTasks,
                    date = appState.selectedDate,
                    currentTime = appState.currentTime,
                    showCurrentTime = appState.selectedDate == actualToday,
                    onPreviousDay = { appState.moveSelectedDateBy(-1) },
                    onNextDay = { appState.moveSelectedDateBy(1) },
                    onToday = { appState.selectDate(actualToday) },
                    onOpenTask = { appState.openTaskEditor(it, appState.selectedDate) },
                    onToggleComplete = { appState.toggleCompleted(it, appState.selectedDate) },
                    onMoveToUnscheduled = { appState.moveToUnscheduled(it, appState.selectedDate) },
                    onUpdateSchedule = { taskId, schedule -> appState.updateSchedule(taskId, appState.selectedDate, schedule) },
                    onAddTask = { appState.openNewTaskEditor(appState.selectedDate) }
                )
                AppTab.SETTINGS -> SettingsScreen(
                    modifier = contentModifier,
                    appSettings = appSettings,
                    onAppSettingsChange = ::updateAppSettings,
                    reminderSettings = reminderSettings,
                    onReminderSettingsChange = ::updateReminderSettings,
                    onSignIn = { scope.launch { AuthRepository.signInWithGoogle(context) } },
                    onSignOut = { scope.launch { AuthRepository.signOut(context) } },
                    onSyncNow = {
                        scope.launch {
                            val database = TaskDatabase.get(context, userId)
                            SyncManager.syncAll(
                                userId = userId,
                                templateDao = database.taskTemplateDao(),
                                dailyTaskDao = database.dailyTaskDao()
                            )
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
                draft = draft,
                onDismiss = appState::dismissEditor,
                onDelete = if (draft.taskId != null || draft.templateId != null) appState::deleteEditingTask else null,
                onSave = appState::saveEditor,
                onChange = { updated -> appState.updateEditor { updated } }
            )
        }
    }
}

@Composable
private fun MigrationDialog(onMigrate: () -> Unit, onStartFresh: () -> Unit) {
    val cardBg = Color(0xFF1E1E1E)
    val accent = Color(0xFF8687E7)
    val textPrimary = Color.White
    val textMuted = Color(0xFF99A1AF)
    AlertDialog(
        onDismissRequest = {},
        containerColor = cardBg,
        title = {
            Text(
                "이전 데이터 이동",
                style = TextStyle(color = textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            )
        },
        text = {
            Text(
                "게스트로 작성한 로컬 데이터를\nGoogle 계정으로 옮길까요?\n\n새로 시작하면 계정에 저장된\n클라우드 데이터를 불러옵니다.",
                style = TextStyle(color = textMuted, fontSize = 14.sp, lineHeight = 21.sp)
            )
        },
        confirmButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent)
                    .clickable(onClick = onMigrate)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    "옮기기",
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                )
            }
        },
        dismissButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(0.7.dp, Color(0xFF2A2A2A), RoundedCornerShape(10.dp))
                    .clickable(onClick = onStartFresh)
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text("새로 시작", style = TextStyle(color = textMuted, fontSize = 14.sp))
            }
        }
    )
}

@Composable
private fun AppBottomBar(currentTab: AppTab, onTabSelected: (AppTab) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().background(NavBackground)) {
        Box(modifier = Modifier.fillMaxWidth().background(NavBackground).navigationBarsPadding()) {
            Row(
                modifier = Modifier.fillMaxWidth().height(78.dp).background(NavBackground),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppTab.entries.forEach { tab ->
                    BottomBarItem(
                        tab = tab,
                        selected = currentTab == tab,
                        modifier = Modifier.weight(1f),
                        onClick = { onTabSelected(tab) }
                    )
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(NavDivider).align(Alignment.TopCenter))
        }
    }
}

@Composable
private fun BottomBarItem(tab: AppTab, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val color = if (selected) NavActive else NavInactive
    Column(
        modifier = modifier
            .fillMaxHeight()
            .clickable(onClick = onClick)
            .padding(top = 12.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TabIcon(tab = tab, color = color)
        Text(
            text = tab.label,
            style = TextStyle(color = color, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        )
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
