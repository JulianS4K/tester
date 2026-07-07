@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.ui.components.PosterCard
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SearchViewModel(private val repo: TraktRepository) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<MediaItem>>>(UiState.Success(emptyList()))
    val state: StateFlow<UiState<List<MediaItem>>> = _state.asStateFlow()

    private var job: Job? = null

    fun onQueryChange(query: String) {
        job?.cancel()
        val q = query.trim()
        if (q.length < 2) {
            _state.value = UiState.Success(emptyList())
            return
        }
        job = viewModelScope.launch {
            delay(350) // debounce D-pad / keyboard input
            _state.value = UiState.Loading
            _state.value = runCatching { repo.search(q) }
                .fold(
                    onSuccess = { UiState.Success(it) },
                    onFailure = { UiState.Error("Search failed. Please try again.") },
                )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SearchViewModel(appContainer().repository) }
        }
    }
}

@Composable
fun SearchScreen(
    onOpen: (MediaItem) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(top = 32.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = {
                query = it
                viewModel.onQueryChange(it)
            },
            singleLine = true,
            label = { Text("Search movies & shows") },
            modifier = Modifier
                .padding(horizontal = 48.dp)
                .fillMaxWidth(0.6f),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.border,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
            ),
        )

        when (val s = state) {
            is UiState.Loading -> LoadingView()
            is UiState.Error -> MessageView(message = s.message)
            is UiState.Success -> {
                val results = s.data
                if (results.isEmpty()) {
                    MessageView(
                        message = if (query.length < 2) "Type to search Trakt's catalog."
                        else "No results for \"$query\".",
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(150.dp),
                        contentPadding = PaddingValues(48.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(results, key = { "${it.type}-${it.traktId}" }) { item ->
                            PosterCard(item = item, onClick = onOpen)
                        }
                    }
                }
            }
        }
    }
}
