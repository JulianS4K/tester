package com.trakt.tv.watch

import android.net.Uri

/**
 * A streaming app we know how to launch on a TV. Packages are listed most-
 * specific-first (Fire TV / Google TV variants); the first one that's installed
 * wins. [searchUrl], when present, is a web link the app itself handles so we
 * can jump straight into an in-app search for the title instead of just opening
 * the app cold.
 */
data class StreamingProvider(
    val id: String,
    val name: String,
    val packages: List<String>,
    val searchUrl: ((title: String) -> String)? = null,
) {
    companion object {
        private fun enc(title: String): String = Uri.encode(title)

        /** Registry of common TV streaming apps (Fire TV + Google TV package ids). */
        val ALL: List<StreamingProvider> = listOf(
            StreamingProvider(
                id = "netflix",
                name = "Netflix",
                // Fire TV: com.netflix.ninja · Google TV / Android: com.netflix.mediaclient
                packages = listOf("com.netflix.ninja", "com.netflix.mediaclient"),
                searchUrl = { "https://www.netflix.com/search?q=${enc(it)}" },
            ),
            StreamingProvider(
                id = "prime",
                name = "Prime Video",
                packages = listOf(
                    "com.amazon.avod", // Fire TV
                    "com.amazon.amazonvideo.livingroom", // Android/Google TV
                    "com.amazon.avod.thirdpartyclient",
                ),
            ),
            StreamingProvider(
                id = "disney",
                name = "Disney+",
                packages = listOf("com.disney.disneyplus"),
                searchUrl = { "https://www.disneyplus.com/search?q=${enc(it)}" },
            ),
            StreamingProvider(
                id = "max",
                name = "Max",
                packages = listOf("com.wbd.stream", "com.hbo.hbonow"),
            ),
            StreamingProvider(
                id = "hulu",
                name = "Hulu",
                packages = listOf("com.hulu.livingroomplus", "com.hulu.plus"),
            ),
            StreamingProvider(
                id = "appletv",
                name = "Apple TV",
                packages = listOf(
                    "com.apple.atve.androidtv.appletv", // Google TV
                    "com.apple.atve.amazon.appletv", // Fire TV
                ),
            ),
            StreamingProvider(
                id = "paramount",
                name = "Paramount+",
                packages = listOf("com.cbs.ott"),
            ),
            StreamingProvider(
                id = "peacock",
                name = "Peacock",
                packages = listOf("com.peacocktv.peacockandroid"),
            ),
            StreamingProvider(
                id = "youtube",
                name = "YouTube",
                packages = listOf(
                    "com.google.android.youtube.tv", // Google TV
                    "com.amazon.firetv.youtube", // Fire TV
                    "com.google.android.youtube",
                ),
                searchUrl = { "https://www.youtube.com/results?search_query=${enc(it)}" },
            ),
        )

        /** Every package we may try to launch — mirrored in the manifest <queries>. */
        val ALL_PACKAGES: List<String> = ALL.flatMap { it.packages }.distinct()
    }
}
