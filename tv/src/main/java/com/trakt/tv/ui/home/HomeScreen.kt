@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.home

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.FeaturedHero
import com.trakt.tv.ui.components.HomeSkeleton
import com.trakt.tv.ui.components.MediaRow
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.launcher.LaunchApp
import com.trakt.tv.launcher.LauncherApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class HomeRow(val title: String, val items: List<MediaItem>)

class HomeViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<HomeRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<HomeRow>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val rows = coroutineScope {
                // Personalized rows (return empty when signed out).
                val continueWatching = async { safe { repo.continueWatching() } }
                val watchlist = async { safe { repo.watchlist() } }
                val upcoming = async { safe { repo.upcoming() } }
                val recShows = async { safe { repo.recommendedShows() } }
                val recMovies = async { safe { repo.recommendedMovies() } }
                // Public discovery.
                val trendingShows = async { safe { repo.trendingShows() } }
                val trendingMovies = async { safe { repo.trendingMovies() } }
                val mostWatched = async { safe { repo.mostWatchedShows() } }
                val popularShows = async { safe { repo.popularShows() } }
                val popularMovies = async { safe { repo.popularMovies() } }
                val boxOffice = async { safe { repo.boxOffice() } }
                val anticipatedShows = async { safe { repo.anticipatedShows() } }
                val anticipatedMovies = async { safe { repo.anticipatedMovies() } }
                listOf(
                    HomeRow("Continue Watching", continueWatching.await()),
                    HomeRow("Your Watchlist", watchlist.await()),
                    HomeRow("Coming Soon", upcoming.await()),
                    HomeRow("Recommended Shows", recShows.await()),
                    HomeRow("Recommended Movies", recMovies.await()),
                    HomeRow("Trending Shows", trendingShows.await()),
                    HomeRow("Trending Movies", trendingMovies.await()),
                    HomeRow("Most Watched This Week", mostWatched.await()),
                    HomeRow("Popular Shows", popularShows.await()),
                    HomeRow("Popular Movies", popularMovies.await()),
                    HomeRow("Box Office", boxOffice.await()),
                    HomeRow("Anticipated Shows", anticipatedShows.await()),
                    HomeRow("Anticipated Movies", anticipatedMovies.await()),
                ).filter { it.items.isNotEmpty() }
            }
            _state.value =
                if (rows.isEmpty()) UiState.Error("Couldn't load anything. Check your connection and API key.")
                else UiState.Success(rows)
        }
    }

    fun addToWatchlist(item: MediaItem, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            onResult(runCatching { repo.addToWatchlist(item) }.getOrDefault(false))
        }
    }

    private inline fun safe(block: () -> List<MediaItem>): List<MediaItem> =
        runCatching(block).getOrDefault(emptyList())

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(appContainer().repository) }
        }
    }
}

@Composable
fun HomeScreen(
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Every launchable app on this device — the home screen's "Apps" row.
    val apps by produceState(initialValue = emptyList<LaunchApp>(), context) {
        value = withContext(Dispatchers.IO) { LauncherApps.installed(context) }
    }

    when (val s = state) {
        is UiState.Loading -> Column(modifier.fillMaxSize()) {
            if (apps.isNotEmpty()) AppsRow(apps = apps, context = context)
            HomeSkeleton()
        }
        is UiState.Error -> Column(modifier.fillMaxSize()) {
            if (apps.isNotEmpty()) AppsRow(apps = apps, context = context)
            MessageView(message = s.message, actionLabel = "Retry", onAction = viewModel::load)
        }
        is UiState.Success -> {
            val rows = s.data
            val featured = rows.firstOrNull { it.title == "Continue Watching" }?.items?.firstOrNull()
                ?: rows.firstOrNull()?.items?.firstOrNull()
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (featured != null) {
                    item {
                        FeaturedHero(
                            item = featured,
                            onDetails = { onOpen(featured) },
                            onWatchlist = {
                                viewModel.addToWatchlist(featured) { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "Added to watchlist" else "Sign in to use your watchlist",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                        )
                    }
                }
                if (apps.isNotEmpty()) item { AppsRow(apps = apps, context = context) }
                items(rows.size) { i ->
                    val row = rows[i]
                    MediaRow(title = row.title, items = row.items, onOpen = onOpen)
                }
            }
        }
    }
}

/** Launcher-style row of every launchable app on the device, plus Settings. */
@Composable
private fun AppsRow(apps: List<LaunchApp>, context: android.content.Context) {
    Column(Modifier.fillMaxWidth().padding(top = 28.dp)) {
        Text(
            text = "Apps",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(start = 48.dp, bottom = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 48.dp),
        ) {
            items(apps, key = { it.packageName }) { app ->
                AppTile(label = app.label, icon = app.icon, onClick = { LauncherApps.launch(context, app) })
            }
            item {
                AppTile(
                    label = "Settings",
                    icon = null,
                    fallback = Icons.Filled.Settings,
                    onClick = { LauncherApps.openSettings(context) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(
    label: String,
    icon: ImageBitmap?,
    fallback: androidx.compose.ui.graphics.vector.ImageVector = Icons.Filled.Apps,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.width(200.dp),
        border = CardDefaults.border(
            focusedBorder = Border(BorderStroke(3.dp, MaterialTheme.colorScheme.primary)),
        ),
        scale = CardDefaults.scale(focusedScale = 1.06f),
    ) {
        Box(
            Modifier.fillMaxWidth().height(96.dp).padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
                } else {
                    Icon(fallback, contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
