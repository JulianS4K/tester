package com.trakt.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Data models for the parts of the Trakt API v2 this TV client uses.
 *
 * Only the fields the app reads are declared; Moshi ignores the rest of each
 * JSON object. Shapes follow the "Standard Media Objects" and endpoint
 * examples in the Trakt API blueprint.
 */

@JsonClass(generateAdapter = true)
data class Ids(
    val trakt: Long = 0,
    val slug: String? = null,
    val imdb: String? = null,
    val tmdb: Long? = null,
    val tvdb: Long? = null,
)

/** ?extended=images returns arrays of (protocol-less) WebP URLs per image type. */
@JsonClass(generateAdapter = true)
data class Images(
    val fanart: List<String>? = null,
    val poster: List<String>? = null,
    val logo: List<String>? = null,
    val clearart: List<String>? = null,
    val banner: List<String>? = null,
    val thumb: List<String>? = null,
    val screenshot: List<String>? = null,
    val headshot: List<String>? = null,
)

@JsonClass(generateAdapter = true)
data class Movie(
    val title: String? = null,
    val year: Int? = null,
    val ids: Ids = Ids(),
    val images: Images? = null,
    // ?extended=full
    val overview: String? = null,
    val tagline: String? = null,
    val released: String? = null,
    val runtime: Int? = null,
    val certification: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val genres: List<String>? = null,
    val trailer: String? = null,
    val status: String? = null,
)

@JsonClass(generateAdapter = true)
data class Show(
    val title: String? = null,
    val year: Int? = null,
    val ids: Ids = Ids(),
    val images: Images? = null,
    // ?extended=full
    val overview: String? = null,
    @Json(name = "first_aired") val firstAired: String? = null,
    val runtime: Int? = null,
    val network: String? = null,
    val certification: String? = null,
    val country: String? = null,
    val rating: Double? = null,
    val votes: Int? = null,
    val genres: List<String>? = null,
    val status: String? = null,
    @Json(name = "aired_episodes") val airedEpisodes: Int? = null,
    val trailer: String? = null,
)

@JsonClass(generateAdapter = true)
data class Episode(
    val season: Int? = null,
    val number: Int? = null,
    val title: String? = null,
    val ids: Ids = Ids(),
    val images: Images? = null,
    val overview: String? = null,
    val rating: Double? = null,
    @Json(name = "first_aired") val firstAired: String? = null,
)

/** /shows/trending — { watchers, show }. */
@JsonClass(generateAdapter = true)
data class TrendingShow(
    val watchers: Int = 0,
    val show: Show,
)

/** /movies/trending — { watchers, movie }. */
@JsonClass(generateAdapter = true)
data class TrendingMovie(
    val watchers: Int = 0,
    val movie: Movie,
)

/** /search/{type} results. */
@JsonClass(generateAdapter = true)
data class SearchResult(
    val type: String? = null,
    val score: Double? = null,
    val movie: Movie? = null,
    val show: Show? = null,
)

/** /sync/watchlist — { rank, listed_at, type, movie|show }. */
@JsonClass(generateAdapter = true)
data class WatchlistItem(
    val rank: Int? = null,
    @Json(name = "listed_at") val listedAt: String? = null,
    val type: String? = null,
    val movie: Movie? = null,
    val show: Show? = null,
)

/** /sync/history — { id, watched_at, action, type, movie|show|episode }. */
@JsonClass(generateAdapter = true)
data class HistoryItem(
    val id: Long? = null,
    @Json(name = "watched_at") val watchedAt: String? = null,
    val action: String? = null,
    val type: String? = null,
    val movie: Movie? = null,
    val show: Show? = null,
    val episode: Episode? = null,
)

// ---- Auth (device flow) ----

@JsonClass(generateAdapter = true)
data class DeviceCodeRequest(
    @Json(name = "client_id") val clientId: String,
)

@JsonClass(generateAdapter = true)
data class DeviceCode(
    @Json(name = "device_code") val deviceCode: String,
    @Json(name = "user_code") val userCode: String,
    @Json(name = "verification_url") val verificationUrl: String,
    @Json(name = "expires_in") val expiresIn: Int,
    val interval: Int,
)

@JsonClass(generateAdapter = true)
data class DeviceTokenRequest(
    val code: String,
    @Json(name = "client_id") val clientId: String,
    @Json(name = "client_secret") val clientSecret: String,
)

@JsonClass(generateAdapter = true)
data class RefreshTokenRequest(
    @Json(name = "refresh_token") val refreshToken: String,
    @Json(name = "client_id") val clientId: String,
    @Json(name = "client_secret") val clientSecret: String,
    @Json(name = "redirect_uri") val redirectUri: String = "urn:ietf:wg:oauth:2.0:oob",
    @Json(name = "grant_type") val grantType: String = "refresh_token",
)

@JsonClass(generateAdapter = true)
data class AccessToken(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "refresh_token") val refreshToken: String? = null,
    @Json(name = "token_type") val tokenType: String? = null,
    @Json(name = "expires_in") val expiresIn: Long = 0,
    @Json(name = "created_at") val createdAt: Long = 0,
)

// ---- Sync request bodies ----

@JsonClass(generateAdapter = true)
data class MovieRef(val ids: Ids)

@JsonClass(generateAdapter = true)
data class ShowRef(val ids: Ids)

@JsonClass(generateAdapter = true)
data class EpisodeRef(val ids: Ids)

/** Shared body for /sync/history, /sync/watchlist, /sync/collection. */
@JsonClass(generateAdapter = true)
data class SyncItems(
    val movies: List<MovieRef>? = null,
    val shows: List<ShowRef>? = null,
    val episodes: List<EpisodeRef>? = null,
) {
    companion object {
        fun movie(ids: Ids) = SyncItems(movies = listOf(MovieRef(ids)))
        fun show(ids: Ids) = SyncItems(shows = listOf(ShowRef(ids)))
        fun episode(ids: Ids) = SyncItems(episodes = listOf(EpisodeRef(ids)))
    }
}

/** Minimal user settings response (used to show who is signed in). */
@JsonClass(generateAdapter = true)
data class UserSettings(val user: User? = null)

@JsonClass(generateAdapter = true)
data class User(
    val username: String? = null,
    val name: String? = null,
    val ids: Ids? = null,
)
