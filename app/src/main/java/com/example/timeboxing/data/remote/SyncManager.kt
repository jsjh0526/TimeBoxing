package com.example.timeboxing.data.remote

import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalTime
import java.time.format.DateTimeFormatter

data class RemoteStatusUi(
    val templateCount: Int,
    val taskCount: Int,
    val checkedAt: String
)

sealed class SyncState {
    data object Idle    : SyncState()
    data object Syncing : SyncState()
    data class Success(val time: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

object SyncManager {
    private val _state        = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _remoteStatus = MutableStateFlow<RemoteStatusUi?>(null)
    val remoteStatus: StateFlow<RemoteStatusUi?> = _remoteStatus.asStateFlow()

    // syncAll()이 반환한 카운트를 바로 사용해서 추가 상태 조회를 피한다.
    suspend fun syncAll(
        userId: String,
        templateDao: TaskTemplateDao,
        dailyTaskDao: DailyTaskDao
    ) {
        if (_state.value is SyncState.Syncing) return
        _state.value = SyncState.Syncing
        try {
            val status = SupabaseSync.syncAll(userId, templateDao, dailyTaskDao)
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            _remoteStatus.value = RemoteStatusUi(
                templateCount = status.templateCount,
                taskCount     = status.taskCount,
                checkedAt     = time
            )
            _state.value = SyncState.Success(time)
        } catch (e: Exception) {
            _state.value = SyncState.Error(e.message ?: "Sync failed")
        }
    }

    // 앱 진입 시 클라우드 현황만 조회하고, 로컬 DB는 건드리지 않는다.
    suspend fun refreshRemoteStatus(userId: String) {
        runCatching {
            val status = SupabaseSync.status(userId)
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            _remoteStatus.value = RemoteStatusUi(
                templateCount = status.templateCount,
                taskCount     = status.taskCount,
                checkedAt     = time
            )
        }
    }

    fun resetToIdle() { _state.value = SyncState.Idle }
}
