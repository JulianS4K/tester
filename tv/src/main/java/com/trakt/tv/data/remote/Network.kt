package com.trakt.tv.data.remote

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import com.trakt.tv.BuildConfig
import com.trakt.tv.data.TokenStore
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Builds the two Retrofit-backed [TraktApi] instances the app uses:
 *  - [authApi]: header interceptor only (device code/token + refresh live here so
 *    a failed refresh cannot recurse through the authenticator).
 *  - [api]: header interceptor + [TokenAuthenticator] for all content/sync calls.
 */
class Network(val tokenStore: TokenStore) {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val converter = MoshiConverterFactory.create(moshi)

    private val logging = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BASIC
        else HttpLoggingInterceptor.Level.NONE
    }

    private val bootstrapClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthHeaderInterceptor(tokenStore))
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val authApi: TraktApi = Retrofit.Builder()
        .baseUrl(TraktConfig.BASE_URL)
        .client(bootstrapClient)
        .addConverterFactory(converter)
        .build()
        .create(TraktApi::class.java)

    private val authedClient: OkHttpClient = bootstrapClient.newBuilder()
        .authenticator(TokenAuthenticator(tokenStore, authApi))
        .build()

    val api: TraktApi = Retrofit.Builder()
        .baseUrl(TraktConfig.BASE_URL)
        .client(authedClient)
        .addConverterFactory(converter)
        .build()
        .create(TraktApi::class.java)

    // ---- TMDB (optional "Available on <service>" data) ----

    private val tmdbClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(TmdbKeyInterceptor())
        .addInterceptor(logging)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    val tmdbApi: TmdbApi = Retrofit.Builder()
        .baseUrl(TmdbConfig.BASE_URL)
        .client(tmdbClient)
        .addConverterFactory(converter)
        .build()
        .create(TmdbApi::class.java)
}
