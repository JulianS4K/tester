package com.trakt.tv.data.model

/** The two content kinds this client browses. */
enum class MediaType(val trakt: String) {
    MOVIE("movies"),
    SHOW("shows");

    companion object {
        fun fromTrakt(value: String?): MediaType =
            if (value == "movie" || value == "movies") MOVIE else SHOW
    }
}

/**
 * Unified, UI-facing representation of a movie or show so rows/cards/detail can
 * render either kind without branching. Built from the raw [Movie]/[Show] models.
 */
data class MediaItem(
    val type: MediaType,
    val traktId: Long,
    val title: String,
    val year: Int?,
    val overview: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val rating: Double?,
    val runtime: Int?,
    val genres: List<String>,
    val network: String?,
    val certification: String?,
    val status: String?,
    val ids: Ids,
) {
    val subtitle: String
        get() = buildString {
            year?.let { append(it) }
            if (network != null) {
                if (isNotEmpty()) append("  ·  ")
                append(network)
            }
        }

    companion object {
        fun from(movie: Movie): MediaItem = MediaItem(
            type = MediaType.MOVIE,
            traktId = movie.ids.trakt,
            title = movie.title.orEmpty(),
            year = movie.year,
            overview = movie.overview,
            posterUrl = ImageUrls.pick(movie.images?.poster),
            backdropUrl = ImageUrls.pick(movie.images?.fanart),
            logoUrl = ImageUrls.pick(movie.images?.logo),
            rating = movie.rating,
            runtime = movie.runtime,
            genres = movie.genres.orEmpty(),
            network = null,
            certification = movie.certification,
            status = movie.status,
            ids = movie.ids,
        )

        fun from(show: Show): MediaItem = MediaItem(
            type = MediaType.SHOW,
            traktId = show.ids.trakt,
            title = show.title.orEmpty(),
            year = show.year,
            overview = show.overview,
            posterUrl = ImageUrls.pick(show.images?.poster),
            backdropUrl = ImageUrls.pick(show.images?.fanart),
            logoUrl = ImageUrls.pick(show.images?.logo),
            rating = show.rating,
            runtime = show.runtime,
            genres = show.genres.orEmpty(),
            network = show.network,
            certification = show.certification,
            status = show.status,
            ids = show.ids,
        )
    }
}

/** A cast/crew member for the detail page's people row. */
data class CastItem(
    val personId: Long,
    val name: String,
    val character: String?,
    val headshotUrl: String?,
)

/** Image URL helpers — Trakt returns protocol-less WebP URLs; prepend https://. */
object ImageUrls {
    fun pick(urls: List<String>?): String? =
        urls?.firstOrNull()?.let { if (it.startsWith("http")) it else "https://$it" }
}
