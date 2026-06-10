package com.helm.work

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.helm.data.DispatchCadence
import java.util.Calendar
import java.util.concurrent.TimeUnit

object HelmScheduler {
    private const val USAGE_WORK = "helm_usage_collect"
    private const val DISPATCH_WORK = "helm_dispatch"

    /** Collect usage roughly every couple of hours. */
    fun scheduleUsageCollection(context: Context) {
        val request = PeriodicWorkRequestBuilder<UsageCollectWorker>(2, TimeUnit.HOURS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            USAGE_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }

    /** (Re)schedule the Dispatch newsletter for the chosen cadence, targeting ~8am. */
    fun scheduleDispatch(context: Context, cadence: DispatchCadence) {
        val intervalHours = if (cadence == DispatchCadence.WEEKLY) 24L * 7 else 24L
        val request = PeriodicWorkRequestBuilder<DispatchWorker>(intervalHours, TimeUnit.HOURS)
            .setInitialDelay(initialDelayToMorning(), TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            DISPATCH_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    private fun initialDelayToMorning(): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 8); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return target.timeInMillis - now.timeInMillis
    }
}
