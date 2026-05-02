package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "task_templates")
data class TaskTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val tagsSerialized: String,
    val recurrenceType: String?,
    val repeatDaysSerialized: String,
    val defaultStartMinute: Int?,
    val defaultEndMinute: Int?,
    val reminderEnabled: Boolean
)
