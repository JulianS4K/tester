@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.trakt.tv.data.model.MediaItem
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Launcher-style status bar: live clock + date and the signed-in user. */
@Composable
fun StatusBar(username: String?, modifier: Modifier = Modifier) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(20_000)
        }
    }
    val time = remember(now.hour, now.minute) { now.format(DateTimeFormatter.ofPattern("h:mm a")) }
    val date = remember(now.dayOfYear) { now.format(DateTimeFormatter.ofPattern("EEE, MMM d")) }

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 48.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        if (username != null) {
            Text("👤 $username", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(20.dp))
        }
        Text(date, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text(time, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** Immersive spotlight header at the top of Home. */
@Composable
fun FeaturedHero(
    item: MediaItem,
    onDetails: () -> Unit,
    onWatchlist: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().height(440.dp)) {
        if (item.backdropUrl != null) {
            AsyncImage(
                model = item.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x660B0B0F), Color(0xFF0B0B0F)))))
        Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(Color(0xFF0B0B0F), Color(0x000B0B0F)))))

        Column(
            Modifier.align(Alignment.BottomStart).padding(start = 48.dp, end = 48.dp, bottom = 28.dp).fillMaxWidth(0.6f),
        ) {
            Text(item.title, style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold, maxLines = 2)
            Spacer(Modifier.height(8.dp))
            val meta = listOfNotNull(
                item.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                item.year?.toString(),
                item.network,
                item.genres.take(2).joinToString(", ").ifBlank { null },
            ).joinToString("   ·   ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!item.overview.isNullOrBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(item.overview, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Button(onClick = onDetails) { Text("View Details") }
                Button(onClick = onWatchlist) { Text("＋ Watchlist") }
            }
        }
    }
}

/** Static placeholder skeleton shown while Home content loads. */
@Composable
fun HomeSkeleton(modifier: Modifier = Modifier) {
    val block = MaterialTheme.colorScheme.surfaceVariant
    Column(modifier.fillMaxWidth().padding(top = 24.dp)) {
        Box(Modifier.padding(start = 48.dp).width(360.dp).height(440.dp).clip(RoundedCornerShape(16.dp)).background(block))
        repeat(3) {
            Spacer(Modifier.height(28.dp))
            Box(Modifier.padding(start = 48.dp).width(180.dp).height(22.dp).clip(RoundedCornerShape(6.dp)).background(block))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.padding(start = 48.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(6) {
                    Box(Modifier.size(width = 150.dp, height = 225.dp).clip(RoundedCornerShape(12.dp)).background(block))
                }
            }
        }
    }
}
