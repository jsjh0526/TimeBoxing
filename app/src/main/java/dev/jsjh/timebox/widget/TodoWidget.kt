package dev.jsjh.timebox.widget

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.layout.wrapContentHeight
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.jsjh.timebox.BuildConfig
import dev.jsjh.timebox.R
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.remote.SyncScheduler
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.data.repository.SyncedTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.feature.settings.effectiveToday
import dev.jsjh.timebox.notification.ReminderRefreshBus
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.notification.ReminderSettingsStore
import dev.jsjh.timebox.ui.format.formatClockRange
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


private val WidgetBg = Color(0xFF151515)
private val WidgetCard = Color(0xFF24242A)
private val WidgetCardCompleted = Color(0xFF1E1E22)
private val WidgetAccent = Color(0xFF8687E7)
private val WidgetText = Color(0xFFFFFFFF)
private val WidgetMuted = Color(0xFF9AA1AF)
private val WidgetBorder = Color(0xFFFFFFFF)
private val WidgetControlHitSize = 30.dp
private val WidgetCompactControlHitSize = 26.dp
private val TaskIdKey = ActionParameters.Key<String>("task_id")
private val TaskDateKey = ActionParameters.Key<String>("task_date")
private const val GuestUserId = "guest"

class TodoWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        TimeBoxAnalytics.initialize(context.applicationContext)
        TimeBoxAnalytics.widgetAdded()
    }

    override fun onDisabled(context: Context) {
        TimeBoxAnalytics.initialize(context.applicationContext)
        TimeBoxAnalytics.widgetRemoved()
        super.onDisabled(context)
    }
}

class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(TodoWidgetSizes)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = withContext(Dispatchers.IO) { loadTodoWidgetStateOrFallback(context) }
        provideContent {
            val state by produceState(initialValue = initialState) {
                TodoWidgetRefreshBus.events.collect {
                    value = withContext(Dispatchers.IO) {
                        runCatching { loadTodoWidgetState(context) }
                            .getOrElse { e -> value.copy(errorMessage = "${e::class.simpleName}: ${e.message}") }
                    }
                }
            }
            TodoWidgetContent(
                state = state,
                context = context
            )
        }
    }
}

object TodoWidgetUpdater {
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            updateNow(appContext)
        }
    }

    suspend fun updateNow(context: Context) {
        val appContext = context.applicationContext
        TodoWidgetRefreshBus.notifyChanged()
        runCatching { TodoWidget().updateAll(appContext) }
            .onFailure { e -> Log.e("TodoWidget", "updateAll failed", e) }
    }
}

private object TodoWidgetRefreshBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun notifyChanged() {
        _events.tryEmit(Unit)
    }
}

internal data class TodoWidgetState(
    val date: LocalDate,
    val tasks: List<DailyTask>,
    val incompleteCount: Int,
    val totalCount: Int,
    val isUnlocked: Boolean,
    val errorMessage: String? = null,
    val ownerUserId: String
)

private suspend fun loadTodoWidgetState(context: Context): TodoWidgetState {
    val appContext = context.applicationContext
    val today = effectiveToday(AppSettingsStore(appContext).read().dayStartHour)
    val ownerUserId = ActiveUserStore.readUserId(appContext)
    val repository = createWidgetLocalRepository(appContext, ownerUserId)
    val tasks = repository.getTasks(today)
        .sortedWith(
            compareBy<DailyTask> { it.isCompleted }
                .thenByDescending { it.isBig3 }
                .thenBy { it.schedule?.startMinute ?: Int.MAX_VALUE }
                .thenBy { it.title }
        )
    return TodoWidgetState(
        date = today,
        tasks = tasks,
        incompleteCount = tasks.count { !it.isCompleted },
        totalCount = tasks.size,
        isUnlocked = WidgetAccessStore.isUnlocked(appContext),
        ownerUserId = ownerUserId
    )
}

private suspend fun loadTodoWidgetStateOrFallback(context: Context): TodoWidgetState =
    runCatching { loadTodoWidgetState(context) }.getOrElse { e ->
        Log.e("TodoWidget", "loadTodoWidgetState failed", e)
        TodoWidgetState(
            date = LocalDate.now(),
            tasks = emptyList(),
            incompleteCount = 0,
            totalCount = 0,
            isUnlocked = false,
            errorMessage = "${e::class.simpleName}: ${e.message}",
            ownerUserId = ActiveUserStore.readUserId(context)
        )
    }

@Composable
internal fun TodoWidgetContent(
    state: TodoWidgetState,
    context: Context
) {
    if (state.errorMessage != null) {
        ErrorWidgetState(context, state.errorMessage)
        return
    }

    val fontScale = context.resources.configuration.fontScale
    val layout = todoWidgetLayout(LocalSize.current, fontScale)
    val compactHeader = layout.compact && fontScale > 1.3f
    val openAction = actionStartActivity(WidgetLaunchRequest.todoIntent(context))
    val addAction = actionStartActivity(WidgetLaunchRequest.addTaskIntent(context))
    val addButtonSize = 42.dp

    if (!state.isUnlocked) {
        LockedWidgetState(
            context = context,
            action = actionStartActivity(WidgetLaunchRequest.settingsIntent(context))
        )
        return
    }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(24.dp)
            .padding(layout.padding)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = GlanceModifier.defaultWeight().wrapContentHeight()
                    .clickable(openAction, rippleOverride = R.drawable.bg_widget_no_ripple),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = context.getString(R.string.widget_today),
                    modifier = GlanceModifier.fillMaxWidth(),
                    maxLines = 1,
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = if (compactHeader) 14.sp else 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = context.getString(R.string.widget_left, state.incompleteCount, state.totalCount),
                    maxLines = 1,
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = if (compactHeader) 10.sp else 12.sp)
                )
            }
            Spacer(GlanceModifier.width(10.dp))
            Box(
                modifier = GlanceModifier
                    .size(addButtonSize)
                    .cornerRadius(15.dp)
                    .background(WidgetAccent)
                    .clickable(addAction, rippleOverride = R.drawable.bg_widget_no_ripple),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    provider = ImageProvider(R.drawable.ic_widget_add),
                    contentDescription = context.getString(R.string.home_add_task),
                    modifier = GlanceModifier.size(24.dp)
                )
            }
        }

        Spacer(GlanceModifier.height(layout.spacing))

        if (state.tasks.isEmpty()) {
            EmptyWidgetState(context)
            return@Column
        }

        val itemIds = remember(state.tasks) { todoWidgetItemIds(state.tasks) }
        LazyColumn(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(state.tasks, itemId = { itemIds.getValue(it.widgetItemKey()) }) { task ->
                Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = layout.spacing)) {
                    TodoWidgetCard(
                        task = task,
                        layout = layout,
                        context = context,
                        openAction = actionStartActivity(
                            WidgetLaunchRequest.taskIntent(context, WidgetTaskTarget(state.ownerUserId, task.id, task.date))
                        )
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorWidgetState(context: Context, message: String) {
    val title = if (BuildConfig.DEBUG) {
        "DEBUG ERROR"
    } else {
        context.getString(R.string.widget_error_title)
    }
    val body = if (BuildConfig.DEBUG) {
        message
    } else {
        context.getString(R.string.widget_error_message)
    }
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(24.dp)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = title,
            style = TextStyle(color = ColorProvider(Color(0xFFFF6B6B)), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        )
        Spacer(GlanceModifier.height(6.dp))
        Text(
            text = body,
            maxLines = 8,
            style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 11.sp)
        )
    }
}

@Composable
private fun LockedWidgetState(context: Context, action: Action) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(24.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = context.getString(R.string.widget_locked_title),
            style = TextStyle(
                color = ColorProvider(WidgetText),
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(8.dp))
        Text(
            text = context.getString(R.string.widget_locked_message),
            maxLines = 3,
            style = TextStyle(
                color = ColorProvider(WidgetMuted),
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        )
        Spacer(GlanceModifier.height(14.dp))
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(42.dp)
                .cornerRadius(14.dp)
                .background(WidgetAccent)
                .clickable(action, rippleOverride = R.drawable.bg_widget_no_ripple),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = context.getString(R.string.widget_locked_action),
                style = TextStyle(
                    color = ColorProvider(WidgetText),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
        }
    }
}

@Composable
private fun EmptyWidgetState(context: Context) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(86.dp)
            .cornerRadius(16.dp)
            .background(WidgetCard)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = context.getString(R.string.widget_no_tasks),
            style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 13.sp)
        )
    }
}

@Composable
private fun TodoWidgetCard(task: DailyTask, layout: TodoWidgetLayout, context: Context, openAction: Action) {
    val timeText = task.schedule?.let { formatClockRange(context, it.startMinute, it.endMinute) }
        ?: context.getString(R.string.widget_unscheduled)
    val actionParameters = actionParametersOf(
        TaskIdKey to task.id,
        TaskDateKey to task.date.toString()
    )
    val decoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    if (task.isBig3) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .wrapContentHeight()
                .cornerRadius(18.dp)
                .background(WidgetBorder)
                .clickable(openAction, rippleOverride = R.drawable.bg_widget_no_ripple)
                .padding(2.dp)
        ) {
            TodoWidgetCardBody(
                task = task,
                timeText = timeText,
                layout = layout,
                decoration = decoration,
                actionParameters = actionParameters,
                openAction = openAction
            )
        }
        return
    }

    TodoWidgetCardBody(
        task = task,
        timeText = timeText,
        layout = layout,
        decoration = decoration,
        actionParameters = actionParameters,
        openAction = openAction
    )
}

@Composable
private fun TodoWidgetCardBody(
    task: DailyTask,
    timeText: String,
    layout: TodoWidgetLayout,
    decoration: TextDecoration,
    actionParameters: ActionParameters,
    openAction: Action
) {
    val compact = layout.compact
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .wrapContentHeight()
            .cornerRadius(16.dp)
            .background(if (task.isCompleted) WidgetCardCompleted else WidgetCard)
            .clickable(openAction, rippleOverride = R.drawable.bg_widget_no_ripple)
            .padding(horizontal = 12.dp, vertical = layout.cardVerticalPadding)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            WidgetCheckbox(
                checked = task.isCompleted,
                compact = compact,
                action = actionRunCallback<ToggleWidgetTaskAction>(actionParameters)
            )
            Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
            Text(
                text = task.title,
                modifier = GlanceModifier.defaultWeight(),
                maxLines = 1,
                style = TextStyle(
                    color = ColorProvider(if (task.isCompleted) WidgetMuted else WidgetText),
                    fontSize = if (compact) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    textDecoration = decoration
                )
            )
        }

        Spacer(GlanceModifier.height(if (compact) 4.dp else 9.dp))

        Text(
            text = timeText,
            modifier = GlanceModifier.padding(start = 30.dp),
            maxLines = 1,
            style = TextStyle(
                color = ColorProvider(WidgetMuted),
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = decoration
            )
        )
    }
}

@Composable
private fun WidgetCheckbox(checked: Boolean, compact: Boolean, action: Action) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) WidgetCompactControlHitSize else WidgetControlHitSize)
            .clickable(action, rippleOverride = R.drawable.bg_widget_no_ripple),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(
                if (checked) R.drawable.ic_widget_checkbox_checked else R.drawable.ic_widget_checkbox_empty
            ),
            contentDescription = null,
            modifier = GlanceModifier.size(22.dp)
        )
    }
}

class ToggleWidgetTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val date = parameters[TaskDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val appContext = context.applicationContext
        val repository = createWidgetRepository(appContext)
        val task = repository.getTask(date, taskId)
        repository.toggleCompleted(date, taskId)
        if (task != null) {
            TimeBoxAnalytics.initialize(appContext)
            TimeBoxAnalytics.taskCompleted(
                completed = !task.isCompleted,
                source = "widget",
                isBig3 = task.isBig3,
                isTutorial = task.id.startsWith("seed-") || task.templateId == "tpl-standup"
            )
        }
        refreshAfterWidgetMutation(appContext, glanceId, repository, date)
    }
}

class DeleteWidgetTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        // Old RemoteViews can still reference this callback until the launcher refreshes them.
        TodoWidgetUpdater.updateNow(context)
    }
}

private suspend fun refreshAfterWidgetMutation(
    context: Context,
    glanceId: GlanceId,
    repository: TaskRepository,
    date: LocalDate
) {
    val appContext = context.applicationContext
    ActiveUserStore.readUserId(appContext)
        .takeIf { it != GuestUserId }
        ?.let { SyncScheduler.enqueueUpload(appContext, it) }
    ReminderRefreshBus.notifyTaskChanged()
    TodoWidgetRefreshBus.notifyChanged()
    runCatching { TodoWidget().update(appContext, glanceId) }
    withContext(Dispatchers.IO) {
        ReminderScheduler.syncTasks(
            context = appContext,
            date = date,
            tasks = repository.getTasks(date),
            settings = ReminderSettingsStore(appContext).read(),
            dayStartHour = AppSettingsStore(appContext).read().dayStartHour
        )
        runCatching { TodoWidget().updateAll(appContext) }
    }
}

private fun createWidgetLocalRepository(context: Context, userId: String): RoomTaskRepository {
    val appContext = context.applicationContext
    val database = TaskDatabase.get(appContext, userId)
    return RoomTaskRepository(
        templateDao = database.taskTemplateDao(),
        dailyTaskDao = database.dailyTaskDao()
    )
}

private fun createWidgetRepository(context: Context): TaskRepository {
    val appContext = context.applicationContext
    val userId = ActiveUserStore.readUserId(appContext)
    val database = TaskDatabase.get(appContext, userId)
    val local = RoomTaskRepository(
        templateDao = database.taskTemplateDao(),
        dailyTaskDao = database.dailyTaskDao()
    )
    if (userId == GuestUserId) return local
    initSupabase(appContext)
    return SyncedTaskRepository(
        database = database,
        local = local,
        templateDao = database.taskTemplateDao(),
        dailyTaskDao = database.dailyTaskDao(),
        userId = userId
    )
}
