# 📺 Trakt TV — a custom Android TV / Fire TV home screen

A 10-foot [Trakt](https://trakt.tv) client for **Fire TV Stick** and **Google TV /
Android TV**, built with **Jetpack Compose for TV** — designed to work as your
**custom TV home screen (launcher)**. It surfaces **what you want to watch**
(your watchlist + trending), shows a row of the **streaming apps installed on your
TV**, and **opens them to watch** — all driven by the [Trakt API v2](https://api.trakt.tv).

This is the `:tv` Gradle module in this repository (separate from the `:app`
"Helm" phone app).

## Use it as your TV home screen

The app declares the Android **HOME** launcher intent, so on **Google TV /
Android TV** you can make it the default Home app:

- **Settings → Apps → Default apps → Home app → Trakt TV**, or press the **Home**
  button and pick Trakt TV when the system asks which launcher to use.
- To revert, choose the original launcher in the same setting.

When set as Home, the **Apps row** (top of the Home screen) launches your installed
streaming apps, and **Back on the Home screen does nothing** (it won't drop you to a
blank screen), as a launcher should.

> ⚠️ **Fire TV** locks its launcher — Amazon doesn't let a third-party app replace
> the Fire TV home screen. There the app still installs and runs as a normal app
> (and appears on the Fire TV home row); the launcher/Home-app behavior is a
> Google TV / Android TV feature.

## Features

| Area | Details |
|---|---|
| 🔐 **Sign in** | Trakt **device OAuth flow** — no keyboard needed. Shows a `user_code` + `verification_url` and a **QR code**; you authorize on your phone while the TV polls for the token. Tokens are stored in DataStore and **auto-refreshed** on expiry. |
| 🏠 **Home (launcher)** | Immersive **featured hero** up top, a launcher **status bar** (live clock + date + user), the **Apps row**, **Continue Watching** (with progress bars), **Your Watchlist**, and Trending / Recommended / Box Office / Anticipated rows. Loading shows a **skeleton**. Works as the device's default Home app. |
| 🎬 **Episode detail** | From Seasons → Episodes, open an episode for its still, overview, **Mark Watched** and **Rate**. |
| 🔎 **Search** | Text query across movies & shows (debounced), poster grid of results. |
| 📄 **Detail** | Backdrop, overview, rating, genres, runtime, **Up Next** episode, **Seasons → Episodes** (per-episode mark-watched), **Cast** (→ person page), and "More like this". |
| ➕ **Track actions** | Add to **Watchlist**, **Mark Watched**, **Add to Collection**, and **Rate 1–10** (writes to `/sync/*`). Prompts sign-in when needed. |
| 🧭 **Browse / Lists / Stats** | Browse by **genre**, explore **Trending Lists** (→ list contents), and view your **Trakt stats**. |
| 👤 **People** | Cast → person page with their movies & shows. |
| ▶️ **Ways to watch** | Trakt has no streaming links, so the detail page **bridges to the device's apps**: a **"Search on this TV"** action (Google TV → native ways-to-watch, Fire TV → universal search) plus buttons that **open the streaming apps installed on the device** (Netflix, Prime, Disney+, Max, Hulu, Apple TV, Paramount+, Peacock, YouTube), deep-linking into the title search where the provider supports it. Web links (**Trakt / IMDb / TMDB**, built from the `ids`) show when a browser is present. |
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
  watch/StreamingProvider.kt  Registry of TV streaming apps (Fire TV + Google TV pkgs)
  watch/WatchLauncher.kt     Launch installed apps / TV search / web links ("Ways to watch")
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
- **"Ways to watch" is a best-effort bridge.** Trakt returns no streaming/entitlement
  data, so the app can't guarantee a title is on a given service or deep-link to the
  exact episode. It reliably **opens installed apps** and **fires the TV's global
  search** (which surfaces native "ways to watch" on Google TV); per-provider title
  deep-links are used where a public one exists (Netflix, Disney+, YouTube). Package
  visibility for detection is declared in the manifest `<queries>` — add a provider's
  package there and to `watch/StreamingProvider.kt` to support more apps. For exact
  per-service availability, a future option is TMDB "watch providers" (needs a TMDB key).
- This is an unofficial client; "Trakt" is a trademark of its owners.
