package com.danny.dannyapp

import org.junit.Test

import org.junit.Assert.*

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun testCountryHomeNations() {
        assertEquals("Nordirland", com.danny.dannyapp.data.Country.getDisplayName("NIR"))
        assertEquals(R.drawable.ic_flag_nir, com.danny.dannyapp.data.Country.getFlagResId("NIR"))
        assertEquals("England", com.danny.dannyapp.data.Country.getDisplayName("ENG"))
        assertNull(com.danny.dannyapp.data.Country.getFlagResId("ENG"))
    }
}