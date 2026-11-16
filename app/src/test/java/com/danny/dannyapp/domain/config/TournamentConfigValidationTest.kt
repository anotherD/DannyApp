package com.danny.dannyapp.domain.config

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class TournamentConfigValidationTest {

    @Before
    fun setUp() {
        TournamentConfigLoader.clearCache()
    }

    @Test
    fun validateSeasonCalendar2027Json() {
        val file = File("src/main/assets/season_calendar_2027.json").let {
            if (it.exists()) it else File("app/src/main/assets/season_calendar_2027.json")
        }
        assertTrue("season_calendar_2027.json muss existieren", file.exists())

        val config = FileInputStream(file).use { inputStream ->
            TournamentConfigLoader.loadFromStream(inputStream)
        }

        assertNotNull(config)
        assertTrue("Kalendereinträge dürfen nicht leer sein", config.calendar.isNotEmpty())
    }

    @Test
    fun invalidJsonThrowsIllegalStateException() {
        val invalidJson = """
            {
              "seasonYear": 2027,
              "source": "Invalid Config",
              "events": {
                "PLAYERS_CHAMPIONSHIP": {
                  "totalPrizeFund": 999999.0,
                  "prizes": {
                    "WINNER": 15000,
                    "F": 10000,
                    "SF": 6500,
                    "QF": 4000,
                    "L16": 3000,
                    "L32": 2000,
                    "L64": 1250,
                    "L128": 0
                  },
                  "distances": {
                    "L128": { "bestOf": 11, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "L64": { "bestOf": 11, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "L32": { "bestOf": 11, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "L16": { "bestOf": 11, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "QF": { "bestOf": 11, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "SF": { "bestOf": 13, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false },
                    "F": { "bestOf": 15, "format": "LEGS", "legsPerSet": 1, "twoClearLegsRule": false }
                  }
                }
              },
              "calendar": []
            }
        """.trimIndent()

        assertThrows(IllegalStateException::class.java) {
            TournamentConfigLoader.parse(invalidJson)
        }
    }
}
