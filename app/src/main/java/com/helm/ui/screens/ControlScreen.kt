package com.helm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.helm.data.AppLimit
import com.helm.data.EnforcementMode
import com.helm.data.FocusWindow
import com.helm.ui.HelmViewModel
import com.helm.ui.components.SectionCard
import com.helm.util.Format

@Composable
fun ControlScreen(vm: HelmViewModel) {
    val settings by vm.settings.collectAsState()
    val limits by vm.limits.collectAsState()
    val focus by vm.focusWindows.collectAsState()
    var showLimitDialog by remember { mutableStateOf(false) }
    var showFocusDialog by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SectionCard {
                Text("Enforcement: ${if (settings.mode == EnforcementMode.ENFORCE) "Enforce" else "Monitor"}", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (settings.mode == EnforcementMode.ENFORCE)
                        "Helm will alert you when limits are hit. Hard blocking arrives in Phase 2 (needs the accessibility service)."
                    else
                        "Monitor mode: Helm tracks limits and notifies you, but won't block apps. Switch to Enforce in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("App limits", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = { showLimitDialog = true }, label = { Text("Add") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            }
        }
        if (limits.isEmpty()) {
            item { SectionCard { Text("No limits yet. Add a daily minute budget for an app.") } }
        }
        items(limits, key = { it.packageName }) { limit ->
            LimitRow(limit, vm)
        }

        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Focus windows", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                AssistChip(onClick = { showFocusDialog = true }, label = { Text("Add") }, leadingIcon = { Icon(Icons.Filled.Add, null) })
            }
        }
        if (focus.isEmpty()) {
            item { SectionCard { Text("No focus windows. Schedule app-free time for work, study or sleep.") } }
        }
        items(focus, key = { it.id }) { window ->
            FocusRow(window, vm)
        }
    }

    if (showLimitDialog) {
        LimitDialog(vm, onDismiss = { showLimitDialog = false })
    }
    if (showFocusDialog) {
        FocusDialog(vm, onDismiss = { showFocusDialog = false })
    }
}

@Composable
private fun LimitRow(limit: AppLimit, vm: HelmViewModel) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(limit.appLabel, fontWeight = FontWeight.SemiBold)
                Text("${limit.dailyLimitMinutes} min/day", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = limit.enabled, onCheckedChange = { vm.toggleLimit(limit) })
            IconButton(onClick = { vm.removeLimit(limit.packageName) }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun FocusRow(window: FocusWindow, vm: HelmViewModel) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text(window.label, fontWeight = FontWeight.SemiBold)
                Text(
                    "${Format.formatMinuteOfDay(window.startMinute)}–${Format.formatMinuteOfDay(window.endMinute)} · ${daysLabel(window.daysMask)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                val count = window.blockedPackages.split(",").filter { it.isNotBlank() }.size
                Text("$count apps blocked", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = window.enabled, onCheckedChange = { vm.toggleFocus(window) })
            IconButton(onClick = { vm.removeFocus(window.id) }) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun LimitDialog(vm: HelmViewModel, onDismiss: () -> Unit) {
    val apps = remember { vm.installedApps() }
    var selected by remember { mutableStateOf<Pair<String, String>?>(null) }
    var minutes by remember { mutableStateOf("30") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = selected != null && minutes.toIntOrNull() != null,
                onClick = {
                    selected?.let { vm.setLimit(it.first, it.second, minutes.toInt()) }
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add app limit") },
        text = {
            Column {
                OutlinedTextField(
                    value = minutes, onValueChange = { minutes = it },
                    label = { Text("Daily limit (minutes)") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Text("Choose an app", style = MaterialTheme.typography.labelMedium)
                AppPicker(apps, single = selected?.let { listOf(it.first) } ?: emptyList()) { pkg ->
                    selected = apps.firstOrNull { it.first == pkg }
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FocusDialog(vm: HelmViewModel, onDismiss: () -> Unit) {
    val apps = remember { vm.installedApps() }
    var label by remember { mutableStateOf("Focus") }
    var start by remember { mutableStateOf("09:00") }
    var end by remember { mutableStateOf("17:00") }
    val days = remember { (0..6).map { it == 1 || it == 2 || it == 3 || it == 4 || it == 5 }.toMutableStateList() }
    val picked = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = label.isNotBlank() && parseTime(start) != null && parseTime(end) != null && picked.isNotEmpty(),
                onClick = {
                    vm.addFocus(
                        FocusWindow(
                            label = label,
                            startMinute = parseTime(start)!!,
                            endMinute = parseTime(end)!!,
                            daysMask = daysToMask(days),
                            blockedPackages = picked.joinToString(","),
                        ),
                    )
                    onDismiss()
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add focus window") },
        text = {
            Column {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("Start HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("End HH:MM") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    val names = listOf("S", "M", "T", "W", "T", "F", "S")
                    names.forEachIndexed { i, n ->
                        FilterChip(selected = days[i], onClick = { days[i] = !days[i] }, label = { Text(n) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Block these apps", style = MaterialTheme.typography.labelMedium)
                AppPicker(apps, multi = picked) { pkg ->
                    if (picked.contains(pkg)) picked.remove(pkg) else picked.add(pkg)
                }
            }
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AppPicker(
    apps: List<Pair<String, String>>,
    single: List<String> = emptyList(),
    multi: SnapshotStateList<String>? = null,
    onToggle: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, apps) {
        if (query.isBlank()) apps.take(60) else apps.filter { it.second.contains(query, ignoreCase = true) }.take(60)
    }
    Column {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text("Search apps") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Box(Modifier.heightIn(max = 220.dp)) {
            LazyColumn {
                items(filtered, key = { it.first }) { (pkg, name) ->
                    val checked = multi?.contains(pkg) ?: single.contains(pkg)
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(name, modifier = Modifier.weight(1f))
                        FilterChip(selected = checked, onClick = { onToggle(pkg) }, label = { Text(if (checked) "✓" else "+") })
                    }
                }
            }
        }
    }
}

private fun parseTime(s: String): Int? {
    val parts = s.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun daysToMask(days: List<Boolean>): Int {
    var mask = 0
    days.forEachIndexed { i, on -> if (on) mask = mask or (1 shl i) }
    return mask
}

private fun daysLabel(mask: Int): String {
    val names = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
    val on = (0..6).filter { (mask shr it) and 1 == 1 }
    return when {
        on.size == 7 -> "Every day"
        on == listOf(1, 2, 3, 4, 5) -> "Weekdays"
        on.isEmpty() -> "No days"
        else -> on.joinToString(",") { names[it] }
    }
}
