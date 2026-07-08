package com.trakt.tv.ui

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.trakt.tv.AppContainer
import com.trakt.tv.TraktTvApp

/** Pulls the app's [AppContainer] out of [CreationExtras] inside a ViewModel factory. */
fun CreationExtras.appContainer(): AppContainer {
    val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as TraktTvApp
    return app.container
}
