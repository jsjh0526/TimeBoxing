package com.example.timeboxing.data.remote

import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

sealed class SyncState {
    data object Idle    : SyncState()
    data object Syncing : SyncState()
    data class Success(val time: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

object SyncManager {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ) {
        if (_state.value is SyncState.Syncing) return  // 중복 실행 방지
        _state.value = SyncState.Syncing
        try {
            SupabaseSync.syncAll(userId, templateDao, dailyTaskDao)
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            _state.value = SyncState.Success(time)
        } catch (e: Exception) {
            _state.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    fun resetToIdle() {
        _state.value = SyncState.Idle
    }
}
