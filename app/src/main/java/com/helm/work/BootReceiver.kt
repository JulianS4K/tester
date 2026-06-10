package com.helm.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.helm.data.HelmRepository
import com.helm.data.DispatchCadence
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Re-arms periodic work after a reboot. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pending = goAsync()
        HelmScheduler.scheduleUsageCollection(context)
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val cadence = HelmRepository.get(context).settings.first().cadence
                HelmScheduler.scheduleDispatch(context, cadence)
            } catch (e: Exception) {
                HelmScheduler.scheduleDispatch(context, DispatchCadence.WEEKLY)
            } finally {
                pending.finish()
            }
        }
    }
}
