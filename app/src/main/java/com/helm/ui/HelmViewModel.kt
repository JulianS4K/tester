package com.helm.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.helm.data.AppDayTotal
import com.helm.data.AppLimit
import com.helm.data.DayTotal
import com.helm.data.DispatchCadence
import com.helm.data.DispatchReport
import com.helm.data.EnforcementMode
import com.helm.data.FocusWindow
import com.helm.data.HelmRepository
import com.helm.data.HelmSettings
import com.helm.data.LogEntry
import com.helm.data.LogKind
import com.helm.data.SettingsStore
import com.helm.dispatch.DispatchGenerator
import com.helm.usage.UsageCollector
import com.helm.util.Format
import com.helm.work.HelmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HelmViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = HelmRepository.get(app)

    val today: String = UsageCollector.dayKey(System.currentTimeMillis())
    private val weekAgo: String = Format.dayKeyDaysAgo(6)

    val settings: StateFlow<HelmSettings> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HelmSettings())

    val todayUsage: StateFlow<List<AppDayTotal>> =
        repo.usageForDay(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val weekUsage: StateFlow<List<AppDayTotal>> =
        repo.usageBetween(weekAgo, today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dailyTotals: StateFlow<List<DayTotal>> =
        repo.dailyTotals(weekAgo, today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayTotalMillis: StateFlow<Long> =
        repo.totalMillisForDay(today).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

    val recentLogs: StateFlow<List<LogEntry>> =
        repo.recentLogs(150).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val limits: StateFlow<List<AppLimit>> =
        repo.limits().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val focusWindows: StateFlow<List<FocusWindow>> =
        repo.focusWindows().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val dispatches: StateFlow<List<DispatchReport>> =
        repo.dispatches().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun hasUsageAccess(): Boolean = repo.hasUsageAccess()
    fun appLabel(pkg: String): String = repo.appLabel(pkg)
    fun installedApps(): List<Pair<String, String>> = repo.collector.launchableApps()

    fun refreshUsage() = viewModelScope.launch { repo.refreshUsage() }

    fun addLog(entry: LogEntry) = viewModelScope.launch { repo.addLog(entry) }
    fun deleteLog(id: Long) = viewModelScope.launch { repo.deleteLog(id) }

    fun setLimit(pkg: String, label: String, minutes: Int) = viewModelScope.launch {
        repo.upsertLimit(AppLimit(packageName = pkg, appLabel = label, dailyLimitMinutes = minutes))
    }
    fun removeLimit(pkg: String) = viewModelScope.launch { repo.deleteLimit(pkg) }
    fun toggleLimit(limit: AppLimit) = viewModelScope.launch { repo.upsertLimit(limit.copy(enabled = !limit.enabled)) }

    fun addFocus(window: FocusWindow) = viewModelScope.launch { repo.upsertFocus(window) }
    fun removeFocus(id: Long) = viewModelScope.launch { repo.deleteFocus(id) }
    fun toggleFocus(window: FocusWindow) = viewModelScope.launch { repo.upsertFocus(window.copy(enabled = !window.enabled)) }

    fun setMode(mode: EnforcementMode) = viewModelScope.launch { repo.settingsStore.setMode(mode) }
    fun setCurrency(c: String) = viewModelScope.launch { repo.settingsStore.setCurrency(c) }

    fun setCadence(c: DispatchCadence) = viewModelScope.launch {
        repo.settingsStore.setCadence(c)
        HelmScheduler.scheduleDispatch(getApplication(), c)
    }

    fun setPin(pin: String?) = viewModelScope.launch {
        repo.settingsStore.setPinHash(pin?.let { SettingsStore.hashPin(it) })
    }
    fun checkPin(pin: String): Boolean =
        settings.value.pinHash?.let { it == SettingsStore.hashPin(pin) } ?: true

    fun generateDispatchNow(onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.refreshUsage()
        val report = DispatchGenerator(repo).generate(settings.value.cadence)
        repo.insertDispatch(report)
        onDone()
    }

    // --- Health Connect ---
    fun healthAvailability(): Int = repo.health.availability()
    val healthPermissions: Set<String> get() = repo.health.permissions
    fun healthPermissionContract() =
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()

    fun syncHealth(onResult: (String) -> Unit) = viewModelScope.launch {
        val msg = runCatching { repo.health.syncToday(repo) }
            .getOrElse { "Health sync failed: ${it.message}" }
        onResult(msg)
    }

    fun importCsv(uri: android.net.Uri, onResult: (String) -> Unit) = viewModelScope.launch {
        val app = getApplication<Application>()
        val text = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
        }
        if (text.isNullOrBlank()) { onResult("Couldn't read that file."); return@launch }
        val result = com.helm.finance.CsvImporter.parse(text, settings.value.currency)
        repo.addLogs(result.entries)
        onResult("Imported ${result.entries.size} transactions · ${result.skipped} skipped (${result.detected}).")
    }
}
