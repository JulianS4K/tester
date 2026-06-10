package com.helm.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.helm.MainActivity
import com.helm.R

object Notifications {
    const val CHANNEL_DISPATCH = "dispatch"
    const val CHANNEL_LIMITS = "limits"

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DISPATCH, "The Dispatch", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Your periodic self-newsletter"
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_LIMITS, "Limits & focus", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Alerts when you hit an app limit or enter a focus window"
            },
        )
    }

    fun notify(context: Context, channel: String, id: Int, title: String, text: String) {
        ensureChannels(context)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = android.app.PendingIntent.getActivity(
            context, id, intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, notification)
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS not granted yet — ignore.
        }
    }
}
