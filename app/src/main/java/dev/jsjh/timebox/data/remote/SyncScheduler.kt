package dev.jsjh.timebox.data.remote

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dev.jsjh.timebox.auth.ActiveUserStore
import dev.jsjh.timebox.auth.initSupabase
import dev.jsjh.timebox.auth.supabase
import dev.jsjh.timebox.data.local.database.TaskDatabase
import io.github.jan.supabase.auth.auth
import java.util.concurrent.TimeUnit

/** Schedules one coalesced, network-constrained upload per signed-in account. */
object SyncScheduler {
    private const val INPUT_USER_ID = "sync_user_id"
    private const val WORK_PREFIX = "timebox-upload-"

    fun enqueueUpload(context: Context, userId: String) {
        if (userId.isBlank() || userId == "guest") return
        val request = OneTimeWorkRequestBuilder<PendingSyncWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .setInputData(workDataOf(INPUT_USER_ID to userId))
            .addTag(workName(userId))
            .build()
        WorkManager.getInstance(context.applicationContext).enqueueUniqueWork(
            workName(userId),
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context, userId: String) {
        if (userId.isBlank() || userId == "guest") return
        WorkManager.getInstance(context.applicationContext).cancelUniqueWork(workName(userId))
    }

    internal fun userId(input: androidx.work.Data): String = input.getString(INPUT_USER_ID).orEmpty()

    private fun workName(userId: String): String = WORK_PREFIX + userId.hashCode().toUInt().toString(16)
}

class PendingSyncWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val userId = SyncScheduler.userId(inputData)
        if (userId.isBlank() || userId == "guest") return Result.success()
        if (ActiveUserStore.readUserId(applicationContext) != userId) return Result.success()

        initSupabase(applicationContext)
        val sessionReady = runCatching {
            supabase.auth.awaitInitialization()
            supabase.auth.loadFromStorage() || supabase.auth.currentSessionOrNull() != null
        }.getOrDefault(false)
        if (!sessionReady) return Result.retry()

        return runCatching {
            val result = DurableSync.uploadPending(userId, TaskDatabase.get(applicationContext, userId))
            if (result.pendingCount == 0) Result.success() else Result.retry()
        }.getOrElse { error ->
            if (error.message.orEmpty().contains("account mismatch", ignoreCase = true)) {
                Result.success()
            } else {
                Result.retry()
            }
        }
    }
}
