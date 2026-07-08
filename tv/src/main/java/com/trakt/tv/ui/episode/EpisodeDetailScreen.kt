@file:OptIn(ExperimentalTvMaterial3Api::class, ExperimentalLayoutApi::class)

package com.trakt.tv.ui.episode

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil.compose.AsyncImage
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.Episode
import com.trakt.tv.data.model.ImageUrls
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

class EpisodeDetailViewModel(
    private val repo: TraktRepository,
    private val showId: String,
    private val season: Int,
    private val number: Int,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<Episode>>(UiState.Loading)
    val state: StateFlow<UiState<Episode>> = _state.asStateFlow()
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    val signedIn: StateFlow<Boolean> = repo.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repo.episode(showId, season, number) }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load this episode.") })
        }
    }

    fun markWatched() = act("Marked as watched", "Couldn't mark watched") { repo.markEpisodeWatched(it) }
    fun rate(rating: Int) = act("Rated $rating/10", "Couldn't rate") { repo.rateEpisode(it, rating) }

    private fun act(ok: String, fail: String, action: suspend (Long) -> Boolean) {
        val trakt = (state.value as? UiState.Success)?.data?.ids?.trakt ?: return
        if (trakt == 0L) return
        viewModelScope.launch {
            _message.value = if (runCatching { action(trakt) }.getOrDefault(false)) ok else fail
        }
    }

    companion object {
        fun factory(showId: String, season: Int, number: Int) =
            viewModelFactory { initializer { EpisodeDetailViewModel(appContainer().repository, showId, season, number) } }
    }
}

@Composable
fun EpisodeDetailScreen(
    showId: String,
    season: Int,
    number: Int,
    onRequireSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EpisodeDetailViewModel = viewModel(
        key = "ep-$showId-$season-$number",
        factory = EpisodeDetailViewModel.factory(showId, season, number),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> {
            val ep = s.data
            val still = ImageUrls.pick(ep.images?.screenshot)
            Box(modifier.fillMaxSize()) {
                if (still != null) {
                    AsyncImage(
                        model = still,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().height(460.dp),
                    )
                }
                Box(Modifier.fillMaxWidth().height(460.dp).background(Brush.verticalGradient(listOf(Color(0x880B0B0F), Color(0xFF0B0B0F)))))
                LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(top = 64.dp, bottom = 48.dp)) {
                    item {
                        Column(Modifier.padding(horizontal = 48.dp).fillMaxWidth(0.7f)) {
                            Text("S${ep.season ?: season}E${ep.number ?: number}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(ep.title ?: "Episode ${ep.number ?: number}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                            if (!ep.overview.isNullOrBlank()) {
                                Spacer(Modifier.height(14.dp))
                                Text(ep.overview, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { if (signedIn) viewModel.markWatched() else onRequireSignIn() }) {
                                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Mark Watched")
                            }
                            Spacer(Modifier.height(16.dp))
                            Text("Rate", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(8.dp))
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                for (n in 1..10) Button(onClick = { if (signedIn) viewModel.rate(n) else onRequireSignIn() }) { Text(n.toString()) }
                            }
                            if (message != null) {
                                Spacer(Modifier.height(12.dp))
                                Text(message!!, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
