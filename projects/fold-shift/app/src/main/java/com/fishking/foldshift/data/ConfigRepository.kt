package com.fishking.foldshift.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Persisted user configuration. Today we store:
 *  - which package to use as the inner / outer launcher
 *  - whether the controller is enabled
 *  - whether the user has acknowledged battery-optimisation guidance
 *
 * Storage lives in a single Preferences DataStore. We keep the surface tiny
 * so Phase 4 (the controller) and Phase 7 (the settings UI) can both depend
 * on the same source of truth.
 */
class ConfigRepository(private val context: Context) {

    val outerPackage: Flow<String> = context.dataStore.data.map { it[KEY_OUTER] ?: DEFAULT_OUTER }
    val innerPackage: Flow<String> = context.dataStore.data.map { it[KEY_INNER] ?: DEFAULT_INNER }
    val enabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ENABLED] ?: false }
    val batteryPromptAcknowledged: Flow<Boolean> =
        context.dataStore.data.map { it[KEY_BATTERY_ACK] ?: false }

    suspend fun setOuterPackage(pkg: String) {
        context.dataStore.edit { it[KEY_OUTER] = pkg.trim() }
    }

    suspend fun setInnerPackage(pkg: String) {
        context.dataStore.edit { it[KEY_INNER] = pkg.trim() }
    }

    suspend fun setEnabled(value: Boolean) {
        context.dataStore.edit { it[KEY_ENABLED] = value }
    }

    suspend fun acknowledgeBatteryPrompt() {
        context.dataStore.edit { it[KEY_BATTERY_ACK] = true }
    }

    companion object {
        private const val DEFAULT_OUTER = "bitpit.launcher"
        private const val DEFAULT_INNER = "com.sec.android.app.launcher"

        private val KEY_OUTER: Preferences.Key<String> = stringPreferencesKey("outer_package")
        private val KEY_INNER: Preferences.Key<String> = stringPreferencesKey("inner_package")
        private val KEY_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("enabled")
        private val KEY_BATTERY_ACK: Preferences.Key<Boolean> = booleanPreferencesKey("battery_ack")

        private val Context.dataStore by preferencesDataStore(name = "foldshift_config")
    }
}