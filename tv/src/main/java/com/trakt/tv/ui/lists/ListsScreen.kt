@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.lists

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
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
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.TraktList
import com.trakt.tv.ui.UiState
import com.trakt.tv.ui.appContainer
import com.trakt.tv.ui.components.LoadingView
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.ui.components.PosterCard
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------- Lists overview ----------------

class ListsViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<TraktList>>>(UiState.Loading)
    val state: StateFlow<UiState<List<TraktList>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repo.trendingLists() }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load lists.") })
        }
    }

    companion object {
        val Factory = viewModelFactory { initializer { ListsViewModel(appContainer().repository) } }
    }
}

@Composable
fun ListsScreen(
    onOpenList: (id: String, name: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListsViewModel = viewModel(factory = ListsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    when (val s = state) {
        is UiState.Loading -> LoadingView(modifier)
        is UiState.Error -> MessageView(message = s.message, modifier = modifier, actionLabel = "Retry", onAction = viewModel::load)
        is UiState.Success -> LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 40.dp, bottom = 48.dp, start = 48.dp, end = 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text("Trending Lists", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
            }
            items(s.data.size) { i ->
                val list = s.data[i]
                val id = (list.ids.trakt.takeIf { it != 0L }?.toString()) ?: list.ids.slug
                if (id != null) {
                    Button(onClick = { onOpenList(id, list.name ?: "List") }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                            Text(list.name ?: "List", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = listOfNotNull(
                                    list.itemCount?.let { "$it items" },
                                    list.user?.username?.let { "by $it" },
                                ).joinToString("  ·  "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------- Single list ----------------

class ListDetailViewModel(private val repo: TraktRepository, private val id: String) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Loading)
    val state: StateFlow<UiState<List<MediaItem>>> = _state.asStateFlow()

    init { load() }

    fun load() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            _state.value = runCatching { repo.listItems(id) }
                .fold({ UiState.Success(it) }, { UiState.Error("Couldn't load this list.") })
        }
    }

    companion object {
        fun factory(id: String) = viewModelFactory { initializer { ListDetailViewModel(appContainer().repository, id) } }
    }
}

@Composable
fun ListDetailScreen(
    id: String,
    name: String,
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ListDetailViewModel = viewModel(key = "list-$id", factory = ListDetailViewModel.factory(id)),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize()) {
        Text(name, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 48.dp, top = 40.dp, bottom = 12.dp))
        when (val s = state) {
            is UiState.Loading -> LoadingView()
            is UiState.Error -> MessageView(message = s.message, actionLabel = "Retry", onAction = viewModel::load)
            is UiState.Success -> if (s.data.isEmpty()) {
                MessageView(message = "This list is empty.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(150.dp),
                    contentPadding = PaddingValues(48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(s.data, key = { "${it.type}-${it.traktId}" }) { PosterCard(it, onOpen) }
                }
            }
        }
    }
}
