@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.stats

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.tv.material3.Card
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.UserStats
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MessageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StatsViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<UserStats>>(UiState.Loading)
    val state: StateFlow<UiState<UserStats>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repo.stats() }
                .fold({ UiState.Success(it) }, { UiState.Error("Sign in to see your stats.") })
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { StatsViewModel(appContainer().repository) } }
    }
}

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(factory = StatsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> {
            val st = s.data
            val minutes = (st.movies?.minutes ?: 0) + (st.episodes?.minutes ?: 0)
            val tiles = listOfNotNull(
                st.movies?.watched?.let { "Movies watched" to it.toString() },
                st.shows?.watched?.let { "Shows watched" to it.toString() },
                st.episodes?.watched?.let { "Episodes watched" to it.toString() },
                st.episodes?.plays?.let { "Episode plays" to it.toString() },
                st.movies?.collected?.let { "Movies collected" to it.toString() },
                st.shows?.collected?.let { "Shows collected" to it.toString() },
                ("Time watched" to "${minutes / 60} h"),
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(240.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(48.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Text("Your Stats", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                }
                items(tiles.size) { i -> StatTile(tiles[i].first, tiles[i].second) }
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String) {
    Card(onClick = {}, modifier = Modifier.width(240.dp)) {
        Column(Modifier.padding(20.dp)) {
            Text(value, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
