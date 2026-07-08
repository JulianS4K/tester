package com.trakt.tv.data.remote

import com.trakt.tv.data.TokenStore
import okhttp3.Interceptor
import okhttp3.Response

/**
 * Attaches the Trakt-required headers to every request, plus the Bearer token
 * when the user is signed in. See "Required Headers" in the Trakt API docs.
 */
class AuthHeaderInterceptor(private val tokenStore: TokenStore) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("Content-Type", "application/json")
            .header("User-Agent", TraktConfig.USER_AGENT)
            .header("trakt-api-version", TraktConfig.API_VERSION)
            .header("trakt-api-key", TraktConfig.clientId)

        // Don't send a stale bearer to the token endpoints themselves.
        val path = chain.request().url.encodedPath
        val isAuthEndpoint = path.startsWith("/oauth/")
        if (!isAuthEndpoint) {
            tokenStore.current?.accessToken?.let { builder.header("Authorization", "Bearer $it") }
        }
        return chain.proceed(builder.build())
    }
}
