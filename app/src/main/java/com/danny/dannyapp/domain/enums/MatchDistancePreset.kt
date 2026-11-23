package com.danny.dannyapp.domain.enums

import androidx.annotation.StringRes
import com.danny.dannyapp.R

/**
 * Vordefinierte Voreinstellungen (Presets) für Match-Distanzen in Karrieren.
 * Ermöglicht das Auswählen von Best-of-Legs oder Best-of-Sets Distanzen.
 */
enum class MatchDistancePreset(
    val percent: Int,
    @StringRes val labelResId: Int
) {
    ORIGINAL(100, R.string.match_distance_preset_original),
    SHORTENED(75, R.string.match_distance_preset_shortened),
    COMPACT(50, R.string.match_distance_preset_compact),
    RAPID(25, R.string.match_distance_preset_rapid);

    companion object {
        val DEFAULT = ORIGINAL

        /**
         * Liefert das [MatchDistancePreset] zum angegebenen Namen (Fail-Fast über [valueOf]).
         */
        fun fromName(name: String): MatchDistancePreset {
            return valueOf(name)
        }
    }
}
