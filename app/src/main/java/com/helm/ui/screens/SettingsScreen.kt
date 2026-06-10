package com.helm.ui.screens

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.HealthConnectClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.helm.data.DispatchCadence
import com.helm.data.EnforcementMode
import com.helm.ui.HelmViewModel
import com.helm.ui.components.SectionCard

@Composable
fun SettingsScreen(vm: HelmViewModel) {
    val context = LocalContext.current
    val settings by vm.settings.collectAsState()
    var pin by remember { mutableStateOf("") }
    var currencyInput by remember { mutableStateOf(settings.currency) }
    var healthMsg by remember { mutableStateOf<String?>(null) }
    val healthStatus = remember { vm.healthAvailability() }
    val healthLauncher = rememberLauncherForActivityResult(vm.healthPermissionContract()) { granted ->
        if (granted.containsAll(vm.healthPermissions)) vm.syncHealth { healthMsg = it }
        else healthMsg = "Some health permissions were denied."
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SectionCard("Enforcement mode") {
            Text("Monitor only tracks and notifies. Enforce will (in Phase 2) actively block over-limit apps.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = settings.mode == EnforcementMode.MONITOR, onClick = { vm.setMode(EnforcementMode.MONITOR) }, label = { Text("Monitor") })
                FilterChip(selected = settings.mode == EnforcementMode.ENFORCE, onClick = { vm.setMode(EnforcementMode.ENFORCE) }, label = { Text("Enforce") })
            }
        }

        SectionCard("Dispatch cadence") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DispatchCadence.entries.forEach { c ->
                    FilterChip(selected = settings.cadence == c, onClick = { vm.setCadence(c) }, label = { Text(c.display) })
                }
            }
        }

        SectionCard("Currency") {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = currencyInput, onValueChange = { currencyInput = it.take(3) }, label = { Text("Symbol") }, singleLine = true, modifier = Modifier.weight(1f))
                Button(onClick = { vm.setCurrency(currencyInput.ifBlank { "₹" }) }) { Text("Save") }
            }
        }

        SectionCard("Bypass resistance (PIN)") {
            Text(if (settings.pinHash == null) "No PIN set — settings are open." else "A PIN is protecting settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(8) },
                label = { Text("New PIN") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(enabled = pin.length >= 4, onClick = { vm.setPin(pin); pin = "" }) { Text("Set PIN") }
                OutlinedButton(onClick = { vm.setPin(null); pin = "" }) { Text("Clear PIN") }
            }
        }

        SectionCard("Foreground watcher") {
            Text("Optional: enables \"what you're using now\" and future hard-enforcement blocking.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) { Text("Open accessibility settings") }
        }

        SectionCard("Health Connect") {
            val statusText = when (healthStatus) {
                HealthConnectClient.SDK_AVAILABLE -> "Available — connect to auto-sync steps, sleep, weight & workouts."
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "Update the Health Connect app to continue."
                else -> "Not available on this device. You can still log health manually on the Track tab."
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = healthStatus == HealthConnectClient.SDK_AVAILABLE,
                    onClick = { healthLauncher.launch(vm.healthPermissions) },
                ) { Text("Connect & sync") }
                OutlinedButton(
                    enabled = healthStatus == HealthConnectClient.SDK_AVAILABLE,
                    onClick = { vm.syncHealth { healthMsg = it } },
                ) { Text("Sync now") }
            }
            healthMsg?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
            }
        }

        SectionCard("About Helm") {
            Text("Helm v0.1 — your private control center. All data stays on this device. Built monitor-first.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
