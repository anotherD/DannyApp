package com.danny.dannyapp.data

import com.danny.dannyapp.data.db.Career
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CareerTest {

    @Test
    fun testCareerDefaultValues() {
        val career = Career(name = "Test Career", level = 1)
        assertEquals(1, career.season)
        assertEquals(1, career.level)
    }

    @Test
    fun testCareerValidLevels() {
        val minCareer = Career(name = "Min Level", level = Career.MIN_LEVEL)
        assertEquals(Career.MIN_LEVEL, minCareer.level)

        val maxCareer = Career(name = "Max Level", level = Career.MAX_LEVEL)
        assertEquals(Career.MAX_LEVEL, maxCareer.level)
    }

    @Test
    fun testCareerInvalidLevels() {
        assertThrows(IllegalArgumentException::class.java) {
            Career(name = "Invalid Low", level = 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            Career(name = "Invalid High", level = 26)
        }
    }

    @Test
    fun testCareerSeasonValidation() {
        val customSeasonCareer = Career(name = "Custom Season", level = 5, season = 500)
        assertEquals(500, customSeasonCareer.season)

        assertThrows(IllegalArgumentException::class.java) {
            Career(name = "Invalid Season", level = 5, season = 0)
        }

        assertThrows(IllegalArgumentException::class.java) {
            Career(name = "Invalid Season Neg", level = 5, season = -1)
        }
    }
}
