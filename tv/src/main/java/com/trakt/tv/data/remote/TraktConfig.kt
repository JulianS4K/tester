package com.trakt.tv.data.remote

import com.trakt.tv.BuildConfig

/** Static API configuration for the Trakt client. */
object TraktConfig {
    const val BASE_URL = "https://api.trakt.tv/"
    const val API_VERSION = "2"
    val USER_AGENT = "TraktTV-AndroidTV/${BuildConfig.VERSION_NAME}"

    /** Trakt device-flow verification base (user visits verification_url + code). */
    const val ACTIVATE_URL = "https://trakt.tv/activate"

    val clientId: String get() = BuildConfig.TRAKT_CLIENT_ID
    val clientSecret: String get() = BuildConfig.TRAKT_CLIENT_SECRET

    /** True once real credentials have been baked in via local.properties/env. */
    val isConfigured: Boolean get() = clientId.isNotBlank() && clientSecret.isNotBlank()
}
