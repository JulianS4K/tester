@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trakt.tv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.Genre
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.MediaType
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.MediaRow
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class BrowseViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _showGenres = MutableStateFlow<List<Genre>>(emptyList())
    val showGenres: StateFlow<List<Genre>> = _showGenres.asStateFlow()
    private val _movieGenres = MutableStateFlow<List<Genre>>(emptyList())
    val movieGenres: StateFlow<List<Genre>> = _movieGenres.asStateFlow()

    private val _results = MutableStateFlow<UiState<List<MediaItem>>?>(null)
    val results: StateFlow<UiState<List<MediaItem>>?> = _results.asStateFlow()
    private val _selected = MutableStateFlow<String?>(null)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    init {
        viewModelScope.launch {
            coroutineScope {
                val shows = async { runCatching { repo.genres(MediaType.SHOW) }.getOrDefault(emptyList()) }
                val movies = async { runCatching { repo.genres(MediaType.MOVIE) }.getOrDefault(emptyList()) }
                _showGenres.value = shows.await()
                _movieGenres.value = movies.await()
            }
        }
    }

    fun select(type: MediaType, genre: Genre) {
        val slug = genre.slug ?: return
        _selected.value = "${genre.name ?: slug} · ${if (type == MediaType.SHOW) "Shows" else "Movies"}"
        _results.value = UiState.Loading
        viewModelScope.launch {
            _results.value = runCatching { repo.byGenre(type, slug) }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load titles.") })
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { BrowseViewModel(appContainer().repository) } }
    }
}

@Composable
fun BrowseScreen(
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BrowseViewModel = viewModel(factory = BrowseViewModel.Factory),
) {
    val showGenres by viewModel.showGenres.collectAsStateWithLifecycle()
    val movieGenres by viewModel.movieGenres.collectAsStateWithLifecycle()
    val results by viewModel.results.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = 40.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        item {
            Text("Browse", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 48.dp))
        }
        item { GenreBlock("Shows", showGenres) { viewModel.select(MediaType.SHOW, it) } }
        item { GenreBlock("Movies", movieGenres) { viewModel.select(MediaType.MOVIE, it) } }

        when (val r = results) {
            is UiState.Success -> item { MediaRow(selected ?: "Results", r.data, onOpen) }
            is UiState.Loading -> item { Text("Loading…", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 48.dp)) }
            is UiState.Error -> item { Text(r.message, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 48.dp)) }
            null -> Unit
        }
    }
}

@Composable
private fun GenreBlock(label: String, genres: List<Genre>, onPick: (Genre) -> Unit) {
    if (genres.isEmpty()) return
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 48.dp, bottom = 8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 48.dp),
        ) {
            genres.forEach { g ->
                Button(onClick = { onPick(g) }) { Text(g.name ?: g.slug ?: "") }
            }
        }
    }
}
