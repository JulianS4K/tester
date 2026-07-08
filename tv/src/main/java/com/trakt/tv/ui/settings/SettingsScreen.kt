@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.data.remote.TraktConfig

@Composable
fun SettingsScreen(
    signedIn: Boolean,
    username: String?,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(start = 48.dp, top = 64.dp, end = 48.dp).width(560.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(16.dp))
        Text("Account", style = MaterialTheme.typography.titleLarge)
        Text(
            text = if (signedIn) "Signed in as ${username ?: "your Trakt account"}." else "Not signed in.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (signedIn) {
            Button(onClick = onSignOut) { Text("Sign out") }
        } else {
            Button(onClick = onSignIn) { Text("Sign in with Trakt") }
        }

        Spacer(Modifier.height(24.dp))
        Text("About", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Trakt TV — an Android TV / Fire TV client for Trakt.\n" +
                "Data & scrobbling powered by the Trakt API (api.trakt.tv).",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = if (TraktConfig.isConfigured) "API key: configured ✓" else "API key: NOT configured — add it to local.properties.",
            style = MaterialTheme.typography.labelMedium,
            color = if (TraktConfig.isConfigured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
