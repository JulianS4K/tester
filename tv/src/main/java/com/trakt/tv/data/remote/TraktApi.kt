package com.trakt.tv.data.remote

import com.trakt.tv.data.model.AccessToken
import com.trakt.tv.data.model.DeviceCode
import com.trakt.tv.data.model.DeviceCodeRequest
import com.trakt.tv.data.model.DeviceTokenRequest
import com.trakt.tv.data.model.HistoryItem
import com.trakt.tv.data.model.Movie
import com.trakt.tv.data.model.RefreshTokenRequest
import com.trakt.tv.data.model.SearchResult
import com.trakt.tv.data.model.Show
import com.trakt.tv.data.model.SyncItems
import com.trakt.tv.data.model.TrendingMovie
import com.trakt.tv.data.model.TrendingShow
import com.trakt.tv.data.model.UserSettings
import com.trakt.tv.data.model.WatchlistItem
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Trakt API v2. Required headers (trakt-api-version / trakt-api-key /
 * Content-Type) and the Bearer token are attached by [AuthHeaderInterceptor];
 * the [Header] "Authorization: optional" marker below is stripped by that
 * interceptor and just signals which endpoints personalise results when signed
 * in. Base URL is https://api.trakt.tv/.
 */
interface TraktApi {

    // ---- Authentication (device flow) ----

    @POST("oauth/device/code")
    suspend fun deviceCode(@Body body: DeviceCodeRequest): DeviceCode

    /** Poll for the token. 400 = pending, 429 = slow down, 410 = expired, etc. */
    @POST("oauth/device/token")
    suspend fun deviceToken(@Body body: DeviceTokenRequest): Response<AccessToken>

    @POST("oauth/token")
    suspend fun refreshToken(@Body body: RefreshTokenRequest): Response<AccessToken>

    // ---- Discover ----

    @GET("movies/trending")
    suspend fun trendingMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<TrendingMovie>

    @GET("movies/popular")
    suspend fun popularMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<Movie>

    @GET("movies/anticipated")
    suspend fun anticipatedMovies(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<TrendingMovie>

    @GET("shows/trending")
    suspend fun trendingShows(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<TrendingShow>

    @GET("shows/popular")
    suspend fun popularShows(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<Show>

    @GET("shows/anticipated")
    suspend fun anticipatedShows(
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 24,
        @Query("extended") extended: String = "full,images",
    ): List<TrendingShow>

    // ---- Detail ----

    @GET("movies/{id}")
    suspend fun movieSummary(
        @Path("id") id: String,
        @Query("extended") extended: String = "full,images",
    ): Movie

    @GET("shows/{id}")
    suspend fun showSummary(
        @Path("id") id: String,
        @Query("extended") extended: String = "full,images",
    ): Show

    @GET("movies/{id}/related")
    suspend fun relatedMovies(
        @Path("id") id: String,
        @Query("limit") limit: Int = 20,
        @Query("extended") extended: String = "full,images",
    ): List<Movie>

    @GET("shows/{id}/related")
    suspend fun relatedShows(
        @Path("id") id: String,
        @Query("limit") limit: Int = 20,
        @Query("extended") extended: String = "full,images",
    ): List<Show>

    // ---- Search ----

    @GET("search/{type}")
    suspend fun search(
        @Path("type") type: String,
        @Query("query") query: String,
        @Query("limit") limit: Int = 30,
        @Query("extended") extended: String = "full,images",
    ): List<SearchResult>

    // ---- Sync (OAuth required) ----

    @GET("sync/watchlist")
    suspend fun watchlist(
        @Query("extended") extended: String = "full,images",
    ): List<WatchlistItem>

    @GET("sync/history")
    suspend fun history(
        @Query("limit") limit: Int = 40,
        @Query("extended") extended: String = "full,images",
    ): List<HistoryItem>

    @POST("sync/watchlist")
    suspend fun addToWatchlist(@Body body: SyncItems): Response<Unit>

    @POST("sync/watchlist/remove")
    suspend fun removeFromWatchlist(@Body body: SyncItems): Response<Unit>

    @POST("sync/history")
    suspend fun addToHistory(@Body body: SyncItems): Response<Unit>

    @POST("sync/history/remove")
    suspend fun removeFromHistory(@Body body: SyncItems): Response<Unit>

    @GET("users/settings")
    suspend fun userSettings(): UserSettings
}
