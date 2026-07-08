@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.season

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.trakt.tv.data.model.Episode
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MessageView
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SeasonViewModel(
    private val repo: TraktRepository,
    private val showId: String,
    private val season: Int,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<Episode>>>(UiState.Loading)
    val state: StateFlow<UiState<List<Episode>>> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val signedIn: StateFlow<Boolean> = repo.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repo.episodes(showId, season) }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load episodes.") })
        }
    }

    fun markWatched(ep: Episode) {
        val trakt = ep.ids.trakt
        if (trakt == 0L) return
        viewModelScope.launch {
            val ok = runCatching { repo.markEpisodeWatched(trakt) }.getOrDefault(false)
            _message.value = if (ok) "Marked S${ep.season}E${ep.number} watched" else "Couldn't mark watched"
        }
    }

    companion object {
        fun factory(showId: String, season: Int) =
            viewModelFactory { initializer { SeasonViewModel(appContainer().repository, showId, season) } }
    }
}

@Composable
fun SeasonScreen(
    showId: String,
    showTitle: String,
    season: Int,
    onOpenEpisode: (Int) -> Unit,
    onRequireSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeasonViewModel = viewModel(key = "season-$showId-$season", factory = SeasonViewModel.factory(showId, season)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp, start = 48.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = "$showTitle — ${if (season == 0) "Specials" else "Season $season"}",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
            if (message != null) {
                item { Text(message!!, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary) }
            }
            items(s.data.size) { i ->
                EpisodeRow(
                    ep = s.data[i],
                    onOpen = { onOpenEpisode(s.data[i].number ?: 0) },
                    onMarkWatched = { if (signedIn) viewModel.markWatched(s.data[i]) else onRequireSignIn() },
                )
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, onOpen: () -> Unit, onMarkWatched: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.fillMaxWidth(0.68f)) {
            Text(
                text = "${ep.number ?: 0}. ${ep.title ?: "Episode ${ep.number ?: 0}"}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (!ep.overview.isNullOrBlank()) {
                Text(
                    text = ep.overview,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onOpen) { Text("Details") }
        Spacer(Modifier.width(10.dp))
        Button(onClick = onMarkWatched) { Text("✓ Watched") }
    }
}
