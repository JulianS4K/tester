package com.trakt.tv.data

import com.trakt.tv.data.model.Ids
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.MediaType
import com.trakt.tv.data.model.SyncItems
import com.trakt.tv.data.remote.Network
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Single entry point for content and sync. All calls are main-safe (dispatched
 * to IO). UI/ViewModels depend only on this + [MediaItem].
 */
class TraktRepository(private val network: Network) {

    val tokenStore: TokenStore get() = network.tokenStore
    val isSignedIn: Flow<Boolean> get() = network.tokenStore.isSignedIn
    val username: Flow<String?> get() = network.tokenStore.username

    private val api get() = network.api

    // ---- Discover ----

    suspend fun trendingMovies(): List<MediaItem> = io {
        api.trendingMovies().map { MediaItem.from(it.movie) }
    }

    suspend fun popularMovies(): List<MediaItem> = io {
        api.popularMovies().map { MediaItem.from(it) }
    }

    suspend fun anticipatedMovies(): List<MediaItem> = io {
        api.anticipatedMovies().map { MediaItem.from(it.movie) }
    }

    suspend fun trendingShows(): List<MediaItem> = io {
        api.trendingShows().map { MediaItem.from(it.show) }
    }

    suspend fun popularShows(): List<MediaItem> = io {
        api.popularShows().map { MediaItem.from(it) }
    }

    suspend fun anticipatedShows(): List<MediaItem> = io {
        api.anticipatedShows().map { MediaItem.from(it.show) }
    }

    // ---- Detail ----

    suspend fun detail(type: MediaType, id: String): MediaItem = io {
        when (type) {
            MediaType.MOVIE -> MediaItem.from(api.movieSummary(id))
            MediaType.SHOW -> MediaItem.from(api.showSummary(id))
        }
    }

    suspend fun related(type: MediaType, id: String): List<MediaItem> = io {
        when (type) {
            MediaType.MOVIE -> api.relatedMovies(id).map { MediaItem.from(it) }
            MediaType.SHOW -> api.relatedShows(id).map { MediaItem.from(it) }
        }
    }

    // ---- Search ----

    suspend fun search(query: String): List<MediaItem> = io {
        if (query.isBlank()) return@io emptyList()
        api.search("movie,show", query).mapNotNull { r ->
            when {
                r.movie != null -> MediaItem.from(r.movie)
                r.show != null -> MediaItem.from(r.show)
                else -> null
            }
        }
    }

    // ---- Library (OAuth) ----

    suspend fun watchlist(): List<MediaItem> = io {
        api.watchlist().mapNotNull { item ->
            item.movie?.let { MediaItem.from(it) } ?: item.show?.let { MediaItem.from(it) }
        }
    }

    suspend fun history(): List<MediaItem> = io {
        api.history().mapNotNull { item ->
            item.movie?.let { MediaItem.from(it) } ?: item.show?.let { MediaItem.from(it) }
        }
    }

    // ---- Actions (OAuth) ----

    suspend fun addToWatchlist(item: MediaItem): Boolean =
        io { api.addToWatchlist(body(item)).isSuccessful }

    suspend fun removeFromWatchlist(item: MediaItem): Boolean =
        io { api.removeFromWatchlist(body(item)).isSuccessful }

    suspend fun markWatched(item: MediaItem): Boolean =
        io { api.addToHistory(body(item)).isSuccessful }

    suspend fun removeFromHistory(item: MediaItem): Boolean =
        io { api.removeFromHistory(body(item)).isSuccessful }

    private fun body(item: MediaItem): SyncItems {
        val ids = Ids(trakt = item.ids.trakt, slug = item.ids.slug, imdb = item.ids.imdb, tmdb = item.ids.tmdb, tvdb = item.ids.tvdb)
        return if (item.type == MediaType.MOVIE) SyncItems.movie(ids) else SyncItems.show(ids)
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
