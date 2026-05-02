package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_tasks")
data class DailyTaskEntity(
    @PrimaryKey val id: String,
    val templateId: String?,
    val dateIso: String,
    val title: String,
    val note: String?,
    val tagsSerialized: String,
    val isBig3: Boolean,
    val isCompleted: Boolean,
    val startMinute: Int?,
    val endMinute: Int?,
    val reminderEnabled: Boolean,
    val source: String
)
