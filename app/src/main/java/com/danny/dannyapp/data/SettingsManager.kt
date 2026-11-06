package com.danny.dannyapp.data

import android.content.Context
import android.content.SharedPreferences

class SettingsManager(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var config1Checked: Boolean
        get() = prefs.getBoolean(KEY_CONFIG_1, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONFIG_1, value).apply()
        }

    var config2Checked: Boolean
        get() = prefs.getBoolean(KEY_CONFIG_2, false)
        set(value) {
            prefs.edit().putBoolean(KEY_CONFIG_2, value).apply()
        }

    fun saveConfigs(config1: Boolean, config2: Boolean) {
        prefs.edit()
            .putBoolean(KEY_CONFIG_1, config1)
            .putBoolean(KEY_CONFIG_2, config2)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "danny_app_settings"
        private const val KEY_CONFIG_1 = "key_config_1"
        private const val KEY_CONFIG_2 = "key_config_2"
    }
}
