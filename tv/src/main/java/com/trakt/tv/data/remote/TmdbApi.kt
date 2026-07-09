package com.trakt.tv.data.remote

import com.trakt.tv.data.model.TmdbWatchResponse
import retrofit2.http.GET
import retrofit2.http.Path

/**
 * Minimal TMDB API — just the watch-providers endpoints. The v3 `api_key` query
 * param is attached by [TmdbKeyInterceptor]. Base URL is https://api.themoviedb.org/3/.
 */
interface TmdbApi {

    @GET("movie/{id}/watch/providers")
    suspend fun movieWatchProviders(@Path("id") tmdbId: Long): TmdbWatchResponse

    @GET("tv/{id}/watch/providers")
    suspend fun showWatchProviders(@Path("id") tmdbId: Long): TmdbWatchResponse
}
