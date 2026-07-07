package com.trakt.tv.data.remote

import com.trakt.tv.data.TokenStore
import com.trakt.tv.data.model.RefreshTokenRequest
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

/**
 * Transparently refreshes the access token on a 401. The Trakt access token is
 * valid for a limited window; when it lapses we exchange the refresh token for
 * a new one and replay the request. If refresh fails, tokens are cleared so the
 * UI falls back to the sign-in screen.
 *
 * Uses [authApi], which runs on a client WITHOUT this authenticator, so a failed
 * refresh can never recurse.
 */
class TokenAuthenticator(
    private val tokenStore: TokenStore,
    private val authApi: TraktApi,
) : Authenticator {

    private val lock = Any()

    override fun authenticate(route: Route?, response: Response): Request? {
        val tokens = tokenStore.current ?: return null
        val refresh = tokens.refreshToken ?: return null
        if (responseCount(response) >= 2) return null // already retried once

        synchronized(lock) {
            val latest = tokenStore.current ?: return null
            val failedToken = response.request.header("Authorization")?.removePrefix("Bearer ")

            // Another thread may have refreshed already — just use the new token.
            if (latest.accessToken != failedToken) {
                return response.request.retryWith(latest.accessToken)
            }

            val refreshed = runBlocking {
                runCatching {
                    authApi.refreshToken(
                        RefreshTokenRequest(
                            refreshToken = refresh,
                            clientId = TraktConfig.clientId,
                            clientSecret = TraktConfig.clientSecret,
                        ),
                    )
                }.getOrNull()
            }

            val body = refreshed?.takeIf { it.isSuccessful }?.body()
            if (body == null) {
                runBlocking { tokenStore.clear() }
                return null
            }
            runBlocking { tokenStore.save(body, System.currentTimeMillis()) }
            return response.request.retryWith(body.accessToken)
        }
    }

    private fun Request.retryWith(accessToken: String): Request =
        newBuilder().header("Authorization", "Bearer $accessToken").build()

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
