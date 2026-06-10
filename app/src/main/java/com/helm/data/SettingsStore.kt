package com.helm.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class EnforcementMode { MONITOR, ENFORCE }
enum class DispatchCadence(val display: String) { DAILY("Daily"), WEEKLY("Weekly") }

data class HelmSettings(
    val mode: EnforcementMode = EnforcementMode.MONITOR,
    val cadence: DispatchCadence = DispatchCadence.WEEKLY,
    val pinHash: String? = null,
    val currency: String = "₹",
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "helm_settings")

class SettingsStore(private val context: Context) {
    private object Keys {
        val MODE = stringPreferencesKey("mode")
        val CADENCE = stringPreferencesKey("cadence")
        val PIN = stringPreferencesKey("pin_hash")
        val CURRENCY = stringPreferencesKey("currency")
    }

    val settings: Flow<HelmSettings> = context.dataStore.data.map { p ->
        HelmSettings(
            mode = p[Keys.MODE]?.let { runCatching { EnforcementMode.valueOf(it) }.getOrNull() } ?: EnforcementMode.MONITOR,
            cadence = p[Keys.CADENCE]?.let { runCatching { DispatchCadence.valueOf(it) }.getOrNull() } ?: DispatchCadence.WEEKLY,
            pinHash = p[Keys.PIN],
            currency = p[Keys.CURRENCY] ?: "₹",
        )
    }

    suspend fun setMode(mode: EnforcementMode) = context.dataStore.edit { it[Keys.MODE] = mode.name }
    suspend fun setCadence(c: DispatchCadence) = context.dataStore.edit { it[Keys.CADENCE] = c.name }
    suspend fun setCurrency(c: String) = context.dataStore.edit { it[Keys.CURRENCY] = c }
    suspend fun setPinHash(hash: String?) = context.dataStore.edit {
        if (hash == null) it.remove(Keys.PIN) else it[Keys.PIN] = hash
    }

    // Simple SHA-256 PIN hashing — adequate for an on-device self-control gate.
    companion object {
        fun hashPin(pin: String): String {
            val md = java.security.MessageDigest.getInstance("SHA-256")
            return md.digest(("helm:$pin").toByteArray()).joinToString("") { "%02x".format(it) }
        }
    }
}
