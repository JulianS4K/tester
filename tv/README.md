# 📺 Trakt TV — Android TV / Fire TV client

A 10-foot [Trakt](https://trakt.tv) client for **Fire TV Stick** and **Google TV /
Android TV**, built with **Jetpack Compose for TV**. Browse what's trending,
search the catalog, open rich detail pages, and manage your Trakt **watchlist**
and **watch history** — all driven by the [Trakt API v2](https://api.trakt.tv).

This is the `:tv` Gradle module in this repository (separate from the `:app`
"Helm" phone app).

## Features

| Area | Details |
|---|---|
| 🔐 **Sign in** | Trakt **device OAuth flow** — no keyboard needed. Shows a `user_code` + `verification_url` and a **QR code**; you authorize on your phone while the TV polls for the token. Tokens are stored in DataStore and **auto-refreshed** on expiry. |
| 🏠 **Home** | Horizontal, D-pad-navigable rows: Trending / Popular Shows & Movies + Anticipated. |
| 🔎 **Search** | Text query across movies & shows (debounced), poster grid of results. |
| 📄 **Detail** | Backdrop + logo, overview, rating, genres, runtime, certification, network, and a "More like this" row. |
| ➕ **Sync actions** | Add to **Watchlist** and **Mark as Watched** (writes to `/sync/watchlist` and `/sync/history`). Prompts sign-in when needed. |
| 📚 **Library** | Your Watchlist and History (OAuth), as poster grids. |

## Architecture

```
tv/src/main/java/com/trakt/tv/
  TraktTvApp.kt            Application → builds AppContainer
  AppContainer.kt          Manual DI: TokenStore, Network, Repository, AuthManager
  MainActivity.kt          Compose host (TraktTvTheme → TraktApp)
  data/
    model/Models.kt        Moshi models for the Trakt API v2 responses/bodies
    model/MediaItem.kt      Unified movie/show UI model (+ image URL helpers)
    remote/TraktApi.kt      Retrofit interface (discover, detail, search, sync, auth)
    remote/Network.kt       OkHttp + Moshi + Retrofit wiring (authApi + authed api)
    remote/AuthHeaderInterceptor.kt   Required Trakt headers + Bearer token
    remote/TokenAuthenticator.kt      Transparent refresh-token on 401
    remote/TraktConfig.kt   Base URL, API version, client id/secret (BuildConfig)
    TokenStore.kt           DataStore-backed token persistence + in-memory cache
    TraktRepository.kt      Main-safe entry point returning MediaItem
  auth/AuthManager.kt       Device-code flow: generate code, poll for token
  util/QrCodes.kt           ZXing QR bitmap for the activation URL
  ui/
    theme/Theme.kt          tv-material3 dark color scheme
    TraktApp.kt             Nav host (rail + back stack) — Home/Search/Library/Settings/Detail/SignIn
    components/             PosterCard, MediaRow, NavRail, status views
    home/ search/ detail/ library/ signin/ settings/   screen + ViewModel per feature
```

**Stack:** Kotlin 2.0, Jetpack **Compose for TV** (`androidx.tv:tv-material`) +
Compose Material3 (a few widgets), Retrofit + OkHttp + Moshi, Coil (images),
DataStore (tokens), ZXing (QR). Single-Activity, ViewModel + coroutines/Flow.

## Configure your Trakt API keys

The API key is **never committed**. Create an app at
<https://trakt.tv/oauth/applications/new> (set the redirect URI to
`urn:ietf:wg:oauth:2.0:oob`), then add the credentials to **`local.properties`**
in the repo root:

```properties
trakt.clientId=YOUR_CLIENT_ID
trakt.clientSecret=YOUR_CLIENT_SECRET
```

Alternatively pass them as Gradle properties (`-Ptrakt.clientId=…`) or the
`TRAKT_CLIENT_ID` / `TRAKT_CLIENT_SECRET` environment variables. Without keys the
app still launches and shows an "API key not configured" notice (public browsing
needs at least a `clientId`; sign-in needs both).

## Build & install

**Requirements:** Android Studio (Koala+), JDK 17, Android SDK 35.

```bash
./gradlew :tv:assembleDebug
# APK: tv/build/outputs/apk/debug/tv-debug.apk

# Fire TV / Android TV over adb (enable Developer Options → ADB debugging):
adb connect <TV_IP>:5555
adb install -r tv/build/outputs/apk/debug/tv-debug.apk
```

The app declares `LEANBACK_LAUNCHER` (so it appears on the TV home row) and does
**not** require a touchscreen. `leanback` is `required="false"`, so it also
installs on phones/emulators for quick testing.

## Notes

- Images come from Trakt via `?extended=images` (protocol-less WebP URLs; the app
  prepends `https://` and Coil caches them). Trakt requires image caching — Coil's
  default disk/memory cache satisfies this.
- The access token is valid for a limited window; `TokenAuthenticator` swaps in a
  fresh one on a 401 and replays the request. A failed refresh clears the session.
- This is an unofficial client; "Trakt" is a trademark of its owners.
