package com.helm.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helm.data.EnforcementMode
import com.helm.data.HelmRepository
import com.helm.usage.UsageCollector
import com.helm.util.Format
import kotlinx.coroutines.flow.first

/** Periodically refreshes usage stats and checks app limits. */
class UsageCollectWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = HelmRepository.get(applicationContext)
        val granted = repo.refreshUsage()
        if (!granted) return Result.success()

        // Limit checks — in MONITOR mode we only notify; ENFORCE adds blocking (Phase 2).
        val today = UsageCollector.dayKey(System.currentTimeMillis())
        val mode = repo.settings.first().mode
        for (limit in repo.activeLimits()) {
            val usedMin = Format.minutes(repo.millisForApp(today, limit.packageName))
            if (usedMin >= limit.dailyLimitMinutes) {
                val verb = if (mode == EnforcementMode.ENFORCE) "is now blocked" else "passed its limit"
                Notifications.notify(
                    applicationContext,
                    Notifications.CHANNEL_LIMITS,
                    limit.packageName.hashCode(),
                    "${limit.appLabel} $verb",
                    "You've used ${limit.appLabel} for ${usedMin}m today (limit ${limit.dailyLimitMinutes}m).",
                )
            }
        }
        return Result.success()
    }
}
