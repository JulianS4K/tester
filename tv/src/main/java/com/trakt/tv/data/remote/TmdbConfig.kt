package com.trakt.tv.data.remote

import com.trakt.tv.BuildConfig

/**
 * TMDB configuration for the (optional) "Available on <service>" feature.
 * TMDB's watch-providers data is powered by JustWatch and requires attribution.
 */
object TmdbConfig {
    const val BASE_URL = "https://api.themoviedb.org/3/"
    const val IMAGE_BASE = "https://image.tmdb.org/t/p/w92"

    val apiKey: String get() = BuildConfig.TMDB_API_KEY
    val region: String get() = BuildConfig.TMDB_REGION.ifBlank { "US" }

    val isConfigured: Boolean get() = apiKey.isNotBlank()

    fun logoUrl(path: String?): String? = path?.takeIf { it.isNotBlank() }?.let { "$IMAGE_BASE$it" }
}
