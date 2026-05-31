package dev.jsjh.timebox.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
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
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dev.jsjh.timebox.R
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.data.local.database.TaskDatabase
import dev.jsjh.timebox.data.repository.RoomTaskRepository
import dev.jsjh.timebox.data.repository.SyncedTaskRepository
import dev.jsjh.timebox.domain.model.DailyTask
import dev.jsjh.timebox.domain.repository.TaskRepository
import dev.jsjh.timebox.feature.settings.AppSettingsStore
import dev.jsjh.timebox.feature.settings.effectiveToday
import dev.jsjh.timebox.notification.ReminderRefreshBus
import dev.jsjh.timebox.notification.ReminderScheduler
import dev.jsjh.timebox.notification.ReminderSettingsStore
import java.time.LocalDate
import java.util.Locale
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
}

class TodoWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val initialState = withContext(Dispatchers.IO) { loadTodoWidgetState(context) }
        provideContent {
            val state by produceState(initialValue = initialState) {
                TodoWidgetRefreshBus.events.collect {
                    value = withContext(Dispatchers.IO) { loadTodoWidgetState(context) }
                }
            }
            val size = LocalSize.current
            val compact = size.height < 180.dp
            TodoWidgetContent(
                state = state,
                compact = compact,
                context = context
            )
        }
    }
}

object TodoWidgetUpdater {
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        TodoWidgetRefreshBus.notifyChanged()
        CoroutineScope(Dispatchers.Default).launch {
            runCatching { TodoWidget().updateAll(appContext) }
        }
    }
}

private object TodoWidgetRefreshBus {
    private val _events = MutableSharedFlow<Unit>(extraBufferCapacity = 4)
    val events = _events.asSharedFlow()

    fun notifyChanged() {
        _events.tryEmit(Unit)
    }
}

private data class TodoWidgetState(
    val date: LocalDate,
    val tasks: List<DailyTask>,
    val incompleteCount: Int,
    val totalCount: Int
)

private fun loadTodoWidgetState(context: Context): TodoWidgetState {
    val appContext = context.applicationContext
    val today = effectiveToday(AppSettingsStore(appContext).read().dayStartHour)
    val repository = createWidgetLocalRepository(appContext)
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
        totalCount = tasks.size
    )
}

@Composable
private fun TodoWidgetContent(
    state: TodoWidgetState,
    compact: Boolean,
    context: Context
) {
    val mainActivity = ComponentName(context, dev.jsjh.timebox.MainActivity::class.java)
    val addAction = actionStartActivity(
        mainActivity,
        actionParametersOf(
            ActionParameters.Key<String>(WidgetLaunchRequest.EXTRA_OPEN_TAB) to WidgetLaunchRequest.TAB_TODO,
            ActionParameters.Key<Boolean>(WidgetLaunchRequest.EXTRA_OPEN_ADD_TASK) to true
        )
    )
    val addButtonSize = 42.dp

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WidgetBg)
            .cornerRadius(24.dp)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = context.getString(R.string.widget_today),
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                )
                Text(
                    text = context.getString(R.string.widget_left, state.incompleteCount, state.totalCount),
                    style = TextStyle(color = ColorProvider(WidgetMuted), fontSize = 12.sp)
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
                Text(
                    text = "+",
                    style = TextStyle(
                        color = ColorProvider(WidgetText),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center
                    )
                )
            }
        }

        Spacer(GlanceModifier.height(if (compact) 8.dp else 10.dp))

        if (state.tasks.isEmpty()) {
            EmptyWidgetState(context)
            return@Column
        }

        LazyColumn(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items(state.tasks, itemId = { task -> task.id.hashCode().toLong() }) { task ->
                Column {
                    TodoWidgetCard(
                        task = task,
                        compact = compact,
                        context = context
                    )
                    Spacer(GlanceModifier.height(if (compact) 8.dp else 10.dp))
                }
            }
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
private fun TodoWidgetCard(task: DailyTask, compact: Boolean, context: Context) {
    val timeText = task.schedule?.let { "${formatClock(it.startMinute)} - ${formatClock(it.endMinute)}" }
        ?: context.getString(R.string.widget_unscheduled)
    val cardHeight = if (compact) 58.dp else 78.dp
    val actionParameters = actionParametersOf(
        TaskIdKey to task.id,
        TaskDateKey to task.date.toString()
    )
    val decoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None

    if (task.isBig3) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(if (compact) 62.dp else 82.dp)
                .cornerRadius(18.dp)
                .background(WidgetBorder)
                .padding(2.dp)
        ) {
            TodoWidgetCardBody(
                task = task,
                timeText = timeText,
                cardHeight = cardHeight,
                compact = compact,
                decoration = decoration,
                actionParameters = actionParameters
            )
        }
        return
    }

    TodoWidgetCardBody(
        task = task,
        timeText = timeText,
        cardHeight = cardHeight,
        compact = compact,
        decoration = decoration,
        actionParameters = actionParameters
    )
}

@Composable
private fun TodoWidgetCardBody(
    task: DailyTask,
    timeText: String,
    cardHeight: androidx.compose.ui.unit.Dp,
    compact: Boolean,
    decoration: TextDecoration,
    actionParameters: ActionParameters
) {
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(cardHeight)
            .cornerRadius(16.dp)
            .background(if (task.isCompleted) WidgetCardCompleted else WidgetCard)
            .padding(horizontal = 12.dp, vertical = if (compact) 8.dp else 11.dp)
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
            Spacer(GlanceModifier.width(if (compact) 4.dp else 6.dp))
            WidgetCloseButton(
                compact = compact,
                action = actionRunCallback<DeleteWidgetTaskAction>(actionParameters)
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

@Composable
private fun WidgetCloseButton(compact: Boolean, action: Action) {
    Box(
        modifier = GlanceModifier
            .size(if (compact) WidgetCompactControlHitSize else WidgetControlHitSize)
            .clickable(action, rippleOverride = R.drawable.bg_widget_no_ripple),
        contentAlignment = Alignment.Center
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_close),
            contentDescription = null,
            modifier = GlanceModifier.size(if (compact) 18.dp else 20.dp)
        )
    }
}

private fun formatClock(totalMinutes: Int): String =
    String.format(Locale.ENGLISH, "%02d:%02d", totalMinutes / 60, totalMinutes % 60)

class ToggleWidgetTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val date = parameters[TaskDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val appContext = context.applicationContext
        val repository = createWidgetRepository(appContext)
        repository.toggleCompleted(date, taskId)
        refreshAfterWidgetMutation(appContext, glanceId, repository, date)
    }
}

class DeleteWidgetTaskAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val taskId = parameters[TaskIdKey] ?: return
        val date = parameters[TaskDateKey]?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return
        val appContext = context.applicationContext
        val repository = createWidgetRepository(appContext)
        repository.deleteTask(date, taskId)
        refreshAfterWidgetMutation(appContext, glanceId, repository, date)
    }
}

private suspend fun refreshAfterWidgetMutation(
    context: Context,
    glanceId: GlanceId,
    repository: TaskRepository,
    date: LocalDate
) {
    val appContext = context.applicationContext
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

private fun createWidgetLocalRepository(context: Context): RoomTaskRepository {
    val appContext = context.applicationContext
    val userId = ActiveUserStore.readUserId(appContext)
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
        local = local,
        templateDao = database.taskTemplateDao(),
        dailyTaskDao = database.dailyTaskDao(),
        userId = userId
    )
}
