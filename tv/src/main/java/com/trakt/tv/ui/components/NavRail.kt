@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.trakt.tv.ui.Destination

private data class RailItem(val label: String, val icon: ImageVector, val destination: Destination)

@Composable
fun NavRail(
    current: Destination,
    signedIn: Boolean,
    username: String?,
    onSelect: (Destination) -> Unit,
    onAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val items = listOf(
        RailItem("Home", Icons.Filled.Home, Destination.Home),
        RailItem("Search", Icons.Filled.Search, Destination.Search),
        RailItem("Library", Icons.Filled.Bookmarks, Destination.Library),
        RailItem("Settings", Icons.Filled.Settings, Destination.Settings),
    )

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(220.dp)
            .padding(vertical = 24.dp, horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = "TRAKT",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, bottom = 20.dp),
        )

        items.forEach { item ->
            RailButton(
                label = item.label,
                icon = item.icon,
                selected = current == item.destination,
                onClick = { onSelect(item.destination) },
            )
        }

        Spacer(Modifier.weight(1f))

        RailButton(
            label = if (signedIn) (username ?: "Account") else "Sign in",
            icon = if (signedIn) Icons.Filled.AccountCircle else Icons.AutoMirrored.Filled.Login,
            selected = current == Destination.SignIn,
            onClick = onAccount,
        )
    }
}

@Composable
private fun RailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(shape = MaterialTheme.shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent,
            contentColor = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContainerColor = MaterialTheme.colorScheme.primary,
            focusedContentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(text = label, style = MaterialTheme.typography.titleSmall)
        }
    }
}
