package dev.jsjh.timebox.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

@Entity(tableName = "task_templates")
@Serializable
data class TaskTemplateEntity(
    @PrimaryKey val id: String,
    val title: String,
    val note: String?,
    val tagsSerialized: String,
    val recurrenceType: String?,
    val repeatDaysSerialized: String,
    val startDateIso: String?,
    val defaultStartMinute: Int?,
    val defaultEndMinute: Int?,
    val reminderEnabled: Boolean
)
