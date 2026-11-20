package com.danny.dannyapp.domain.config

import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.enums.TournamentEvent
import com.danny.dannyapp.domain.usecase.BracketGenerator
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import kotlinx.coroutines.flow.Flow
import org.json.JSONObject
import org.junit.Assert.assertEquals
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

    private fun loadValidJsonString(): String {
        val file = File("src/main/assets/season_calendar_2027.json").let {
            if (it.exists()) it else File("app/src/main/assets/season_calendar_2027.json")
        }
        return file.readText()
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

        val dummyDao = object : TournamentDao {
            override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> = throw NotImplementedError()
            override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = emptyList()
            override suspend fun getTournamentById(id: Long): Tournament? = null
            override suspend fun insertTournament(tournament: Tournament): Long = 1L
            override suspend fun insertTournaments(tournaments: List<Tournament>) {}
            override suspend fun updateTournament(tournament: Tournament) {}
            override suspend fun deleteTournament(tournament: Tournament) {}
            override suspend fun deleteTournamentById(id: Long) {}
            override suspend fun deleteTournamentsForSeason(seasonId: Long) {}
        }

        val calendarUseCase = GenerateSeasonCalendarUseCase(TournamentRepository(dummyDao))
        val tournaments = calendarUseCase.createSeasonCalendar(1L)
        assertTrue("Generierte Turniere dürfen nicht leer sein", tournaments.isNotEmpty())
        for (tournament in tournaments) {
            assertTrue("order muss > 0 sein", tournament.order > 0)
            assertTrue("name darf nicht leer sein", tournament.name.isNotBlank())
            assertTrue("type darf nicht leer sein", tournament.type.isNotBlank())
            assertTrue("hostCountry darf nicht leer sein", !tournament.hostCountry.isNullOrBlank())
            assertTrue("hostCity darf nicht leer sein", !tournament.hostCity.isNullOrBlank())
        }

        val dummyPlayerIds = (1L..256L).toList()
        val oomIds = dummyPlayerIds.take(128)
        val ptIds = dummyPlayerIds.drop(128).take(32)
        val qualIds = dummyPlayerIds.drop(160)

        for (event in TournamentEvent.entries) {
            val roundSequence = event.getRoundSequence()
            assertNotNull("RoundSequence darf nicht null sein für $event", roundSequence)
            assertTrue("RoundSequence darf nicht leer sein für $event", roundSequence.isNotEmpty())

            val prizeFund = event.effectiveTotalPrizeFund
            assertTrue("PrizeFund muss >= 0 sein für $event", prizeFund >= 0.0)

            for (round in roundSequence) {
                val distance = event.getMatchDistance(round)
                assertNotNull("MatchDistance für $round in $event darf nicht null sein", distance)
            }

            val pairings = BracketGenerator.generateInitialMatches(
                event = event,
                oomSortedIds = if (event.isQualifier) oomIds.take(8 * event.qualifierBoardCount) else oomIds,
                proTourSortedIds = ptIds,
                qualifierIds = qualIds
            )
            assertNotNull("Pairings dürfen nicht null sein für $event", pairings)

            val playerIdsInMatches = mutableListOf<Long>()
            for (pairing in pairings) {
                if (pairing.player1Id > 0L) {
                    playerIdsInMatches.add(pairing.player1Id)
                }
                if (pairing.player2Id > 0L) {
                    playerIdsInMatches.add(pairing.player2Id)
                }
                if (pairing.player1Id > 0L && pairing.player2Id > 0L) {
                    assertTrue("Player1 und Player2 dürfen nicht identisch sein", pairing.player1Id != pairing.player2Id)
                }
            }
            val uniquePlayers = playerIdsInMatches.toSet()
            assertEquals(
                "Kein Spieler darf in den Erstrunden-Matches von $event doppelt gepaart werden",
                playerIdsInMatches.size,
                uniquePlayers.size
            )
        }
    }

    @Test
    fun wrongPrizeFundThrowsIllegalStateException() {
        val root = JSONObject(loadValidJsonString())
        root.getJSONObject("events").getJSONObject("PLAYERS_CHAMPIONSHIP").put("totalPrizeFund", 999999.0)
        assertThrows(IllegalStateException::class.java) {
            TournamentConfigLoader.parse(root.toString())
        }
    }

    @Test
    fun missingRoundDistanceThrowsIllegalStateException() {
        val root = JSONObject(loadValidJsonString())
        root.getJSONObject("events").getJSONObject("PLAYERS_CHAMPIONSHIP").getJSONObject("distances").remove("F")
        assertThrows(IllegalStateException::class.java) {
            TournamentConfigLoader.parse(root.toString())
        }
    }

    @Test
    fun invalidHostCountryThrowsIllegalStateException() {
        val root = JSONObject(loadValidJsonString())
        root.getJSONArray("calendar").getJSONObject(0).put("hostCountry", "SUI")
        assertThrows(IllegalStateException::class.java) {
            TournamentConfigLoader.parse(root.toString())
        }
    }

    @Test
    fun invalidCalendarOrderThrowsIllegalStateException() {
        val root = JSONObject(loadValidJsonString())
        val calendarArray = root.getJSONArray("calendar")
        for (i in 0 until calendarArray.length()) {
            val entry = calendarArray.getJSONObject(i)
            if (entry.getString("type") == "EUROPEAN_CHAMPIONSHIP") {
                entry.put("order", 1)
            }
        }
        assertThrows(IllegalStateException::class.java) {
            TournamentConfigLoader.parse(root.toString())
        }
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
