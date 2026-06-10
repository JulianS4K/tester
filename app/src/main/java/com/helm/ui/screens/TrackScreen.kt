package com.helm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.helm.data.LogEntry
import com.helm.data.LogKind
import com.helm.ui.HelmViewModel
import com.helm.ui.components.SectionCard
import com.helm.util.Format

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TrackScreen(vm: HelmViewModel) {
    val logs by vm.recentLogs.collectAsState()
    val settings by vm.settings.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    Scaffold(
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showDialog = true },
                icon = { Icon(Icons.Filled.Add, null) },
                text = { Text("Add log") },
            )
        },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize().padding(inner).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (logs.isEmpty()) {
                item { SectionCard { Text("Nothing logged yet. Tap \"Add log\" to record mood, a habit, weight, sleep, an expense and more.") } }
            }
            items(logs, key = { it.id }) { entry ->
                LogRow(entry, settings.currency) { vm.deleteLog(entry.id) }
            }
            item { Spacer(Modifier.height(72.dp)) }
        }
    }

    if (showDialog) {
        AddLogDialog(
            currency = settings.currency,
            onDismiss = { showDialog = false },
            onSave = { entry -> vm.addLog(entry); showDialog = false },
        )
    }
}

@Composable
private fun LogRow(entry: LogEntry, currency: String, onDelete: () -> Unit) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("${entry.kind.display} · ${entry.title}", fontWeight = FontWeight.SemiBold)
                val valueText = entry.value?.let { v ->
                    when (entry.kind) {
                        LogKind.EXPENSE, LogKind.INCOME -> "$currency${"%.0f".format(v)}"
                        else -> "${trimNum(v)} ${entry.unit ?: ""}".trim()
                    }
                }
                val sub = listOfNotNull(valueText, entry.category, entry.note).joinToString(" · ")
                if (sub.isNotBlank()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.dateTime(entry.timestamp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete", tint = MaterialTheme.colorScheme.error) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AddLogDialog(currency: String, onDismiss: () -> Unit, onSave: (LogEntry) -> Unit) {
    var kind by remember { mutableStateOf(LogKind.MOOD) }
    var title by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank() || kind == LogKind.MOOD,
                onClick = {
                    onSave(
                        LogEntry(
                            kind = kind,
                            timestamp = System.currentTimeMillis(),
                            title = title.ifBlank { kind.display },
                            value = value.toDoubleOrNull(),
                            unit = unitFor(kind, currency),
                            category = category.ifBlank { null },
                            note = note.ifBlank { null },
                        ),
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Add log") },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    LogKind.entries.forEach { k ->
                        FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.display) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(titleHint(kind)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (kind != LogKind.NOTE) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text(valueHint(kind, currency)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (kind == LogKind.EXPENSE || kind == LogKind.INCOME || kind == LogKind.WORKOUT || kind == LogKind.CUSTOM) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }
        },
    )
}

private fun trimNum(v: Double): String = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()

private fun titleHint(kind: LogKind) = when (kind) {
    LogKind.MOOD -> "How you feel (e.g. Great)"
    LogKind.HABIT -> "Habit name (e.g. Read)"
    LogKind.NOTE -> "Note title"
    LogKind.EXPENSE -> "What you bought"
    LogKind.INCOME -> "Source"
    LogKind.WORKOUT -> "Workout (e.g. Run)"
    else -> "Label"
}

private fun valueHint(kind: LogKind, currency: String) = when (kind) {
    LogKind.MOOD -> "Mood 1–5"
    LogKind.WEIGHT -> "Weight (kg)"
    LogKind.SLEEP -> "Hours slept"
    LogKind.STEPS -> "Steps"
    LogKind.WATER -> "Water (ml)"
    LogKind.EXPENSE, LogKind.INCOME -> "Amount ($currency)"
    LogKind.WORKOUT -> "Minutes"
    else -> "Value"
}

private fun unitFor(kind: LogKind, currency: String): String? = when (kind) {
    LogKind.WEIGHT -> "kg"
    LogKind.SLEEP -> "h"
    LogKind.STEPS -> "steps"
    LogKind.WATER -> "ml"
    LogKind.EXPENSE, LogKind.INCOME -> currency
    LogKind.WORKOUT -> "min"
    LogKind.MOOD -> "/5"
    else -> null
}
