package com.helm

import android.app.Application
import com.helm.data.HelmRepository
import com.helm.work.HelmScheduler
import com.helm.work.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class HelmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        HelmScheduler.scheduleUsageCollection(this)
        CoroutineScope(Dispatchers.Default).launch {
            val cadence = HelmRepository.get(this@HelmApp).settings.first().cadence
            HelmScheduler.scheduleDispatch(this@HelmApp, cadence)
        }
    }
}
