package com.danny.dannyapp.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var debugMode: Boolean
        get() = prefs.getBoolean(KEY_DEBUG_MODE, true)
        set(value) { prefs.edit { putBoolean(KEY_DEBUG_MODE, value) } }

    var autoSimulateCpu: Boolean
        get() = prefs.getBoolean(KEY_AUTO_SIMULATE_CPU, true)
        set(value) { prefs.edit { putBoolean(KEY_AUTO_SIMULATE_CPU, value) } }

    var autoAdvanceRound: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ADVANCE_ROUND, true)
        set(value) { prefs.edit { putBoolean(KEY_AUTO_ADVANCE_ROUND, value) } }

    companion object {
        private const val PREFS_NAME = "danny_app_settings"
        private const val KEY_DEBUG_MODE = "key_debug_mode"
        private const val KEY_AUTO_SIMULATE_CPU = "key_auto_simulate_cpu"
        private const val KEY_AUTO_ADVANCE_ROUND = "key_auto_advance_round"
    }
}
