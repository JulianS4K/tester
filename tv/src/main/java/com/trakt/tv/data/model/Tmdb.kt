package com.trakt.tv.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * TMDB /watch/providers response (JustWatch-powered). `results` is keyed by
 * ISO country code; each holds the streaming (`flatrate`), rent, and buy options
 * plus a `link` to the TMDB/JustWatch watch page for the title.
 */
@JsonClass(generateAdapter = true)
data class TmdbWatchResponse(
    val results: Map<String, TmdbCountryProviders>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbCountryProviders(
    val link: String? = null,
    val flatrate: List<TmdbProvider>? = null,
    val rent: List<TmdbProvider>? = null,
    val buy: List<TmdbProvider>? = null,
)

@JsonClass(generateAdapter = true)
data class TmdbProvider(
    @Json(name = "provider_id") val providerId: Int = 0,
    @Json(name = "provider_name") val providerName: String? = null,
    @Json(name = "logo_path") val logoPath: String? = null,
    @Json(name = "display_priority") val displayPriority: Int? = null,
)

/** UI-facing streaming availability for a title in one region. */
data class WatchAvailability(
    val flatrate: List<WatchProvider>,
    val rent: List<WatchProvider>,
    val buy: List<WatchProvider>,
    val tmdbLink: String?,
) {
    val isEmpty: Boolean get() = flatrate.isEmpty() && rent.isEmpty() && buy.isEmpty()
}

data class WatchProvider(
    val name: String,
    val logoUrl: String?,
)
