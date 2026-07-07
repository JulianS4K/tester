@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.ui.components.PosterCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _watchlist = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
    val watchlist: StateFlow<UiState<List<MediaItem>>> = _watchlist.asStateFlow()

    private val _history = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
    val history: StateFlow<UiState<List<MediaItem>>> = _history.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _watchlist.value = UiState.Loading
            _watchlist.value = runCatching { repo.watchlist() }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load your watchlist.") })
        }
        viewModelScope.launch {
            _history.value = UiState.Loading
            _history.value = runCatching { repo.history() }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load your history.") })
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { LibraryViewModel(appContainer().repository) }
        }
    }
}

@Composable
fun LibraryScreen(
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LibraryViewModel = viewModel(factory = LibraryViewModel.Factory),
) {
    val watchlist by viewModel.watchlist.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Watchlist", "History")

    Column(modifier = modifier.fillMaxSize().padding(top = 32.dp)) {
        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier.padding(start = 48.dp),
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onFocus = { selectedTab = index },
                    onClick = { selectedTab = index },
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
            }
        }

        val state = if (selectedTab == 0) watchlist else history
        val emptyMsg = if (selectedTab == 0) "Your watchlist is empty." else "Nothing watched yet."
        Grid(state = state, emptyMessage = emptyMsg, onOpen = onOpen, onRetry = viewModel::refresh)
    }
}

@Composable
private fun Grid(
    state: UiState<List<MediaItem>>,
    emptyMessage: String,
    onOpen: (MediaItem) -> Unit,
    onRetry: () -> Unit,
) {
    when (state) {
        is UiState.Loading -> LoadingView()
        is UiState.Error -> MessageView(message = state.message, actionLabel = "Retry", onAction = onRetry)
        is UiState.Success -> if (state.data.isEmpty()) {
            MessageView(message = emptyMessage)
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp),
                contentPadding = PaddingValues(48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(state.data, key = { "${it.type}-${it.traktId}" }) { item ->
                    PosterCard(item = item, onClick = onOpen)
                }
            }
        }
    }
}
