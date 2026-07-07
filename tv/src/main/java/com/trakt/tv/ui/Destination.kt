package com.trakt.tv.ui

import com.trakt.tv.data.model.MediaType

/** In-app navigation targets. Roots show the nav rail; overlays are full-screen. */
sealed interface Destination {
    data object Home : Destination
    data object Search : Destination
    data object Browse : Destination
    data object Lists : Destination
    data object Library : Destination
    data object Stats : Destination
    data object Settings : Destination
    data object SignIn : Destination
    data class Detail(val type: MediaType, val id: String, val title: String) : Destination
    data class Season(val showId: String, val showTitle: String, val season: Int) : Destination
    data class EpisodeDetail(val showId: String, val season: Int, val number: Int) : Destination
    data class Person(val id: String, val name: String) : Destination
    data class ListDetail(val id: String, val name: String) : Destination

    val isRoot: Boolean
        get() = this is Home || this is Search || this is Browse || this is Lists ||
            this is Library || this is Stats || this is Settings
}
