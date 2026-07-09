package com.trakt.tv.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Appends the TMDB v3 `api_key` query param to every TMDB request. */
class TmdbKeyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        val url = original.url.newBuilder()
            .addQueryParameter("api_key", TmdbConfig.apiKey)
            .build()
        return chain.proceed(original.newBuilder().url(url).build())
    }
}
