package com.helm.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.helm.data.DispatchReport
import com.helm.ui.HelmViewModel
import com.helm.ui.components.SectionCard
import com.helm.util.Format

@Composable
fun DispatchScreen(vm: HelmViewModel) {
    val dispatches by vm.dispatches.collectAsState()
    val settings by vm.settings.collectAsState()
    var open by remember { mutableStateOf<DispatchReport?>(null) }
    var generating by remember { mutableStateOf(false) }

    val current = open
    if (current != null) {
        DispatchDetail(current) { open = null }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard("The Dispatch") {
                Text(
                    "Your personal newsletter — a digest of screen time, money, health and habits, " +
                        "delivered ${settings.cadence.display.lowercase()}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = !generating,
                    onClick = {
                        generating = true
                        vm.generateDispatchNow { generating = false }
                    },
                ) {
                    Icon(Icons.Filled.Refresh, null)
                    Spacer(Modifier.height(0.dp))
                    Text(if (generating) "  Generating…" else "  Generate now")
                }
            }
        }
        if (dispatches.isEmpty()) {
            item { SectionCard { Text("No dispatches yet. Tap \"Generate now\" to create your first one.") } }
        }
        items(dispatches, key = { it.id }) { report ->
            SectionCard {
                Text(report.periodLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                Text(report.headline, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(6.dp))
                Text(Format.dateTime(report.createdAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { open = report }) { Text("Read →") }
            }
        }
    }
}

@Composable
private fun DispatchDetail(report: DispatchReport, onBack: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, null); Text("  Back")
            }
            Spacer(Modifier.height(8.dp))
        }
        item { Markdown(report.markdown) }
    }
}

/** Minimal markdown rendering — enough for the Dispatch format. */
@Composable
private fun Markdown(text: String) {
    Column {
        text.lines().forEach { raw ->
            val line = raw.trimEnd()
            when {
                line.startsWith("# ") -> Text(line.removePrefix("# "), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
                line.startsWith("### ") -> Text(line.removePrefix("### "), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 6.dp))
                line.startsWith("## ") -> Text(line.removePrefix("## "), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp, bottom = 2.dp))
                line.startsWith("- ") || line.startsWith("  - ") -> {
                    val indent = if (line.startsWith("  - ")) 16.dp else 4.dp
                    Text("•  " + stripBold(line.substringAfter("- ")), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = indent, top = 1.dp, bottom = 1.dp))
                }
                line.startsWith("---") -> Divider(Modifier.padding(vertical = 8.dp))
                line.startsWith("_") && line.endsWith("_") -> Text(line.trim('_'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                line.isBlank() -> Spacer(Modifier.height(4.dp))
                else -> Text(stripBold(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private fun stripBold(s: String): String = s.replace("**", "")
