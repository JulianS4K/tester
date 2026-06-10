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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.helm.data.AppDayTotal
import com.helm.ui.HelmViewModel
import com.helm.ui.components.ProgressBar
import com.helm.ui.components.SectionCard
import com.helm.util.Format

@Composable
fun UsageScreen(vm: HelmViewModel) {
    var weekly by remember { mutableStateOf(false) }
    val today by vm.todayUsage.collectAsState()
    val week by vm.weekUsage.collectAsState()
    val list = if (weekly) week else today
    val max = (list.maxOfOrNull { it.totalMillis } ?: 1L).coerceAtLeast(1L)
    val total = list.sumOf { it.totalMillis }

    LazyColumn(
        Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Today") })
                FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Last 7 days") })
            }
        }
        item {
            SectionCard {
                Text(if (weekly) "Weekly total" else "Today's total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Format.duration(total), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            }
        }
        if (list.isEmpty()) {
            item {
                SectionCard {
                    Text("No usage recorded yet. Make sure Usage access is granted on the Home tab, then check back after using a few apps.")
                }
            }
        }
        items(list, key = { it.packageName }) { app ->
            AppUsageRow(app, max)
        }
    }
}

@Composable
private fun AppUsageRow(app: AppDayTotal, max: Long) {
    SectionCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(app.appLabel, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(Format.duration(app.totalMillis), style = MaterialTheme.typography.titleSmall)
        }
        Spacer(Modifier.height(8.dp))
        ProgressBar(fraction = app.totalMillis.toFloat() / max, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(6.dp))
        Text("${app.launchCount} opens", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
