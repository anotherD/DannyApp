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
        get() = prefs.getBoolean(KEY_AUTO_SIMULATE_CPU, false)
        set(value) { prefs.edit { putBoolean(KEY_AUTO_SIMULATE_CPU, value) } }

    var autoAdvanceRound: Boolean
        get() = prefs.getBoolean(KEY_AUTO_ADVANCE_ROUND, false)
        set(value) { prefs.edit { putBoolean(KEY_AUTO_ADVANCE_ROUND, value) } }

    var isBracketViewEnabled: Boolean
        get() = prefs.getBoolean(KEY_IS_BRACKET_VIEW_ENABLED, false)
        set(value) { prefs.edit { putBoolean(KEY_IS_BRACKET_VIEW_ENABLED, value) } }

    var useModernMatchCardStyle: Boolean
        get() = prefs.getBoolean(KEY_USE_MODERN_MATCH_CARD_STYLE, true)
        set(value) { prefs.edit { putBoolean(KEY_USE_MODERN_MATCH_CARD_STYLE, value) } }

    companion object {
        private const val PREFS_NAME = "danny_app_settings"
        private const val KEY_DEBUG_MODE = "key_debug_mode"
        private const val KEY_AUTO_SIMULATE_CPU = "key_auto_simulate_cpu"
        private const val KEY_AUTO_ADVANCE_ROUND = "key_auto_advance_round"
        private const val KEY_IS_BRACKET_VIEW_ENABLED = "key_is_bracket_view_enabled"
        private const val KEY_USE_MODERN_MATCH_CARD_STYLE = "key_use_modern_match_card_style"
    }
}
