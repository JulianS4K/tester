# Ship Trakt TV as the device Home screen (launcher / OS)

The `:tv` app already declares the Android **HOME** launcher intent, enumerates
and launches **every installed app** (Apps row + Settings), and consumes Back at
the Home root. That makes it a legitimate **home screen**, not just an app. What's
left is a *device* decision: make it the **default Home** so it's what you see on
boot. Three ways, easiest first.

## 1. Set it as Home on a normal device (no custom OS)

Install the APK, then either:

- Press the **Home** button → the system shows a launcher picker → choose Trakt TV
  → "Always"; or
- **Settings → Apps → Default apps → Home app → Trakt TV**; or
- from adb:
  ```bash
  adb install -r tv/build/outputs/apk/debug/tv-debug.apk
  adb shell cmd package set-home-activity com.trakt.tv/com.trakt.tv.MainActivity
  ```
  To revert: set-home-activity back to the stock launcher (e.g.
  `com.google.android.tvlauncher/.MainActivity` on Google TV), or pick it in Settings.

> Google TV / Android TV allow this. **Fire TV locks its launcher** and won't.

## 2. DIY set-top box — Android on a Raspberry Pi, this as the Home

This is the "make my own OS" route without building AOSP from scratch:

1. Flash an **Android TV** build to the Pi — e.g. **LineageOS for Android TV**
   (KonstaKMI's community `rpi4`/`rpi5` builds) or **emteria.OS**. The app is
   **GMS-free** (no Google Play Services), so a de-Googled build is fine.
2. Boot, enable **Developer options → USB/Network debugging**, connect adb.
3. Install + set as Home:
   ```bash
   adb connect <pi-ip>:5555
   adb install -r tv-debug.apk
   adb shell cmd package set-home-activity com.trakt.tv/com.trakt.tv.MainActivity
   ```
4. (Optional) disable the stock launcher so Trakt TV is the only Home:
   ```bash
   adb shell pm disable-user --user 0 <stock.launcher.package>
   ```

Result: power on the Pi → your Trakt home screen, with every installed streaming
app one click away.

## 3. Bake it into an AOSP / LineageOS build (true firmware)

Ship it as a preinstalled system app and the default Home in your own ROM.

1. Build a release APK and drop it next to these files:
   ```bash
   ./gradlew :tv:assembleRelease   # sign it, or use presigned + your keys
   cp tv/build/outputs/apk/release/tv-release.apk aosp/prebuilt/TraktTvLauncher.apk
   ```
2. Copy `aosp/Android.bp` + `aosp/prebuilt/` into your device tree, e.g.
   `device/<vendor>/<board>/apps/TraktTvLauncher/`.
3. Include it and drop the stock launcher in your `device.mk` (see
   `aosp/trakt_launcher.mk` for the snippet):
   ```make
   PRODUCT_PACKAGES += TraktTvLauncher
   # and do NOT include the stock launcher (e.g. remove/replace TvLauncher/Launcher3)
   ```
   With Trakt TV as the only `HOME` activity in the image, the framework selects it
   as the default Home automatically. If you keep another launcher, set the default
   via a `config_defaultLauncherComponent` overlay in an RRO/overlay package.
4. (Optional) grant system privileges only if you later add system features
   (input switching, etc.) — set `certificate: "platform"` in `Android.bp` and place
   the app under `priv-app`. The current feature set needs **no** system signature.

### Notes
- minSdk 26, targetSdk 35 — target an Android 8+ (API 26+) ROM.
- The app needs `INTERNET` + `QUERY_ALL_PACKAGES` (already declared); a default Home
  app is also granted broad package visibility by the platform.
- `assembleRelease` currently uses the debug-style config with no minify; add your
  signing config in `tv/build.gradle.kts` before shipping a real ROM.
