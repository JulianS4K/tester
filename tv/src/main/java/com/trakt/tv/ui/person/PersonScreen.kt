@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.person

import androidx.compose.foundation.layout.Arrangement
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
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
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

data class PersonUi(val name: String, val bio: String?, val movies: List<MediaItem>, val shows: List<MediaItem>)

class PersonViewModel(private val repo: TraktRepository, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<PersonUi>>(UiState.Loading)
    val state: StateFlow<UiState<PersonUi>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching {
                coroutineScope {
                    val person = async { repo.person(id) }
                    val movies = async { runCatching { repo.personMovies(id) }.getOrDefault(emptyList()) }
                    val shows = async { runCatching { repo.personShows(id) }.getOrDefault(emptyList()) }
                    val p = person.await()
                    PersonUi(p.name.orEmpty(), p.biography, movies.await(), shows.await())
                }
            }.fold({ UiState.Success(it) }, { UiState.Error("Couldn't load this person.") })
        }
    }

    companion object {
        fun factory(id: String) = viewModelFactory { initializer { PersonViewModel(appContainer().repository, id) } }
    }
}

@Composable
fun PersonScreen(
    id: String,
    name: String,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PersonViewModel = viewModel(key = "person-$id", factory = PersonViewModel.factory(id)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 48.dp, bottom = 48.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            item {
                Text(
                    text = s.data.name.ifBlank { name },
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 48.dp),
                )
            }
            val bio = s.data.bio
            if (!bio.isNullOrBlank()) {
                item {
                    Text(
                        text = bio,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 4,
                        modifier = Modifier.padding(horizontal = 48.dp).fillMaxWidth(0.7f),
                    )
                }
            }
            if (s.data.movies.isNotEmpty()) item { MediaRow("Movies", s.data.movies, onOpen) }
            if (s.data.shows.isNotEmpty()) item { MediaRow("Shows", s.data.shows, onOpen) }
        }
    }
}
