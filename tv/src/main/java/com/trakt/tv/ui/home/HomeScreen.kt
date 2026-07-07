@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.home

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MediaRow
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.watch.WatchLauncher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeRow(val title: String, val items: List<MediaItem>)

class HomeViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<HomeRow>>>(UiState.Loading)
    val state: StateFlow<UiState<List<HomeRow>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            val rows = coroutineScope {
                // "Your Watchlist" is personalized; it returns empty when signed out.
                val watchlist = async { safe { repo.watchlist() } }
                val trendingShows = async { safe { repo.trendingShows() } }
                val trendingMovies = async { safe { repo.trendingMovies() } }
                val popularShows = async { safe { repo.popularShows() } }
                val popularMovies = async { safe { repo.popularMovies() } }
                val anticipated = async { safe { repo.anticipatedMovies() } }
                listOf(
                    HomeRow("Your Watchlist", watchlist.await()),
                    HomeRow("Trending Shows", trendingShows.await()),
                    HomeRow("Trending Movies", trendingMovies.await()),
                    HomeRow("Popular Shows", popularShows.await()),
                    HomeRow("Popular Movies", popularMovies.await()),
                    HomeRow("Anticipated Movies", anticipated.await()),
                ).filter { it.items.isNotEmpty() }
            }
            _state.value =
                if (rows.isEmpty()) UiState.Error("Couldn't load anything. Check your connection and API key.")
                else UiState.Success(rows)
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
    // Streaming apps installed on this device — the launcher's "Apps" row.
    val apps = remember { WatchLauncher.installedProviders(context) }

    Column(modifier.fillMaxSize()) {
        if (apps.isNotEmpty()) {
            AppsRow(apps = apps, context = context)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (val s = state) {
                is UiState.Loading -> LoadingView()
                is UiState.Error -> MessageView(
                    message = s.message,
                    actionLabel = "Retry",
                    onAction = viewModel::load,
                )
                is UiState.Success -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 20.dp, bottom = 48.dp),
                    verticalArrangement = Arrangement.spacedBy(28.dp),
                ) {
                    items(s.data.size) { i ->
                        val row = s.data[i]
                        MediaRow(title = row.title, items = row.items, onOpen = onOpen)
                    }
                }
            }
        }
    }
}

/** Launcher-style row of the streaming apps installed on this device. */
@Composable
private fun AppsRow(apps: List<WatchLauncher.Installed>, context: android.content.Context) {
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
            items(apps, key = { it.provider.id }) { entry ->
                AppTile(
                    label = entry.provider.name,
                    onClick = { WatchLauncher.launchApp(context, entry.packageName) },
                )
            }
        }
    }
}

@Composable
private fun AppTile(label: String, onClick: () -> Unit) {
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
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
