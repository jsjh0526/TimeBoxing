package dev.jsjh.timebox.feature.tutorial

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.jsjh.timebox.R
import dev.jsjh.timebox.analytics.TimeBoxAnalytics
import dev.jsjh.timebox.data.repository.TutorialSeedData
import dev.jsjh.timebox.feature.editor.TaskEditorDialog
import dev.jsjh.timebox.feature.editor.toEditorDraft
import dev.jsjh.timebox.feature.home.HomeScreen
import dev.jsjh.timebox.feature.root.AppBottomBar
import dev.jsjh.timebox.feature.root.AppTab
import dev.jsjh.timebox.feature.timetable.TimetableScreen
import dev.jsjh.timebox.feature.todo.TodoScreen
import java.time.LocalDate
import java.time.LocalTime
import kotlinx.coroutines.delay

private val TutorialBackground = Color(0xFF0D0D0D)
private val TutorialSurface = Color(0xFF202126)
private val TutorialSurfaceRaised = Color(0xFF292A31)
private val TutorialAccent = Color(0xFF8687E7)
private val TutorialText = Color.White
private val TutorialMuted = Color(0xFFA8AFBE)

private const val TUTORIAL_STEP_COUNT = 8

private enum class CompletionState {
    NONE,
    SAVING,
    SUCCESS,
    FAILED
}

@Composable
fun TutorialPreviewHost(
    seedData: TutorialSeedData,
    date: LocalDate,
    source: TutorialLaunchSource,
    isRestart: Boolean,
    onSkip: () -> Unit,
    onComplete: suspend (TutorialCompletionResult) -> Boolean,
    onExit: () -> Unit
) {
    val controller = remember(seedData, date) { TutorialPreviewController(seedData, date) }
    var snapshot by remember(controller) { mutableStateOf(controller.snapshot) }
    val registry = remember { TutorialTargetRegistry() }
    var exitConfirmationVisible by remember { mutableStateOf(false) }
    var completionState by remember { mutableStateOf(CompletionState.NONE) }
    var completionAttempt by remember { mutableIntStateOf(0) }
    val currentTime = LocalTime.of(
        TUTORIAL_DEMO_MINUTE / 60,
        TUTORIAL_DEMO_MINUTE % 60
    )
    val recurrenceByTemplateId = remember(seedData) {
        seedData.templates.associate { template -> template.id to template.recurrenceRule }
    }

    fun requestExit() {
        exitConfirmationVisible = true
    }

    fun recordStepCompleted(before: TutorialPreviewSnapshot) {
        TimeBoxAnalytics.tutorialStepCompleted(
            step = before.step.eventName,
            phase = before.analyticsPhase,
            stepIndex = before.step.displayIndex,
            source = source.analyticsValue
        )
    }

    fun activateCurrentTarget() {
        val before = snapshot
        val effect = controller.activateCurrentTarget()
        recordStepCompleted(before)
        snapshot = controller.snapshot
        if (effect == TutorialEffect.SHOW_COMPLETION) {
            completionState = CompletionState.SAVING
        }
    }

    BackHandler(enabled = !snapshot.completionVisible, onBack = ::requestExit)

    LaunchedEffect(source, isRestart) {
        TimeBoxAnalytics.tutorialStarted(source.analyticsValue, isRestart)
    }

    LaunchedEffect(snapshot.step, snapshot.schedulePhase) {
        TimeBoxAnalytics.tutorialStepViewed(
            step = snapshot.step.eventName,
            phase = snapshot.analyticsPhase,
            stepIndex = snapshot.step.displayIndex,
            source = source.analyticsValue
        )
    }

    LaunchedEffect(snapshot.completionVisible, completionAttempt) {
        if (!snapshot.completionVisible) return@LaunchedEffect
        completionState = CompletionState.SAVING
        val result = controller.completionResult()
        val success = result != null && runCatching { onComplete(result) }.getOrDefault(false)
        if (!success) {
            completionState = CompletionState.FAILED
            return@LaunchedEffect
        }
        TimeBoxAnalytics.tutorialCompleted(
            source = source.analyticsValue,
            materialized = source == TutorialLaunchSource.AUTO_NEW
        )
        completionState = CompletionState.SUCCESS
        delay(1_650)
        onExit()
    }

    val todayTasks = snapshot.tasks.filter { it.date == date }
    val yesterdayTasks = snapshot.tasks.filter { it.date == date.minusDays(1) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(TutorialBackground)
            .statusBarsPadding()
    ) {
        TutorialTopBar(
            step = snapshot.step,
            onSkip = if (snapshot.completionVisible) null else ::requestExit
        )
        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = TutorialBackground,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    AppBottomBar(
                        currentTab = snapshot.tab,
                        onTabSelected = {},
                        tutorialTargetRegistry = registry
                    )
                }
            ) { innerPadding ->
                val contentModifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .consumeWindowInsets(innerPadding)

                when (snapshot.tab) {
                    AppTab.TODO -> TodoScreen(
                        modifier = contentModifier,
                        tasks = todayTasks,
                        date = date,
                        recurrenceByTemplateId = recurrenceByTemplateId,
                        yesterdayIncompleteTasks = yesterdayTasks,
                        onQuickAddTask = {},
                        onOpenAddTaskEditor = {},
                        onCarryOverYesterday = {},
                        onDismissYesterdayTask = {},
                        onToggleBig3 = {},
                        onToggleComplete = {},
                        onOpenTask = {},
                        onReorderTask = { _, _ -> },
                        tutorialTargetRegistry = registry,
                        tutorialFocusTarget = snapshot.target
                    )

                    AppTab.TIMETABLE -> TimetableScreen(
                        modifier = contentModifier,
                        tasks = todayTasks.sortedBy { task ->
                            if (task.id == TutorialTaskIds.SCHEDULE) 0 else 1
                        },
                        date = date,
                        currentTime = currentTime,
                        showCurrentTime = true,
                        onPreviousDay = {},
                        onNextDay = {},
                        onToday = {},
                        today = date,
                        calendarStatsForDates = { emptyMap() },
                        onSelectDate = {},
                        onAddTaskForDate = {},
                        onOpenTask = {},
                        onToggleComplete = {},
                        onMoveToUnscheduled = {},
                        onUpdateSchedule = { _, _ -> },
                        onAddTask = {},
                        tutorialTargetRegistry = registry,
                        tutorialFocusTarget = snapshot.target
                    )

                    AppTab.HOME -> HomeScreen(
                        modifier = contentModifier,
                        tasks = todayTasks,
                        date = date,
                        currentTime = currentTime,
                        onOpenTimetable = {},
                        onMarkTaskComplete = {},
                        onOpenTask = {},
                        onAddTask = {},
                        tutorialTargetRegistry = registry
                    )

                    AppTab.SETTINGS -> Box(modifier = contentModifier.background(TutorialBackground))
                }
            }

            if (snapshot.editorTaskId == null && !snapshot.completionVisible) {
                TutorialSpotlightOverlay(
                    snapshot = snapshot,
                    registry = registry,
                    onTargetClick = ::activateCurrentTarget
                )
            }

            if (snapshot.completionVisible) {
                TutorialCompletionOverlay(
                    state = completionState,
                    onRetry = { completionAttempt++ }
                )
            }
        }
    }

    snapshot.editorTaskId?.let { taskId ->
        val task = snapshot.tasks.firstOrNull { it.id == taskId } ?: return@let
        val rule = task.templateId?.let(recurrenceByTemplateId::get)
        val draft = task.toEditorDraft(rule).copy(
            recurringEnabled = true,
            timeBlockEnabled = true,
            alertEnabled = true
        )
        TaskEditorDialog(
            draft = draft,
            today = date,
            onDismiss = ::requestExit,
            onDelete = {},
            onSave = {},
            onChange = {},
            tutorialTargetRegistry = registry,
            tutorialFocusTarget = snapshot.target,
            tutorialHeader = {
                TutorialTopBar(step = snapshot.step, onSkip = ::requestExit)
            },
            tutorialOverlay = {
                TutorialSpotlightOverlay(
                    snapshot = snapshot,
                    registry = registry,
                    onTargetClick = ::activateCurrentTarget
                )
            }
        )
    }

    if (exitConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { exitConfirmationVisible = false },
            containerColor = TutorialSurface,
            title = {
                Text(
                    text = stringResource(R.string.tutorial_exit_title),
                    color = TutorialText,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.tutorial_exit_message),
                    color = TutorialMuted
                )
            },
            confirmButton = {
                Text(
                    text = stringResource(R.string.tutorial_exit_confirm),
                    color = Color(0xFFFF9680),
                    modifier = Modifier
                        .clickable {
                            TimeBoxAnalytics.tutorialSkipped(
                                step = snapshot.step.eventName,
                                phase = snapshot.analyticsPhase,
                                stepIndex = snapshot.step.displayIndex,
                                source = source.analyticsValue
                            )
                            onSkip()
                        }
                        .padding(12.dp)
                )
            },
            dismissButton = {
                Text(
                    text = stringResource(R.string.tutorial_continue),
                    color = TutorialAccent,
                    modifier = Modifier
                        .clickable { exitConfirmationVisible = false }
                        .padding(12.dp)
                )
            }
        )
    }
}

@Composable
private fun TutorialTopBar(
    step: TutorialStep,
    onSkip: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .background(TutorialBackground)
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "${step.displayIndex} / $TUTORIAL_STEP_COUNT",
            color = TutorialText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold
        )
        if (onSkip != null) {
            Text(
                text = stringResource(R.string.tutorial_skip),
                color = TutorialMuted,
                fontSize = 13.sp,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSkip)
                    .padding(horizontal = 10.dp, vertical = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.size(1.dp))
        }
    }
}

@Composable
private fun TutorialSpotlightOverlay(
    snapshot: TutorialPreviewSnapshot,
    registry: TutorialTargetRegistry,
    showCoach: Boolean = true,
    onTargetClick: () -> Unit
) {
    val density = LocalDensity.current
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }
    val targetBoundsInRoot = registry[snapshot.target]
    val targetPaddingPx = with(density) {
        when (snapshot.target) {
            TutorialTarget.TIMETABLE_TAB,
            TutorialTarget.HOME_TAB -> 3.dp.toPx()
            TutorialTarget.MARK_BIG3 -> 8.dp.toPx()
            else -> 6.dp.toPx()
        }
    }
    val spotlightBounds = targetBoundsInRoot
        ?.translatedBy(-overlayOrigin.x, -overlayOrigin.y)
        ?.inflate(targetPaddingPx)
    val pulse = remember { Animatable(0f) }
    val isEditorStep = snapshot.step == TutorialStep.RECURRING_HABIT ||
        snapshot.step == TutorialStep.TIME_BLOCK

    LaunchedEffect(snapshot.step, snapshot.schedulePhase) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, animationSpec = tween(durationMillis = 700))
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlayOrigin = it.positionInRoot() }
            .pointerInput(snapshot.step, snapshot.schedulePhase, spotlightBounds) {
                detectTapGestures { offset ->
                    if (
                        snapshot.advanceOnBackgroundTap ||
                        spotlightBounds?.contains(offset) == true
                    ) {
                        onTargetClick()
                    }
                }
            }
    ) {
        val canvasHeightPx = with(density) { maxHeight.toPx() }
        val showCoachAtTop = isEditorStep ||
            spotlightBounds?.center?.y?.let { it > canvasHeightPx * 0.52f } == true

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            drawRect(Color.Black.copy(alpha = 0.76f))
            spotlightBounds?.let { rect ->
                drawRoundRect(
                    color = Color.Transparent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    blendMode = BlendMode.Clear
                )
                drawRoundRect(
                    color = TutorialAccent,
                    topLeft = rect.topLeft,
                    size = rect.size,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )

                val pulseExpansion = 8.dp.toPx() * pulse.value
                drawRoundRect(
                    color = TutorialAccent.copy(alpha = 0.5f * (1f - pulse.value)),
                    topLeft = Offset(rect.left - pulseExpansion, rect.top - pulseExpansion),
                    size = Size(rect.width + pulseExpansion * 2f, rect.height + pulseExpansion * 2f),
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        if (showCoach) {
            val copy = tutorialCopy(snapshot)
            TutorialCoachCard(
                title = copy.title,
                body = copy.body,
                targetReady = spotlightBounds != null,
                advanceOnBackgroundTap = snapshot.advanceOnBackgroundTap,
                modifier = Modifier
                    .align(if (showCoachAtTop) Alignment.TopCenter else Alignment.BottomCenter)
                    .padding(
                        start = 20.dp,
                        end = 20.dp,
                        top = 20.dp,
                        bottom = if (showCoachAtTop || isEditorStep) 20.dp else 104.dp
                    )
            )
        }
    }
}

@Composable
private fun TutorialCoachCard(
    title: String,
    body: String,
    targetReady: Boolean,
    advanceOnBackgroundTap: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TutorialSurface)
            .border(0.7.dp, TutorialAccent.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            color = TutorialText,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = body,
            color = TutorialMuted,
            fontSize = 14.sp,
            lineHeight = 21.sp
        )
        Text(
            text = when {
                advanceOnBackgroundTap -> stringResource(R.string.tutorial_tap_anywhere)
                targetReady -> stringResource(R.string.tutorial_tap_highlighted)
                else -> stringResource(R.string.tutorial_preparing)
            },
            color = TutorialAccent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun TutorialCompletionOverlay(
    state: CompletionState,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.84f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(TutorialSurfaceRaised)
                .border(0.7.dp, TutorialAccent.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                .padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (state) {
                CompletionState.SAVING, CompletionState.NONE -> CircularProgressIndicator(
                    color = TutorialAccent,
                    modifier = Modifier.size(42.dp),
                    strokeWidth = 3.dp
                )

                CompletionState.SUCCESS -> Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = TutorialAccent,
                    modifier = Modifier.size(48.dp)
                )

                CompletionState.FAILED -> Unit
            }

            Text(
                text = stringResource(
                    if (state == CompletionState.FAILED) {
                        R.string.tutorial_save_failed_title
                    } else {
                        R.string.tutorial_complete_title
                    }
                ),
                color = TutorialText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(
                    if (state == CompletionState.FAILED) {
                        R.string.tutorial_save_failed_body
                    } else {
                        R.string.tutorial_complete_body
                    }
                ),
                color = TutorialMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
            if (state == CompletionState.FAILED) {
                Text(
                    text = stringResource(R.string.tutorial_retry),
                    color = TutorialText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(TutorialAccent)
                        .clickable(onClick = onRetry)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }
    }
}

private data class TutorialCopy(val title: String, val body: String)

@Composable
private fun tutorialCopy(snapshot: TutorialPreviewSnapshot): TutorialCopy = when (snapshot.step) {
    TutorialStep.BRAIN_DUMP -> TutorialCopy(
        stringResource(R.string.tutorial_brain_dump_step_title),
        stringResource(R.string.tutorial_brain_dump_step_body)
    )
    TutorialStep.MARK_BIG3 -> TutorialCopy(
        stringResource(R.string.tutorial_big3_step_title),
        stringResource(R.string.tutorial_big3_step_body)
    )
    TutorialStep.OPEN_EDITOR -> TutorialCopy(
        stringResource(R.string.tutorial_open_task_step_title),
        stringResource(R.string.tutorial_open_task_step_body)
    )
    TutorialStep.RECURRING_HABIT -> TutorialCopy(
        stringResource(R.string.tutorial_recurring_step_title),
        stringResource(R.string.tutorial_recurring_step_body)
    )
    TutorialStep.TIME_BLOCK -> TutorialCopy(
        stringResource(R.string.tutorial_time_block_step_title),
        stringResource(R.string.tutorial_time_block_step_body)
    )
    TutorialStep.SCHEDULE_TASK -> when (snapshot.schedulePhase) {
        TutorialSchedulePhase.OPEN_TIMETABLE -> TutorialCopy(
            stringResource(R.string.tutorial_schedule_open_title),
            stringResource(R.string.tutorial_schedule_open_body)
        )
        TutorialSchedulePhase.CHOOSE_TASK -> TutorialCopy(
            stringResource(R.string.tutorial_schedule_pick_title),
            stringResource(R.string.tutorial_schedule_pick_body)
        )
        TutorialSchedulePhase.SHOW_PLACED -> TutorialCopy(
            stringResource(R.string.tutorial_schedule_placed_title),
            stringResource(R.string.tutorial_schedule_placed_body)
        )
    }
    TutorialStep.OPEN_HOME -> TutorialCopy(
        stringResource(R.string.tutorial_open_home_step_title),
        stringResource(R.string.tutorial_open_home_step_body)
    )
    TutorialStep.HOME_NOW -> TutorialCopy(
        stringResource(R.string.tutorial_now_step_title),
        stringResource(R.string.tutorial_now_step_body)
    )
}

private fun Rect.inflate(amount: Float): Rect = Rect(
    left = left - amount,
    top = top - amount,
    right = right + amount,
    bottom = bottom + amount
)

private fun Rect.translatedBy(dx: Float, dy: Float): Rect = Rect(
    left = left + dx,
    top = top + dy,
    right = right + dx,
    bottom = bottom + dy
)
