package com.mtga.app.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.mtga.app.data.repository.TimelineRepository
import com.mtga.app.data.settings.SettingsStore
import org.koin.core.context.GlobalContext
import java.util.concurrent.TimeUnit

/**
 * Polls followed accounts in the background.
 *
 * This is what turns a five post window into real history. X only renders the
 * head of a profile for logged out visitors, and no cursor exists to go
 * further back. But if MTGA keeps checking and keeps everything it sees, the
 * archive grows forward from the day you start. You cannot recover last month,
 * you can own next month.
 *
 * Each run reuses the normal refresh path, so results land in the same cache
 * the timeline reads, deduplicated by post id.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getOrNull() ?: return Result.retry()
        val settings = koin.get<SettingsStore>()
        if (!settings.current.backgroundSync) return Result.success()

        val repository = koin.get<TimelineRepository>()
        return runCatching { repository.refresh() }
            .fold(
                onSuccess = { merged ->
                    // A partial failure is normal with fragile upstreams and is
                    // not worth a retry storm. The next scheduled run covers it.
                    if (merged.posts.isEmpty() && merged.errors.isNotEmpty()) {
                        Result.retry()
                    } else {
                        Result.success()
                    }
                },
                onFailure = { Result.retry() }
            )
    }

    companion object {
        private const val NAME = "mtga-sync"

        fun schedule(context: Context, intervalMinutes: Int, wifiOnly: Boolean) {
            // WorkManager's floor is 15 minutes, and anything shorter would be
            // rude to instances that are already rate limiting us.
            val interval = intervalMinutes.coerceAtLeast(15).toLong()

            val request = PeriodicWorkRequestBuilder<SyncWorker>(interval, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(
                            if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
                        )
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(NAME)
        }
    }
}
