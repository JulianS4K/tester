@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MediaRow
import com.trakt.tv.ui.components.MessageView
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
                val trendingShows = async { safe { repo.trendingShows() } }
                val trendingMovies = async { safe { repo.trendingMovies() } }
                val popularShows = async { safe { repo.popularShows() } }
                val popularMovies = async { safe { repo.popularMovies() } }
                val anticipated = async { safe { repo.anticipatedMovies() } }
                listOf(
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
    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(
            message = s.message,
            modifier = modifier,
            actionLabel = "Retry",
            onAction = viewModel::load,
        )
        is UiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            items(s.data.size) { i ->
                val row = s.data[i]
                MediaRow(title = row.title, items = row.items, onOpen = onOpen)
            }
        }
    }
}
