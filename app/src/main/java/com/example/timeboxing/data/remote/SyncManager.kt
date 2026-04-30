package com.example.timeboxing.data.remote

import com.example.timeboxing.data.local.dao.DailyTaskDao
import com.example.timeboxing.data.local.dao.TaskTemplateDao
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RemoteStatusUi(
    val templateCount: Int,
    val taskCount: Int,
    val checkedAt: String
)

sealed class SyncState {
    data object Idle : SyncState()
    data object Syncing : SyncState()
    data class Success(val time: String) : SyncState()
    data class Error(val message: String) : SyncState()
}

object SyncManager {
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    private val _remoteStatus = MutableStateFlow<RemoteStatusUi?>(null)
    val remoteStatus: StateFlow<RemoteStatusUi?> = _remoteStatus.asStateFlow()

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
                taskCount = status.taskCount,
                checkedAt = time
            )
            _state.value = SyncState.Success(time)
        } catch (e: Exception) {
            _state.value = SyncState.Error(userFriendlySyncError(e))
        }
    }

    suspend fun refreshRemoteStatus(userId: String) {
        runCatching {
            val status = SupabaseSync.status(userId)
            val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
            _remoteStatus.value = RemoteStatusUi(
                templateCount = status.templateCount,
                taskCount = status.taskCount,
                checkedAt = time
            )
        }
    }

    fun resetToIdle() {
        _state.value = SyncState.Idle
    }

    private fun userFriendlySyncError(error: Exception): String {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "account mismatch" in lower ->
                "현재 로그인 계정과 동기화 계정이 달라요. 로그아웃 후 다시 로그인해주세요."
            "verification failed" in lower ->
                "업로드 후 서버 데이터 수가 맞지 않아요. 다시 동기화해주세요."
            "row-level security" in lower ->
                "동기화 권한 오류가 발생했어요. Supabase의 계정별 데이터 정책을 확인해주세요."
            "on_conflict" in lower || "unique" in lower ->
                "동기화 기준키 설정이 맞지 않아요. Supabase 테이블 키를 확인해주세요."
            "network" in lower || "timeout" in lower ->
                "네트워크 연결이 불안정해서 동기화하지 못했어요."
            else ->
                "동기화 중 오류가 발생했어요."
        }
    }
}
