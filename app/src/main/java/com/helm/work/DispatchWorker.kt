package com.helm.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.helm.data.HelmRepository
import com.helm.dispatch.DispatchGenerator
import kotlinx.coroutines.flow.first

/** Generates a Dispatch report on the user's chosen cadence and notifies them. */
class DispatchWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val repo = HelmRepository.get(applicationContext)
        repo.refreshUsage()
        val cadence = repo.settings.first().cadence
        val report = DispatchGenerator(repo).generate(cadence)
        repo.insertDispatch(report)
        Notifications.notify(
            applicationContext,
            Notifications.CHANNEL_DISPATCH,
            1001,
            "🗞️ Your ${cadence.display} Dispatch is ready",
            report.headline,
        )
        return Result.success()
    }
}
