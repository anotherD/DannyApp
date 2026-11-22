package com.danny.dannyapp.domain.enums

import androidx.annotation.StringRes
import com.danny.dannyapp.R
import com.danny.dannyapp.domain.model.MatchDistance
import kotlin.math.roundToInt

/**
 * Vordefinierte Voreinstellungen (Presets) für Match-Distanzen in Karrieren.
 * Ermöglicht das Verkürzen von Best-of-Legs oder Best-of-Sets Distanzen.
 */
enum class MatchDistancePreset(
    val percent: Int,
    @StringRes val labelResId: Int
) {
    ORIGINAL(100, R.string.match_distance_preset_original),
    SHORTENED(75, R.string.match_distance_preset_shortened),
    COMPACT(50, R.string.match_distance_preset_compact),
    RAPID(25, R.string.match_distance_preset_rapid);

    /**
     * Skaliert eine gegebene [base]-Match-Distanz basierend auf diesem Preset.
     */
    fun scaleDistance(base: MatchDistance): MatchDistance {
        if (this == ORIGINAL) return base

        return if (base.format == MatchFormatType.LEGS) {
            val scaledBestOf = roundToOddMin3(base.bestOf * (percent / 100.0))
            base.copy(bestOf = scaledBestOf)
        } else {
            val scaledSets: Int
            val scaledLegsPerSet: Int
            when (this) {
                SHORTENED -> {
                    scaledSets = roundToOddMin3(base.bestOf * 0.75)
                    scaledLegsPerSet = base.legsPerSet
                }
                COMPACT -> {
                    scaledLegsPerSet = if (base.bestOf >= 9) 5 else 3
                    scaledSets = if (base.bestOf == 7) 5 else roundToOddMin3(base.bestOf * 0.5)
                }
                RAPID -> {
                    scaledSets = 3
                    scaledLegsPerSet = 3
                }
                ORIGINAL -> {
                    scaledSets = base.bestOf
                    scaledLegsPerSet = base.legsPerSet
                }
            }
            base.copy(bestOf = scaledSets, legsPerSet = scaledLegsPerSet)
        }
    }

    companion object {
        val DEFAULT = ORIGINAL

        /**
         * Liefert das [MatchDistancePreset] zum angegebenen Namen (Fail-Fast über [valueOf]).
         */
        fun fromName(name: String): MatchDistancePreset {
            return valueOf(name)
        }

        private fun roundToOddMin3(raw: Double): Int {
            val rounded = raw.roundToInt()
            val odd = if (rounded % 2 == 0) {
                if (raw >= rounded) rounded + 1 else rounded - 1
            } else {
                rounded
            }
            return odd.coerceAtLeast(3)
        }
    }
}
