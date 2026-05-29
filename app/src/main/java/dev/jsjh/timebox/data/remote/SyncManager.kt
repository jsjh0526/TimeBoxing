package dev.jsjh.timebox.data.remote

import dev.jsjh.timebox.data.local.dao.DailyTaskDao
import dev.jsjh.timebox.data.local.dao.TaskTemplateDao
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
    data class Error(val type: SyncErrorType) : SyncState()
}

enum class SyncErrorType {
    AccountMismatch,
    VerificationFailed,
    RowLevelSecurity,
    Conflict,
    Network,
    Unknown
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
            _state.value = SyncState.Error(syncErrorType(e))
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

    private fun syncErrorType(error: Exception): SyncErrorType {
        val raw = error.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "account mismatch" in lower -> SyncErrorType.AccountMismatch
            "verification failed" in lower -> SyncErrorType.VerificationFailed
            "row-level security" in lower -> SyncErrorType.RowLevelSecurity
            "on_conflict" in lower || "unique" in lower -> SyncErrorType.Conflict
            "network" in lower || "timeout" in lower -> SyncErrorType.Network
            else -> SyncErrorType.Unknown
        }
    }
}
