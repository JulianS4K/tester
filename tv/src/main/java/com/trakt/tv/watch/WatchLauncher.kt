package com.trakt.tv.watch

import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.trakt.tv.data.model.MediaItem
import com.trakt.tv.data.model.MediaType

/**
 * Bridges a Trakt title to something the TV can actually play or show. Trakt has
 * no streaming links, so we:
 *   1. open the streaming apps installed on this device (deep-linking into the
 *      title's in-app search when the provider supports it),
 *   2. fall back to the platform's global search ("ways to watch" on Google TV,
 *      universal search on Fire TV),
 *   3. offer web links (Trakt / IMDb / TMDB) for devices that have a browser.
 *
 * Every launch is best-effort and guarded; nothing here throws to the UI.
 */
object WatchLauncher {

    data class Installed(val provider: StreamingProvider, val packageName: String)

    /** Providers from [StreamingProvider.ALL] that are installed on this device. */
    fun installedProviders(context: Context): List<Installed> {
        val pm = context.packageManager
        return StreamingProvider.ALL.mapNotNull { provider ->
            provider.packages.firstOrNull { isInstalled(pm, it) }
                ?.let { Installed(provider, it) }
        }
    }

    /** Open a provider on the title's search page, or cold-launch the app. */
    fun launch(context: Context, installed: Installed, title: String): Boolean {
        val pm = context.packageManager
        val pkg = installed.packageName

        installed.provider.searchUrl?.let { build ->
            val deepLink = Intent(Intent.ACTION_VIEW, Uri.parse(build(title)))
                .setPackage(pkg)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (deepLink.resolveActivity(pm) != null) {
                return runCatching { context.startActivity(deepLink); true }.getOrDefault(false)
            }
        }

        val launch = pm.getLeanbackLaunchIntentForPackage(pkg)
            ?: pm.getLaunchIntentForPackage(pkg)
        launch?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (launch != null) {
            runCatching { context.startActivity(launch); true }.getOrDefault(false)
        } else {
            false
        }
    }

    fun canWebSearch(context: Context): Boolean =
        webSearchIntent("x").resolveActivity(context.packageManager) != null

    /** Fire the device's global/web search for the title. */
    fun webSearch(context: Context, title: String): Boolean {
        val intent = webSearchIntent(title)
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent); true }.getOrDefault(false)
        } else {
            false
        }
    }

    fun canOpenUrls(context: Context): Boolean =
        Intent(Intent.ACTION_VIEW, Uri.parse("https://trakt.tv"))
            .resolveActivity(context.packageManager) != null

    fun openUrl(context: Context, url: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return if (intent.resolveActivity(context.packageManager) != null) {
            runCatching { context.startActivity(intent); true }.getOrDefault(false)
        } else {
            false
        }
    }

    private fun webSearchIntent(title: String): Intent =
        Intent(Intent.ACTION_WEB_SEARCH)
            .putExtra(SearchManager.QUERY, title)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun isInstalled(pm: PackageManager, pkg: String): Boolean =
        try {
            pm.getPackageInfo(pkg, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
}

/** Browser links built from the standard Trakt `ids` object. */
object WatchLinks {
    fun traktUrl(item: MediaItem): String {
        val segment = if (item.type == MediaType.MOVIE) "movies" else "shows"
        val id = item.ids.slug ?: item.ids.trakt.toString()
        return "https://trakt.tv/$segment/$id"
    }

    fun imdbUrl(item: MediaItem): String? =
        item.ids.imdb?.takeIf { it.isNotBlank() }?.let { "https://www.imdb.com/title/$it/" }

    fun tmdbUrl(item: MediaItem): String? {
        val tmdb = item.ids.tmdb ?: return null
        val kind = if (item.type == MediaType.MOVIE) "movie" else "tv"
        return "https://www.themoviedb.org/$kind/$tmdb"
    }
}
