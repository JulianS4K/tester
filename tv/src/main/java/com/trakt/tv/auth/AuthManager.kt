package com.trakt.tv.auth

import com.trakt.tv.data.TokenStore
import com.trakt.tv.data.model.DeviceCode
import com.trakt.tv.data.model.DeviceCodeRequest
import com.trakt.tv.data.model.DeviceTokenRequest
import com.trakt.tv.data.remote.Network
import com.trakt.tv.data.remote.TraktConfig
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** One step of the device-authorization flow, surfaced to the sign-in UI. */
sealed interface AuthEvent {
    /** Show this code + verification_url (and QR) to the user. */
    data class ShowCode(val code: DeviceCode) : AuthEvent
    object Authorized : AuthEvent
    /** Flow finished without authorizing (expired, denied, or configuration error). */
    data class Failed(val reason: String) : AuthEvent
}

/**
 * Implements Trakt's device-code OAuth flow (ideal for a 10-foot UI): generate a
 * code, show it to the user, then poll [`/oauth/device/token`] at the returned
 * interval until they authorize on their phone.
 *
 * Poll status codes handled per the API docs: 200 success, 400 pending, 409
 * already used, 410 expired, 418 denied, 429 slow down.
 */
class AuthManager(private val network: Network) {

    private val authApi get() = network.authApi
    private val tokenStore: TokenStore get() = network.tokenStore

    fun authorize(): Flow<AuthEvent> = flow {
        if (!TraktConfig.isConfigured) {
            emit(AuthEvent.Failed("Trakt API key not configured"))
            return@flow
        }

        val code = try {
            authApi.deviceCode(DeviceCodeRequest(TraktConfig.clientId))
        } catch (t: Throwable) {
            emit(AuthEvent.Failed("Couldn't reach Trakt: ${t.message ?: "network error"}"))
            return@flow
        }
        emit(AuthEvent.ShowCode(code))

        var intervalMs = code.interval.coerceAtLeast(1) * 1000L
        val deadline = System.currentTimeMillis() + code.expiresIn * 1000L

        while (System.currentTimeMillis() < deadline) {
            delay(intervalMs)
            val response = try {
                authApi.deviceToken(
                    DeviceTokenRequest(
                        code = code.deviceCode,
                        clientId = TraktConfig.clientId,
                        clientSecret = TraktConfig.clientSecret,
                    ),
                )
            } catch (t: Throwable) {
                continue // transient network hiccup; keep polling until the deadline
            }

            when (response.code()) {
                200 -> {
                    val token = response.body()
                    if (token == null) {
                        emit(AuthEvent.Failed("Empty token response"))
                        return@flow
                    }
                    tokenStore.save(token, System.currentTimeMillis())
                    refreshUsername()
                    emit(AuthEvent.Authorized)
                    return@flow
                }
                400 -> Unit // pending — user hasn't entered the code yet
                429 -> intervalMs += 1000L // slow down
                409 -> {
                    emit(AuthEvent.Failed("This code was already used. Please try again."))
                    return@flow
                }
                410 -> {
                    emit(AuthEvent.Failed("The code expired. Please try again."))
                    return@flow
                }
                418 -> {
                    emit(AuthEvent.Failed("Authorization was denied."))
                    return@flow
                }
                404 -> {
                    emit(AuthEvent.Failed("Invalid device code. Please try again."))
                    return@flow
                }
                else -> Unit // unexpected — keep polling until the deadline
            }
        }
        emit(AuthEvent.Failed("The code expired. Please try again."))
    }

    /** Fetch and cache the signed-in username for display. Best-effort. */
    private suspend fun refreshUsername() {
        runCatching { network.api.userSettings() }
            .getOrNull()
            ?.user
            ?.let { tokenStore.saveUsername(it.username ?: it.name) }
    }

    suspend fun signOut() {
        tokenStore.clear()
    }
}
