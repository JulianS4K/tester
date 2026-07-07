@file:OptIn(ExperimentalTvMaterial3Api::class)

package com.trakt.tv.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Surface
import com.trakt.tv.AppContainer
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.ui.browse.BrowseScreen
import com.trakt.tv.ui.components.MessageView
import com.trakt.tv.ui.components.NavRail
import com.trakt.tv.ui.detail.DetailScreen
import com.trakt.tv.ui.home.HomeScreen
import com.trakt.tv.ui.library.LibraryScreen
import com.trakt.tv.ui.lists.ListDetailScreen
import com.trakt.tv.ui.lists.ListsScreen
import com.trakt.tv.ui.person.PersonScreen
import com.trakt.tv.ui.search.SearchScreen
import com.trakt.tv.ui.season.SeasonScreen
import com.trakt.tv.ui.settings.SettingsScreen
import com.trakt.tv.ui.signin.SignInScreen
import com.trakt.tv.ui.stats.StatsScreen
import kotlinx.coroutines.launch

@Composable
fun TraktApp(container: AppContainer) {
    val signedIn by container.repository.isSignedIn.collectAsStateWithLifecycle(initialValue = false)
    val username by container.repository.username.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { container.warmUp() }

    val backStack = remember { mutableStateListOf<Destination>(Destination.Home) }
    val current = backStack.last()

    fun push(destination: Destination) { backStack.add(destination) }
    fun selectRoot(destination: Destination) {
        backStack.clear()
        backStack.add(destination)
    }
    fun pop() { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    // Always consume Back: pop within the app, and at the Home root do nothing
    // (a Home launcher must never "back out" to a blank screen).
    BackHandler(enabled = true) { if (backStack.size > 1) pop() }

    val openItem: (MediaItem) -> Unit = { item ->
        push(Destination.Detail(item.type, item.traktId.toString(), item.title))
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxSize()) {
            if (current.isRoot) {
                NavRail(
                    current = current,
                    signedIn = signedIn,
                    username = username,
                    onSelect = { selectRoot(it) },
                    onAccount = { push(Destination.SignIn) },
                )
            }
            Box(Modifier.weight(1f).fillMaxSize()) {
                when (val dest = current) {
                    Destination.Home -> HomeScreen(onOpen = openItem)
                    Destination.Search -> SearchScreen(onOpen = openItem)
                    Destination.Browse -> BrowseScreen(onOpen = openItem)
                    Destination.Lists -> ListsScreen(
                        onOpenList = { id, name -> push(Destination.ListDetail(id, name)) },
                    )
                    Destination.Library -> if (signedIn) {
                        LibraryScreen(onOpen = openItem)
                    } else {
                        MessageView(
                            message = "Sign in to see your Trakt watchlist and history.",
                            actionLabel = "Sign in with Trakt",
                            onAction = { push(Destination.SignIn) },
                        )
                    }
                    Destination.Stats -> if (signedIn) {
                        StatsScreen()
                    } else {
                        MessageView(
                            message = "Sign in to see your Trakt stats.",
                            actionLabel = "Sign in with Trakt",
                            onAction = { push(Destination.SignIn) },
                        )
                    }
                    Destination.Settings -> SettingsScreen(
                        signedIn = signedIn,
                        username = username,
                        onSignIn = { push(Destination.SignIn) },
                        onSignOut = { scope.launch { container.authManager.signOut() } },
                    )
                    Destination.SignIn -> SignInScreen(
                        onSignedIn = { pop() },
                        onSkip = { pop() },
                    )
                    is Destination.Detail -> DetailScreen(
                        type = dest.type,
                        id = dest.id,
                        onOpen = openItem,
                        onPerson = { cast -> push(Destination.Person(cast.personId.toString(), cast.name)) },
                        onSeason = { season -> push(Destination.Season(dest.id, dest.title, season)) },
                        onRequireSignIn = { push(Destination.SignIn) },
                    )
                    is Destination.Season -> SeasonScreen(
                        showId = dest.showId,
                        showTitle = dest.showTitle,
                        season = dest.season,
                        onRequireSignIn = { push(Destination.SignIn) },
                    )
                    is Destination.Person -> PersonScreen(
                        id = dest.id,
                        name = dest.name,
                        onOpen = openItem,
                    )
                    is Destination.ListDetail -> ListDetailScreen(
                        id = dest.id,
                        name = dest.name,
                        onOpen = openItem,
                    )
                }
            }
        }
    }
}
