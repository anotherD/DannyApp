package com.danny.dannyapp.data

import com.danny.dannyapp.data.db.UserProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UserProfileTest {

    @Test
    fun testUserProfileCreation() {
        val profile = UserProfile(name = "Test User", description = "A test player")
        assertEquals("Test User", profile.name)
        assertEquals("A test player", profile.description)
    }

    @Test
    fun testUserProfileEmptyNameThrows() {
        assertThrows(IllegalArgumentException::class.java) {
            UserProfile(name = "", description = "Empty name")
        }
        assertThrows(IllegalArgumentException::class.java) {
            UserProfile(name = "   ", description = "Blank name")
        }
    }
}
