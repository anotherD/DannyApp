package com.danny.dannyapp.data

import com.danny.dannyapp.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CountryTest {

    @Test
    fun testCountryHomeNations() {
        assertEquals("Nordirland", Country.getDisplayName("NIR"))
        assertEquals(R.drawable.ic_flag_nir, Country.getFlagResId("NIR"))
        assertEquals("England", Country.getDisplayName("ENG"))
        assertNull(Country.getFlagResId("ENG"))
    }
}
