package com.trakt.tv

import android.content.Context
import com.trakt.tv.auth.AuthManager
import com.trakt.tv.data.TokenStore
import com.trakt.tv.data.TraktRepository
import com.trakt.tv.data.remote.Network

/**
 * Tiny manual DI container. Single instances of the network stack, repository
 * and auth manager, created once and shared by all ViewModels.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val tokenStore: TokenStore by lazy { TokenStore(appContext) }
    private val network: Network by lazy { Network(tokenStore) }
    val repository: TraktRepository by lazy { TraktRepository(network) }
    val authManager: AuthManager by lazy { AuthManager(network) }

    /** Warm the in-memory token cache so authed requests attach the Bearer token. */
    suspend fun warmUp() = tokenStore.load()
}
