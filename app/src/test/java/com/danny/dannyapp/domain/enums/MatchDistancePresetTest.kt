package com.danny.dannyapp.domain.enums

import com.danny.dannyapp.domain.model.MatchDistance
import org.junit.Assert.assertEquals
import org.junit.Test

class MatchDistancePresetTest {

    @Test
    fun testOriginalPresetReturnsUnchanged() {
        val base = MatchDistance(bestOf = 11, format = MatchFormatType.LEGS, legsPerSet = 1)
        val scaled = MatchDistancePreset.ORIGINAL.scaleDistance(base)
        assertEquals(base, scaled)
    }

    @Test
    fun testLegsScaling() {
        val base = MatchDistance(bestOf = 11, format = MatchFormatType.LEGS, legsPerSet = 1)
        // Shortened: 75% of 11 = 8.25 -> rounded = 8 -> even -> raw >= 8 -> 8 + 1 = 9
        assertEquals(9, MatchDistancePreset.SHORTENED.scaleDistance(base).bestOf)

        // Compact: 50% of 11 = 5.5 -> rounded = 6 -> even -> raw >= 6 -> 6 + 1 = 7 (Wait, 5.5 >= 6 is false, so 6 - 1 = 5)
        assertEquals(5, MatchDistancePreset.COMPACT.scaleDistance(base).bestOf)

        // Rapid: 25% of 11 = 2.75 -> rounded = 3 -> odd = 3 (min 3 -> 3)
        assertEquals(3, MatchDistancePreset.RAPID.scaleDistance(base).bestOf)
    }

    @Test
    fun testSetsScaling() {
        val baseSet = MatchDistance(bestOf = 5, format = MatchFormatType.SETS_BEST_OF_5_LEGS, legsPerSet = 5)
        // Shortened (75% of 5 = 3.75 -> rounded = 4 -> even -> 3.75 >= 4 is false -> 4 - 1 = 3, legsPerSet = 5)
        val shortened = MatchDistancePreset.SHORTENED.scaleDistance(baseSet)
        assertEquals(3, shortened.bestOf)
        assertEquals(5, shortened.legsPerSet)

        // Compact
        val compact = MatchDistancePreset.COMPACT.scaleDistance(baseSet)
        assertEquals(3, compact.bestOf)
        assertEquals(3, compact.legsPerSet)

        // Rapid (sets = 3, legsPerSet = 3)
        val rapid = MatchDistancePreset.RAPID.scaleDistance(baseSet)
        assertEquals(3, rapid.bestOf)
        assertEquals(3, rapid.legsPerSet)
    }

    @Test
    fun testFromName() {
        assertEquals(MatchDistancePreset.ORIGINAL, MatchDistancePreset.fromName("ORIGINAL"))
        assertEquals(MatchDistancePreset.SHORTENED, MatchDistancePreset.fromName("SHORTENED"))
        assertEquals(MatchDistancePreset.COMPACT, MatchDistancePreset.fromName("COMPACT"))
        assertEquals(MatchDistancePreset.RAPID, MatchDistancePreset.fromName("RAPID"))
    }
}
