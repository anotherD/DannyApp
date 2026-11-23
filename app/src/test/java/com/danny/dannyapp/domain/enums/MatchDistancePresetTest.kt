package com.danny.dannyapp.domain.enums

import org.junit.Assert.assertEquals
import org.junit.Test

class MatchDistancePresetTest {

    @Test
    fun testFromName() {
        assertEquals(MatchDistancePreset.ORIGINAL, MatchDistancePreset.fromName("ORIGINAL"))
        assertEquals(MatchDistancePreset.SHORTENED, MatchDistancePreset.fromName("SHORTENED"))
        assertEquals(MatchDistancePreset.COMPACT, MatchDistancePreset.fromName("COMPACT"))
        assertEquals(MatchDistancePreset.RAPID, MatchDistancePreset.fromName("RAPID"))
    }
}
