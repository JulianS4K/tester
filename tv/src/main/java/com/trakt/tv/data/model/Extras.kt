package com.trakt.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Additional Trakt API models for the expanded feature set (playback, calendar,
 * seasons/episodes, people, lists, genres, ratings, stats). Kept separate from
 * the core [Movie]/[Show] models in Models.kt.
 */

/** /sync/playback — in-progress movies & episodes with a progress percentage. */
@JsonClass(generateAdapter = true)
data class PlaybackItem(
    val progress: Double = 0.0,
    @Json(name = "paused_at") val pausedAt: String? = null,
    val id: Long? = null,
    val type: String? = null,
    val movie: Movie? = null,
    val show: Show? = null,
    val episode: Episode? = null,
)

/** /calendars/my/shows — upcoming episodes. */
@JsonClass(generateAdapter = true)
data class CalendarShow(
    @Json(name = "first_aired") val firstAired: String? = null,
    val episode: Episode? = null,
    val show: Show,
)

/** /shows/{id}/seasons */
@JsonClass(generateAdapter = true)
data class Season(
    val number: Int = 0,
    val title: String? = null,
    val overview: String? = null,
    val rating: Double? = null,
    @Json(name = "episode_count") val episodeCount: Int? = null,
    @Json(name = "aired_episodes") val airedEpisodes: Int? = null,
    val ids: Ids = Ids(),
    val images: Images? = null,
)

/** /people/{id} and members of a credits cast list. */
@JsonClass(generateAdapter = true)
data class Person(
    val name: String? = null,
    val biography: String? = null,
    val birthday: String? = null,
    val birthplace: String? = null,
    val ids: Ids = Ids(),
    val images: Images? = null,
)

/** A cast entry (for a title's people) or a credit (for a person's filmography). */
@JsonClass(generateAdapter = true)
data class CastMember(
    val character: String? = null,
    val characters: List<String>? = null,
    val person: Person? = null,
    val movie: Movie? = null,
    val show: Show? = null,
)

/** /shows|movies/{id}/people and /people/{id}/movies|shows */
@JsonClass(generateAdapter = true)
data class Credits(
    val cast: List<CastMember>? = null,
)

/** /shows/{id}/progress/watched — how far through a show the user is. */
@JsonClass(generateAdapter = true)
data class WatchedProgress(
    val aired: Int? = null,
    val completed: Int? = null,
    @Json(name = "next_episode") val nextEpisode: Episode? = null,
    @Json(name = "last_episode") val lastEpisode: Episode? = null,
)

/** /users/{id}/lists and list membership. */
@JsonClass(generateAdapter = true)
data class TraktList(
    val name: String? = null,
    val description: String? = null,
    @Json(name = "item_count") val itemCount: Int? = null,
    val likes: Int? = null,
    val ids: Ids = Ids(),
    val user: User? = null,
)

/** /lists/trending and /lists/popular wrap the list object. */
@JsonClass(generateAdapter = true)
data class TrendingList(
    @Json(name = "like_count") val likeCount: Int? = null,
    @Json(name = "comment_count") val commentCount: Int? = null,
    val list: TraktList,
)

/** Items inside a list. */
@JsonClass(generateAdapter = true)
data class ListItemWrapper(
    val type: String? = null,
    val movie: Movie? = null,
    val show: Show? = null,
)

/** /genres/{type} */
@JsonClass(generateAdapter = true)
data class Genre(
    val name: String? = null,
    val slug: String? = null,
)

/** /shows/watched/{period} — most-watched wrapper (counts ignored). */
@JsonClass(generateAdapter = true)
data class WatchedShowItem(val show: Show)

// --- Ratings ---

@JsonClass(generateAdapter = true)
data class RatingItem(val rating: Int, val ids: Ids)

@JsonClass(generateAdapter = true)
data class SyncRatings(
    val movies: List<RatingItem>? = null,
    val shows: List<RatingItem>? = null,
    val episodes: List<RatingItem>? = null,
) {
    companion object {
        fun of(type: MediaType, ids: Ids, rating: Int): SyncRatings =
            if (type == MediaType.MOVIE) SyncRatings(movies = listOf(RatingItem(rating, ids)))
            else SyncRatings(shows = listOf(RatingItem(rating, ids)))
    }
}

// --- User stats (subset) ---

@JsonClass(generateAdapter = true)
data class UserStats(
    val movies: MoviesStats? = null,
    val shows: ShowsStats? = null,
    val seasons: SeasonsStats? = null,
    val episodes: EpisodesStats? = null,
)

@JsonClass(generateAdapter = true)
data class MoviesStats(
    val plays: Int? = null,
    val watched: Int? = null,
    val collected: Int? = null,
    val ratings: Int? = null,
    val minutes: Long? = null,
)

@JsonClass(generateAdapter = true)
data class ShowsStats(
    val watched: Int? = null,
    val collected: Int? = null,
    val ratings: Int? = null,
)

@JsonClass(generateAdapter = true)
data class SeasonsStats(
    val ratings: Int? = null,
)

@JsonClass(generateAdapter = true)
data class EpisodesStats(
    val plays: Int? = null,
    val watched: Int? = null,
    val minutes: Long? = null,
    val ratings: Int? = null,
)
