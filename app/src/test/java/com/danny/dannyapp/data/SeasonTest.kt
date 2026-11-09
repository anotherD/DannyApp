package com.danny.dannyapp.data

import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TourCard
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TourCardWithPlayer
import com.danny.dannyapp.data.db.USER_ID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SeasonTest {

    @Test
    fun testSeasonValidation() {
        val season = Season(careerId = 1, seasonNumber = 1)
        assertEquals(1, season.seasonNumber)
        assertFalse(season.isCompleted)

        assertThrows(IllegalArgumentException::class.java) {
            Season(careerId = 1, seasonNumber = 0)
        }
    }

    @Test
    fun testTourCardUserProperty() {
        val userTourCard = TourCard(seasonId = 1, playerId = USER_ID, tourCardYear = 2)
        assertTrue(userTourCard.isUser)
        assertEquals(USER_ID, userTourCard.playerId)

        val cpuTourCard = TourCard(seasonId = 1, playerId = 10L, tourCardYear = 1)
        assertFalse(cpuTourCard.isUser)
        assertEquals(10L, cpuTourCard.playerId)

        assertThrows(IllegalArgumentException::class.java) {
            TourCard(seasonId = 1, playerId = 1L, tourCardYear = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            TourCard(seasonId = 1, playerId = 1L, tourCardYear = 3)
        }
    }

    @Test
    fun testSeasonCreation_TruncateCpuCandidatesWhenOver127() = runBlocking {
        val fakePlayerDao = FakePlayerDao()
        val fakeSeasonDao = FakeSeasonDao()
        val fakeTourCardDao = FakeTourCardDao()

        // Create 140 CPU players with tour cards
        val players = (1..140).map { i ->
            Player(
                id = i.toLong(),
                name = "Player $i",
                skill = (i % 10) + 1,
                countryCode = "GER",
                prizeMoney = i * 10.0,
                tourCardYear = 1
            )
        }
        fakePlayerDao.setPlayers(players)

        val repository = SeasonRepository(fakeSeasonDao, fakeTourCardDao, fakePlayerDao)
        val seasonId = repository.createSeason(careerId = 1L)

        val createdCards = fakeTourCardDao.savedCards
        // 1 User + 127 CPU = 128 Total
        assertEquals(128, createdCards.size)
        assertEquals(1, createdCards.count { it.isUser })
        assertEquals(127, createdCards.count { !it.isUser })
    }

    @Test
    fun testSeasonCreation_BackfillCpuCandidatesWhenUnder127() = runBlocking {
        val fakePlayerDao = FakePlayerDao()
        val fakeSeasonDao = FakeSeasonDao()
        val fakeTourCardDao = FakeTourCardDao()

        // Create 80 tour card players + 60 non-tour card players = 140 total
        val tourCardPlayers = (1..80).map { i ->
            Player(
                id = i.toLong(),
                name = "Tour Player $i",
                skill = 8,
                countryCode = "ENG",
                prizeMoney = 100.0,
                tourCardYear = 1
            )
        }
        val nonTourCardPlayers = (81..140).map { i ->
            Player(
                id = i.toLong(),
                name = "Non-Tour Player $i",
                skill = i % 10 + 1,
                countryCode = "NED",
                prizeMoney = 0.0,
                tourCardYear = 0
            )
        }
        fakePlayerDao.setPlayers(tourCardPlayers + nonTourCardPlayers)

        val repository = SeasonRepository(fakeSeasonDao, fakeTourCardDao, fakePlayerDao)
        val seasonId = repository.createSeason(careerId = 1L)

        val createdCards = fakeTourCardDao.savedCards
        // 1 User + 127 CPU = 128 Total
        assertEquals(128, createdCards.size)
        assertEquals(1, createdCards.count { it.isUser })
        assertEquals(127, createdCards.count { !it.isUser })

        // 80 initial tour card players + 47 backfilled non-tour card players = 127 CPU
        val backfilledCards = createdCards.filter { card ->
            card.playerId in 81L..140L
        }
        assertEquals(47, backfilledCards.size)
        assertTrue(backfilledCards.all { it.tourCardYear == 2 })
    }

    // --- Fake DAOs for Unit Testing ---

    private class FakePlayerDao : PlayerDao {
        private var playersList = listOf<Player>()

        fun setPlayers(players: List<Player>) {
            playersList = players
        }

        override fun getAllPlayers(): Flow<List<Player>> = flowOf(playersList)
        override fun getAllPlayersSortedBySkill(): Flow<List<Player>> = flowOf(playersList)
        override fun getAllPlayersSortedByName(): Flow<List<Player>> = flowOf(playersList)
        override suspend fun getAllPlayersList(): List<Player> = playersList
        override suspend fun getPlayerById(id: Long): Player? = playersList.find { it.id == id }
        override suspend fun getPlayerCount(): Int = playersList.size
        override suspend fun insertPlayer(player: Player): Long = player.id
        override suspend fun insertAll(players: List<Player>) {}
        override suspend fun updatePlayer(player: Player) {}
        override suspend fun deletePlayer(player: Player) {}
        override suspend fun deletePlayerById(id: Long) {}
        override suspend fun csvInsertNewPlayers(players: List<Player>) {}
        override suspend fun csvUpdatePlayerStats(name: String, skill: Int, countryCode: String, prizeMoney: Double, tourCardYear: Int, updatedAt: Long) {}
    }

    private class FakeSeasonDao : SeasonDao {
        private val seasons = mutableListOf<Season>()
        private var idCounter = 1L

        override fun getSeasonsForCareer(careerId: Long): Flow<List<Season>> =
            flowOf(seasons.filter { it.careerId == careerId })

        override suspend fun getCurrentSeasonForCareer(careerId: Long): Season? =
            seasons.filter { it.careerId == careerId }.maxByOrNull { it.seasonNumber }

        override suspend fun getSeasonById(id: Long): Season? = seasons.find { it.id == id }

        override suspend fun insertSeason(season: Season): Long {
            val assignedId = if (season.id == 0L) idCounter++ else season.id
            val seasonToSave = season.copy(id = assignedId)
            seasons.add(seasonToSave)
            return assignedId
        }

        override suspend fun updateSeason(season: Season) {
            val index = seasons.indexOfFirst { it.id == season.id }
            if (index >= 0) seasons[index] = season
        }

        override suspend fun deleteSeason(season: Season) {
            seasons.removeIf { it.id == season.id }
        }

        override suspend fun deleteSeasonById(id: Long) {
            seasons.removeIf { it.id == id }
        }
    }

    private class FakeTourCardDao : TourCardDao {
        val savedCards = mutableListOf<TourCard>()
        private var idCounter = 1L

        override fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>> = flowOf(emptyList())
        override suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer> = emptyList()
        override suspend fun getRawTourCardsForSeason(seasonId: Long): List<TourCard> =
            savedCards.filter { it.seasonId == seasonId }

        override suspend fun insertTourCard(tourCard: TourCard): Long {
            val assignedId = if (tourCard.id == 0L) idCounter++ else tourCard.id
            val cardToSave = tourCard.copy(id = assignedId)
            savedCards.add(cardToSave)
            return assignedId
        }

        override suspend fun insertTourCards(tourCards: List<TourCard>) {
            tourCards.forEach { insertTourCard(it) }
        }

        override suspend fun deleteTourCardsForSeason(seasonId: Long) {
            savedCards.removeIf { it.seasonId == seasonId }
        }
    }
}
