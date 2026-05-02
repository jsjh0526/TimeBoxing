package com.example.timeboxing.notification

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.timeboxing.data.local.database.TaskDatabase
import com.example.timeboxing.auth.ActiveUserStore
import com.example.timeboxing.data.repository.RoomTaskRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 기기 재부팅 시 AlarmManager 알람을 재등록.
 * Android는 재부팅 시 모든 알람을 지우기 때문에 필수.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action !in listOf(
                Intent.ACTION_BOOT_COMPLETED,
                "android.intent.action.QUICKBOOT_POWERON",
                "com.htc.intent.action.QUICKBOOT_POWERON"
            )
        ) return

        val settings = ReminderSettingsStore(context).read()
        if (!settings.notificationsEnabled) return

        ReminderScheduler.createChannels(context)

        // 현재 로그인된 userId 기준으로 오늘 + 내일 할일 알람 재등록
        val userId = ActiveUserStore.readUserId(context)
        val scope  = CoroutineScope(Dispatchers.IO + SupervisorJob())
        scope.launch {
            try {
                val database     = TaskDatabase.get(context, userId)
                val repository   = RoomTaskRepository(
                    templateDao  = database.taskTemplateDao(),
                    dailyTaskDao = database.dailyTaskDao()
                )
                val today    = LocalDate.now()
                val tomorrow = today.plusDays(1)

                listOf(today, tomorrow).forEach { date ->
                    val tasks = repository.getTasks(date)
                    ReminderScheduler.syncTasks(context, date, tasks, settings)
                }
            } catch (_: Exception) {
                // 재부팅 직후라 DB 로드 실패할 수 있음 — 조용히 무시
            }
        }
    }
}
