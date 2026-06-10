package com.helm.data

import android.content.Context
import com.helm.usage.UsageCollector
import kotlinx.coroutines.flow.Flow

/** Single entry point the UI and workers use for data + actions. */
class HelmRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = HelmDatabase.get(appContext).dao()
    val settingsStore = SettingsStore(appContext)
    val collector = UsageCollector(appContext)

    val settings: Flow<HelmSettings> = settingsStore.settings

    // --- Usage ---
    fun usageForDay(day: String) = dao.usageForDay(day)
    fun usageBetween(from: String, to: String) = dao.usageBetween(from, to)
    fun totalMillisForDay(day: String) = dao.totalMillisForDay(day)
    fun dailyTotals(from: String, to: String) = dao.dailyTotals(from, to)

    /** Pull the latest stats from the OS into the database. Safe to call often. */
    suspend fun refreshUsage(): Boolean = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val records = collector.collectForDay()
        if (records.isNotEmpty()) dao.upsertUsage(records)
        collector.hasUsageAccess()
    }

    /** Bulk-insert manual logs (used by CSV import). */
    suspend fun addLogs(entries: List<LogEntry>) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        entries.forEach { dao.insertLog(it) }
    }

    suspend fun millisForApp(day: String, pkg: String) = dao.millisForApp(day, pkg)
    fun hasUsageAccess() = collector.hasUsageAccess()
    fun appLabel(pkg: String) = collector.labelFor(pkg)

    // --- Logs ---
    fun recentLogs(limit: Int = 100) = dao.recentLogs(limit)
    fun logsOfKinds(kinds: List<LogKind>) = dao.logsOfKinds(kinds)
    suspend fun logsBetween(from: Long, to: Long) = dao.logsBetween(from, to)
    suspend fun addLog(entry: LogEntry) = dao.insertLog(entry)
    suspend fun deleteLog(id: Long) = dao.deleteLog(id)

    // --- Limits & focus ---
    fun limits() = dao.limits()
    suspend fun activeLimits() = dao.activeLimits()
    suspend fun upsertLimit(limit: AppLimit) = dao.upsertLimit(limit)
    suspend fun deleteLimit(pkg: String) = dao.deleteLimit(pkg)

    fun focusWindows() = dao.focusWindows()
    suspend fun activeFocusWindows() = dao.activeFocusWindows()
    suspend fun upsertFocus(window: FocusWindow) = dao.upsertFocus(window)
    suspend fun deleteFocus(id: Long) = dao.deleteFocus(id)

    // --- Dispatch ---
    fun dispatches(limit: Int = 30) = dao.dispatches(limit)
    suspend fun insertDispatch(report: DispatchReport) = dao.insertDispatch(report)

    companion object {
        @Volatile private var instance: HelmRepository? = null
        fun get(context: Context): HelmRepository = instance ?: synchronized(this) {
            instance ?: HelmRepository(context).also { instance = it }
        }
    }
}
