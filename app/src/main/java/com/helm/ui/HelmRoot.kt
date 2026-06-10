package com.helm.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.viewmodel.compose.viewModel
import com.helm.ui.screens.ControlScreen
import com.helm.ui.screens.DashboardScreen
import com.helm.ui.screens.DispatchScreen
import com.helm.ui.screens.SettingsScreen
import com.helm.ui.screens.TrackScreen
import com.helm.ui.screens.UsageScreen

private enum class Tab(val label: String, val icon: ImageVector) {
    DASHBOARD("Home", Icons.Filled.Dashboard),
    USAGE("Usage", Icons.Filled.BarChart),
    TRACK("Track", Icons.Filled.EditNote),
    CONTROL("Control", Icons.Filled.Shield),
    DISPATCH("Dispatch", Icons.Filled.Mail),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HelmRoot(vm: HelmViewModel = viewModel()) {
    var tab by remember { mutableStateOf(Tab.DASHBOARD) }
    var showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else "Helm · ${tab.label}") },
                actions = {
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        bottomBar = {
            if (!showSettings) {
                NavigationBar {
                    Tab.entries.forEach { t ->
                        NavigationBarItem(
                            selected = tab == t,
                            onClick = { tab = t },
                            icon = { Icon(t.icon, contentDescription = t.label) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        },
    ) { inner ->
        Box(Modifier.fillMaxSize().padding(inner)) {
            when {
                showSettings -> SettingsScreen(vm)
                tab == Tab.DASHBOARD -> DashboardScreen(vm, onOpenUsage = { tab = Tab.USAGE }, onOpenTrack = { tab = Tab.TRACK })
                tab == Tab.USAGE -> UsageScreen(vm)
                tab == Tab.TRACK -> TrackScreen(vm)
                tab == Tab.CONTROL -> ControlScreen(vm)
                tab == Tab.DISPATCH -> DispatchScreen(vm)
            }
        }
    }
}
