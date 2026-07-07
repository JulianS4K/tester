package com.trakt.tv.ui

import com.trakt.tv.data.model.MediaType

/** In-app navigation targets. Roots show the nav rail; overlays are full-screen. */
sealed interface Destination {
    data object Home : Destination
    data object Search : Destination
    data object Library : Destination
    data object Settings : Destination
    data object SignIn : Destination
    data class Detail(val type: MediaType, val id: String, val title: String) : Destination

    val isRoot: Boolean
        get() = this is Home || this is Search || this is Library || this is Settings
}
