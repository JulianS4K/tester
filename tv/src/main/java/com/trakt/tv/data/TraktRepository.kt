package com.trakt.tv.data

import com.trakt.tv.data.model.CastItem
import com.trakt.tv.data.model.Episode
import com.trakt.tv.data.model.Genre
import com.trakt.tv.data.model.Ids
import com.trakt.tv.data.model.ImageUrls
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.MediaType
import com.trakt.tv.data.model.Person
import com.trakt.tv.data.model.Season
import com.trakt.tv.data.model.SyncItems
import com.trakt.tv.data.model.SyncRatings
import com.trakt.tv.data.model.TraktList
import com.trakt.tv.data.model.UserStats
import com.trakt.tv.data.model.WatchedProgress
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

    // ---- Continue watching / progress (OAuth) ----

    suspend fun continueWatching(): List<MediaItem> = io {
        val seen = HashSet<String>()
        api.playback().mapNotNull { pb ->
            val item = pb.show?.let { MediaItem.from(it) } ?: pb.movie?.let { MediaItem.from(it) }
            item?.takeIf { seen.add("${it.type}-${it.traktId}") }
        }
    }

    suspend fun watchedProgress(showId: String): WatchedProgress? = io {
        runCatching { api.watchedProgress(showId) }.getOrNull()
    }

    // ---- Recommendations (OAuth) ----

    suspend fun recommendedMovies(): List<MediaItem> = io { api.recommendedMovies().map { MediaItem.from(it) } }
    suspend fun recommendedShows(): List<MediaItem> = io { api.recommendedShows().map { MediaItem.from(it) } }

    // ---- More discover ----

    suspend fun boxOffice(): List<MediaItem> = io { api.boxOffice().map { MediaItem.from(it.movie) } }
    suspend fun mostWatchedShows(): List<MediaItem> = io { api.mostWatchedShows().map { MediaItem.from(it.show) } }

    suspend fun upcoming(): List<MediaItem> = io {
        val today = java.time.LocalDate.now().toString()
        val seen = HashSet<Long>()
        api.myShows(today, 14).mapNotNull { cs -> MediaItem.from(cs.show).takeIf { seen.add(it.traktId) } }
    }

    // ---- Seasons & episodes ----

    suspend fun seasons(showId: String): List<Season> = io {
        api.seasons(showId).filter { (it.airedEpisodes ?: it.episodeCount ?: 0) > 0 || it.number > 0 }
    }

    suspend fun episodes(showId: String, season: Int): List<Episode> = io { api.seasonEpisodes(showId, season) }

    suspend fun markEpisodeWatched(episodeTrakt: Long): Boolean =
        io { api.addToHistory(SyncItems.episode(Ids(trakt = episodeTrakt))).isSuccessful }

    // ---- People / credits ----

    suspend fun credits(type: MediaType, id: String): List<CastItem> = io {
        val credits = if (type == MediaType.MOVIE) api.moviePeople(id) else api.showPeople(id)
        credits.cast.orEmpty().mapNotNull { c ->
            val p = c.person ?: return@mapNotNull null
            if (p.ids.trakt == 0L) return@mapNotNull null
            CastItem(
                personId = p.ids.trakt,
                name = p.name.orEmpty(),
                character = c.character ?: c.characters?.firstOrNull(),
                headshotUrl = ImageUrls.pick(p.images?.headshot),
            )
        }
    }

    suspend fun person(id: String): Person = io { api.personSummary(id) }
    suspend fun personMovies(id: String): List<MediaItem> = io {
        api.personMovies(id).cast.orEmpty().mapNotNull { it.movie?.let { m -> MediaItem.from(m) } }
    }
    suspend fun personShows(id: String): List<MediaItem> = io {
        api.personShows(id).cast.orEmpty().mapNotNull { it.show?.let { s -> MediaItem.from(s) } }
    }

    // ---- Ratings & collection (OAuth) ----

    suspend fun rate(item: MediaItem, rating: Int): Boolean =
        io { api.addRatings(SyncRatings.of(item.type, item.ids, rating)).isSuccessful }

    suspend fun addToCollection(item: MediaItem): Boolean = io { api.addToCollection(body(item)).isSuccessful }
    suspend fun removeFromCollection(item: MediaItem): Boolean = io { api.removeFromCollection(body(item)).isSuccessful }

    // ---- Browse ----

    suspend fun genres(type: MediaType): List<Genre> = io {
        api.genres(if (type == MediaType.MOVIE) "movies" else "shows").filter { !it.slug.isNullOrBlank() }
    }

    suspend fun byGenre(type: MediaType, genreSlug: String): List<MediaItem> = io {
        if (type == MediaType.MOVIE) api.moviesByGenre(genreSlug).map { MediaItem.from(it) }
        else api.showsByGenre(genreSlug).map { MediaItem.from(it) }
    }

    // ---- Lists ----

    suspend fun trendingLists(): List<TraktList> = io { api.trendingLists().map { it.list } }

    suspend fun listItems(listId: String): List<MediaItem> = io {
        api.listItems(listId).mapNotNull { row ->
            row.movie?.let { MediaItem.from(it) } ?: row.show?.let { MediaItem.from(it) }
        }
    }

    // ---- Stats ----

    suspend fun stats(): UserStats = io { api.userStats() }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(Dispatchers.IO) { block() }
}
