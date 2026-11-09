package com.danny.dannyapp.data

import com.danny.dannyapp.data.db.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerTest {

    @Test
    fun testPlayerCreationWithUpdatedAt() {
        val before = System.currentTimeMillis()
        val player = Player(
            name = "Luke Littler",
            skill = 9,
            countryCode = "ENG",
            prizeMoney = 500.0,
            tourCardYear = 1
        )
        val after = System.currentTimeMillis()

        assertEquals("Luke Littler", player.name)
        assertEquals(9, player.skill)
        assertEquals("ENG", player.countryCode)
        assertTrue(player.hasTourCard)
        assertTrue(player.updatedAt in before..after)
    }
}
