package com.helm.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.item
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.DisposableEffect
import com.helm.data.LogGroup
import com.helm.data.LogKind
import com.helm.ui.HelmViewModel
import com.helm.ui.components.SectionCard
import com.helm.ui.components.StatTile
import com.helm.ui.components.BarChart
import com.helm.util.Format

@Composable
fun DashboardScreen(vm: HelmViewModel, onOpenUsage: () -> Unit, onOpenTrack: () -> Unit) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(vm.hasUsageAccess()) }

    // Re-check usage access + refresh when returning to the screen.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = vm.hasUsageAccess()
                if (hasAccess) vm.refreshUsage()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val todayTotal by vm.todayTotalMillis.collectAsState()
    val daily by vm.dailyTotals.collectAsState()
    val topApps by vm.todayUsage.collectAsState()
    val logs by vm.recentLogs.collectAsState()
    val dispatches by vm.dispatches.collectAsState()
    val settings by vm.settings.collectAsState()

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (!hasAccess) {
            item {
                SectionCard("Turn on screen-time tracking") {
                    Text(
                        "Helm needs \"Usage access\" to measure your app screen time. " +
                            "This stays entirely on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }) { Text("Grant usage access") }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatTile("Screen time today", Format.duration(todayTotal), MaterialTheme.colorScheme.primary, Modifier.weight(1f))
                val unlocks = topApps.sumOf { it.launchCount }
                StatTile("App opens today", unlocks.toString(), MaterialTheme.colorScheme.secondary, Modifier.weight(1f))
            }
        }

        item {
            SectionCard("Last 7 days") {
                val ordered = lastSevenDays(vm.today)
                val map = daily.associate { it.day to it.totalMillis }
                BarChart(
                    values = ordered.map { Format.minutes(map[it] ?: 0L).toFloat() },
                    labels = ordered.map { it.substring(8) },
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = onOpenUsage) { Text("See app breakdown →") }
            }
        }

        item {
            val expenses = logs.filter { it.kind == LogKind.EXPENSE }.sumOf { it.value ?: 0.0 }
            val income = logs.filter { it.kind == LogKind.INCOME }.sumOf { it.value ?: 0.0 }
            val moods = logs.filter { it.kind == LogKind.MOOD }.mapNotNull { it.value }
            SectionCard("Today at a glance") {
                Text("💰 Net ${settings.currency}${"%.0f".format(income - expenses)} · spent ${settings.currency}${"%.0f".format(expenses)}")
                Spacer(Modifier.height(4.dp))
                Text("❤️ ${logs.count { it.kind.group == LogGroup.HEALTH }} health logs")
                Spacer(Modifier.height(4.dp))
                Text("🙂 Mood " + if (moods.isNotEmpty()) "${"%.1f".format(moods.average())}/5" else "—")
                Spacer(Modifier.height(10.dp))
                TextButton(onClick = onOpenTrack) { Text("Add a log →") }
            }
        }

        dispatches.firstOrNull()?.let { latest ->
            item {
                SectionCard("Latest Dispatch") {
                    Text(latest.periodLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    Text(latest.headline, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

/** Returns the last 7 day-keys ending today, oldest first. */
internal fun lastSevenDays(today: String): List<String> =
    (6 downTo 0).map { Format.dayKeyDaysAgo(it) }
