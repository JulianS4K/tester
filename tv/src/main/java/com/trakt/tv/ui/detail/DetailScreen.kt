@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.detail

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Done
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.MediaType
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MediaRow
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.ui.components.RatingBadge
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class DetailUi(val item: MediaItem, val related: List<MediaItem>)

class DetailViewModel(
    private val repo: TraktRepository,
    private val type: MediaType,
    private val id: String,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<DetailUi>>(UiState.Loading)
    val state: StateFlow<UiState<DetailUi>> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    val signedIn: StateFlow<Boolean> =
        repo.isSignedIn.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                coroutineScope {
                    val detail = async { repo.detail(type, id) }
                    val related = async { runCatching { repo.related(type, id) }.getOrDefault(emptyList()) }
                    DetailUi(detail.await(), related.await())
                }
            }.fold(
                onSuccess = { UiState.Success(it) },
                onFailure = { UiState.Error("Couldn't load this title.") },
            )
        }
    }

    fun addToWatchlist() = act("Added to watchlist", "Couldn't add to watchlist") { repo.addToWatchlist(it) }
    fun markWatched() = act("Marked as watched", "Couldn't mark as watched") { repo.markWatched(it) }

    private fun act(ok: String, fail: String, action: suspend (MediaItem) -> Boolean) {
        val item = (state.value as? UiState.Success)?.data?.item ?: return
        viewModelScope.launch {
            val success = runCatching { action(item) }.getOrDefault(false)
            _message.value = if (success) ok else fail
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(type: MediaType, id: String) = viewModelFactory {
            initializer { DetailViewModel(appContainer().repository, type, id) }
        }
    }
}

@Composable
fun DetailScreen(
    type: MediaType,
    id: String,
    onOpen: (MediaItem) -> Unit,
    onRequireSignIn: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: DetailViewModel = viewModel(
        key = "detail-$type-$id",
        factory = DetailViewModel.factory(type, id),
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val signedIn by viewModel.signedIn.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()

    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> DetailContent(
            ui = s.data,
            signedIn = signedIn,
            actionMessage = message,
            onAddWatchlist = { if (signedIn) viewModel.addToWatchlist() else onRequireSignIn() },
            onMarkWatched = { if (signedIn) viewModel.markWatched() else onRequireSignIn() },
            onOpen = onOpen,
            modifier = modifier,
        )
    }
}

@Composable
private fun DetailContent(
    ui: DetailUi,
    signedIn: Boolean,
    actionMessage: String?,
    onAddWatchlist: () -> Unit,
    onMarkWatched: () -> Unit,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val item = ui.item
    Box(modifier.fillMaxSize()) {
        if (item.backdropUrl != null) {
            AsyncImage(
                model = item.backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(520.dp),
            )
        }
        // Scrims for text legibility over the backdrop.
        Box(
            Modifier.fillMaxWidth().height(520.dp).background(
                Brush.verticalGradient(listOf(Color(0xCC0B0B0F), Color(0xFF0B0B0F))),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(Color(0xFF0B0B0F), Color(0x000B0B0F))),
            ),
        )

        LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 48.dp)) {
            item {
                Column(Modifier.padding(start = 48.dp, top = 64.dp, end = 48.dp).fillMaxWidth(0.62f)) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.displaySmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(12.dp))
                    MetaRow(item)
                    if (!item.overview.isNullOrBlank()) {
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = item.overview,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 5,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Button(onClick = onAddWatchlist) {
                            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Watchlist")
                        }
                        Button(onClick = onMarkWatched) {
                            Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Mark Watched")
                        }
                    }
                    if (actionMessage != null) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Done, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Text(actionMessage, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (!signedIn) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Sign in to save to your Trakt watchlist & history.",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (ui.related.isNotEmpty()) {
                item { Spacer(Modifier.height(40.dp)) }
                item {
                    MediaRow(title = "More like this", items = ui.related, onOpen = onOpen)
                }
            }
        }
    }
}

@Composable
private fun MetaRow(item: MediaItem) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        RatingBadge(item.rating)
        val bits = buildList {
            item.year?.let { add(it.toString()) }
            item.runtime?.takeIf { it > 0 }?.let { add("${it}m") }
            item.certification?.takeIf { it.isNotBlank() }?.let { add(it) }
            item.network?.let { add(it) }
            if (item.genres.isNotEmpty()) add(item.genres.take(3).joinToString(", "))
        }
        if (bits.isNotEmpty()) {
            Text(
                text = bits.joinToString("   ·   "),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
