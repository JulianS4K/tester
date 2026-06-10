# ⚓ Helm — your personal control center

Take the helm of your phone **and** your day. Helm is a private, on-device Android
app that **tracks your screen time**, lets you **log your life** (mood, habits,
health and money), helps you **set limits & focus windows**, and emails you a
periodic **self-newsletter — "The Dispatch"** summarizing it all.

Built **monitor-first**: it observes and reports before it ever restricts.
Everything stays on your device — no servers, no accounts, no cloud.

---

## What it does (Phase 1 — this build)

| Pillar | Details |
|---|---|
| 📊 **Usage tracking** | Per-app foreground time + launch counts via Android's `UsageStatsManager`. Daily total, 7-day bar chart, full app breakdown. |
| ✍️ **Manual tracking** | One flexible logger for **mood, habits, notes, weight, sleep, steps, water, workouts, expenses & income**. |
| 🛑 **Control center** | Per-app **daily minute limits** and recurring **focus windows** (work / study / sleep). Limits notify you when hit. |
| 📰 **The Dispatch** | Auto-generated daily/weekly digest of screen time, money, health and habits — delivered as a notification + in-app report. |
| 🔒 **Bypass resistance** | PIN-gated settings; foreground watcher (accessibility) groundwork for Phase 2 enforcement. |
| ❤️ **Health Connect sync** | Optional auto-import of steps, sleep, weight & workouts from Health Connect (Settings → Health Connect). |
| 💳 **Finance CSV import** | Import a bank / UPI statement CSV on the Track tab; columns are auto-detected. |

### Coming next
- **Hard enforcement** — actually block over-limit / in-focus apps via the accessibility service + block screen.
- **Live bank sync** — account-aggregator / Plaid-style; Helm never asks for raw bank credentials.
- **Email delivery** of The Dispatch.

> ⚠️ **Health Connect version:** `connect-client` is pinned in
> `gradle/libs.versions.toml` (`healthConnect`). Health Connect's API is
> version-sensitive — if the first build fails on `com.helm.health.*`, bump that
> version (e.g. to a newer `1.1.0-rcNN` / stable `1.1.0`) and re-sync.

---

## Build & install

This is a standard **Android Studio** project (Kotlin + Jetpack Compose).

**Requirements:** Android Studio (Koala+), JDK 17, Android SDK 35.

```bash
# From a machine with the Android SDK installed:
./gradlew assembleDebug
# APK output:
#   app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Or just open the folder in Android Studio and hit **Run ▶**.

### First-run setup (on the phone)
1. **Home → "Grant usage access"** → enable Helm in *Usage access* (required for screen-time tracking).
2. Allow **notifications** when prompted (for The Dispatch + limit alerts).
3. *(Optional)* **Settings → Foreground watcher** → enable the accessibility service for "what you're using now" and future enforcement.
4. *(Optional)* set a **PIN** in Settings to protect your configuration.

---

## Architecture

```
app/src/main/java/com/helm/
  HelmApp.kt              Application — channels + schedules periodic work
  MainActivity.kt         Compose host, notification permission
  data/
    Entities.kt           Room entities + LogKind enum + converters
    HelmDao.kt            All queries (usage, logs, limits, focus, dispatch)
    HelmDatabase.kt       Room database (singleton)
    HelmRepository.kt     Single data/actions entry point
    SettingsStore.kt      DataStore prefs (mode, cadence, PIN, currency)
  usage/UsageCollector.kt UsageStatsManager → UsageRecords; installed-app list
  dispatch/DispatchGenerator.kt   Builds "The Dispatch" markdown
  work/                   WorkManager workers, scheduler, boot receiver, notifications
  block/                  Accessibility foreground watcher (monitor-first)
  ui/
    HelmViewModel.kt      One AndroidViewModel exposing StateFlows + actions
    HelmRoot.kt           Bottom-nav scaffold
    theme/Theme.kt        Material 3 dark/light scheme
    components/           SectionCard, StatTile, BarChart, ProgressBar
    screens/              Dashboard, Usage, Track, Control, Dispatch, Settings
```

**Stack:** Kotlin 2.0, Jetpack Compose (Material 3), Room (KSP), WorkManager,
DataStore. Single-module, no networking, **all data local**.

## Privacy

Helm is designed for **self-control on your own device**. It reads *which* apps
you use and for how long — never their contents (the accessibility service sets
`canRetrieveWindowContent=false`). Nothing leaves the phone.
