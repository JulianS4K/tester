package com.helm.usage

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import com.helm.data.UsageRecord
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * Reads foreground time + launch counts from [UsageStatsManager].
 * Requires the user to grant "Usage access" in system settings.
 */
class UsageCollector(private val context: Context) {

    private val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val pm = context.packageManager
    private val labelCache = HashMap<String, String>()

    fun hasUsageAccess(): Boolean {
        return try {
            val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
            val mode = appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS,
                Process.myUid(),
                context.packageName,
            )
            mode == AppOpsManager.MODE_ALLOWED
        } catch (e: Exception) {
            false
        }
    }

    /** Build usage records for the local calendar day containing [atMillis]. */
    fun collectForDay(atMillis: Long = System.currentTimeMillis()): List<UsageRecord> {
        if (!hasUsageAccess()) return emptyList()

        val (start, end) = dayBounds(atMillis)
        val day = dayKey(atMillis)

        // Foreground time via event stream (more accurate than aggregate across days).
        val foreground = HashMap<String, Long>()
        val launches = HashMap<String, Int>()
        val lastResume = HashMap<String, Long>()

        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED, UsageEvents.Event.MOVE_TO_FOREGROUND -> {
                    lastResume[pkg] = event.timeStamp
                    launches[pkg] = (launches[pkg] ?: 0) + 1
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.MOVE_TO_BACKGROUND -> {
                    val started = lastResume.remove(pkg) ?: continue
                    if (event.timeStamp > started) {
                        foreground[pkg] = (foreground[pkg] ?: 0) + (event.timeStamp - started)
                    }
                }
            }
        }
        // Close out apps still in the foreground at query end.
        val now = minOf(end, System.currentTimeMillis())
        for ((pkg, started) in lastResume) {
            if (now > started) foreground[pkg] = (foreground[pkg] ?: 0) + (now - started)
        }

        return foreground.entries
            .filter { it.value >= 1_000 && it.key != context.packageName }
            .map { (pkg, millis) ->
                UsageRecord(
                    day = day,
                    packageName = pkg,
                    appLabel = labelFor(pkg),
                    totalMillis = millis,
                    launchCount = launches[pkg] ?: 0,
                )
            }
    }

    fun labelFor(pkg: String): String = labelCache.getOrPut(pkg) {
        try {
            val info = pm.getApplicationInfo(pkg, 0)
            pm.getApplicationLabel(info).toString()
        } catch (e: PackageManager.NameNotFoundException) {
            pkg.substringAfterLast('.')
        }
    }

    /** Launchable apps (excluding Helm itself), sorted by label, for limit/focus pickers. */
    fun launchableApps(): List<Pair<String, String>> {
        val intent = android.content.Intent(android.content.Intent.ACTION_MAIN)
            .addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(intent, 0)
            .mapNotNull { it.activityInfo?.packageName }
            .filter { it != context.packageName }
            .distinct()
            .map { it to labelFor(it) }
            .sortedBy { it.second.lowercase() }
    }

    companion object {
        private val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)

        fun dayKey(millis: Long): String = fmt.format(millis)

        fun dayBounds(millis: Long): Pair<Long, Long> {
            val cal = Calendar.getInstance().apply {
                timeInMillis = millis
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            val start = cal.timeInMillis
            cal.add(Calendar.DAY_OF_MONTH, 1)
            return start to cal.timeInMillis
        }
    }
}
