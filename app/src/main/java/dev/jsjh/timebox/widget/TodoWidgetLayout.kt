package dev.jsjh.timebox.widget

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import dev.jsjh.timebox.domain.model.DailyTask
import java.nio.ByteBuffer
import java.security.MessageDigest

internal val TodoWidgetSizes = setOf(200, 250, 360).flatMap { width ->
    listOf(180, 220).map { height -> DpSize(width.dp, height.dp) }
}.toSet()

internal data class TodoWidgetLayout(
    val compact: Boolean,
    val padding: Dp,
    val spacing: Dp,
    val cardVerticalPadding: Dp
)

internal fun todoWidgetLayout(size: DpSize, fontScale: Float): TodoWidgetLayout {
    val compact = size.width < 250.dp || size.height < 220.dp
    val largeText = fontScale > 1.3f
    val verticalPadding = if (largeText) 6.dp else if (compact) 8.dp else 11.dp
    return TodoWidgetLayout(
        compact = compact,
        padding = if (largeText) 4.dp else 14.dp,
        spacing = if (largeText) 2.dp else if (compact) 8.dp else 10.dp,
        cardVerticalPadding = verticalPadding
    )
}

internal fun DailyTask.widgetItemKey(): String = "$date:$id"

internal fun todoWidgetItemIds(tasks: List<DailyTask>): Map<String, Long> {
    val digest = MessageDigest.getInstance("SHA-256")
    val usedIds = mutableSetOf<Long>()
    // Resolve the rare hash collision in key order, never in the displayed task order.
    return tasks.map { it.widgetItemKey() }.distinct().sorted().associateWith { key ->
        var id = ByteBuffer.wrap(digest.digest(key.toByteArray(Charsets.UTF_8))).long and Long.MAX_VALUE
        while (!usedIds.add(id)) id = (id + 1) and Long.MAX_VALUE
        id
    }
}
