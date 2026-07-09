package com.trakt.tv.launcher

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/** A launchable app on the device, for the home screen's all-apps row. */
data class LaunchApp(
    val packageName: String,
    val label: String,
    val icon: ImageBitmap?,
    val launch: Intent,
)

/**
 * Enumerates and launches every app installed on the device — the core of a
 * real home screen (as opposed to a normal app that only opens a fixed set).
 *
 * Visibility is granted by the manifest `<queries>` (MAIN + LAUNCHER /
 * LEANBACK_LAUNCHER) plus QUERY_ALL_PACKAGES; a default Home app is also granted
 * broad package visibility by the system.
 */
object LauncherApps {

    fun installed(context: Context): List<LaunchApp> {
        val pm = context.packageManager
        val self = context.packageName
        // Preserve insertion order but dedupe by package; prefer the leanback entry.
        val byPackage = LinkedHashMap<String, LaunchApp>()

        for (category in listOf(Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER)) {
            val query = Intent(Intent.ACTION_MAIN).addCategory(category)
            @Suppress("DEPRECATION")
            val resolved = runCatching { pm.queryIntentActivities(query, 0) }.getOrDefault(emptyList())
            for (info in resolved) {
                val activity = info.activityInfo ?: continue
                val pkg = activity.packageName
                if (pkg == self || byPackage.containsKey(pkg)) continue
                val label = runCatching { info.loadLabel(pm)?.toString() }.getOrNull() ?: pkg
                val icon = runCatching { info.loadIcon(pm).toBitmap().asImageBitmap() }.getOrNull()
                val launch = Intent(Intent.ACTION_MAIN)
                    .addCategory(category)
                    .setClassName(pkg, activity.name)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                byPackage[pkg] = LaunchApp(pkg, label, icon, launch)
            }
        }
        return byPackage.values.sortedBy { it.label.lowercase() }
    }

    fun launch(context: Context, app: LaunchApp): Boolean =
        runCatching { context.startActivity(app.launch); true }.getOrDefault(false)

    /** Open the device's system Settings (a home screen needs a way there). */
    fun openSettings(context: Context): Boolean =
        runCatching {
            context.startActivity(Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        }.getOrDefault(false)
}
