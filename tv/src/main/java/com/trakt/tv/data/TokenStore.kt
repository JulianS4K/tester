package com.trakt.tv.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.trakt.tv.data.model.AccessToken
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "trakt_auth")

/**
 * Persists the OAuth tokens in a DataStore. Read synchronously (blocking-free
 * via a cached [current]) by the OkHttp interceptor/authenticator, and observed
 * as a [Flow] by the UI to know whether the user is signed in.
 */
class TokenStore(private val context: Context) {

    private object Keys {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_millis")
        val USERNAME = stringPreferencesKey("username")
    }

    /** Latest known tokens, kept in memory so OkHttp threads never block on IO. */
    @Volatile
    var current: Tokens? = null
        private set

    data class Tokens(
        val accessToken: String,
        val refreshToken: String?,
        val expiresAtMillis: Long,
    )

    val isSignedIn: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ACCESS]?.isNotBlank() == true }

    val username: Flow<String?> =
        context.dataStore.data.map { it[Keys.USERNAME] }

    /** Warm the in-memory cache. Call once on startup. */
    suspend fun load() {
        val prefs = context.dataStore.data.first()
        val access = prefs[Keys.ACCESS]
        current = if (access.isNullOrBlank()) null else Tokens(
            accessToken = access,
            refreshToken = prefs[Keys.REFRESH],
            expiresAtMillis = prefs[Keys.EXPIRES_AT] ?: 0L,
        )
    }

    suspend fun save(token: AccessToken, nowMillis: Long) {
        val expiresAt = nowMillis + token.expiresIn * 1000L
        current = Tokens(token.accessToken, token.refreshToken, expiresAt)
        context.dataStore.edit { prefs ->
            prefs[Keys.ACCESS] = token.accessToken
            token.refreshToken?.let { prefs[Keys.REFRESH] = it }
            prefs[Keys.EXPIRES_AT] = expiresAt
        }
    }

    suspend fun saveUsername(name: String?) {
        context.dataStore.edit { prefs ->
            if (name.isNullOrBlank()) prefs.remove(Keys.USERNAME) else prefs[Keys.USERNAME] = name
        }
    }

    suspend fun clear() {
        current = null
        context.dataStore.edit { it.clear() }
    }
}
