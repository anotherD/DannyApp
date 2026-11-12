package com.danny.dannyapp.domain

import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class GetOrderOfMeritUseCaseTest {

    @Test
    fun testGetOrderOfMeritCalculations() = runBlocking {
        val careerDao = FakeCareerDao()
        val seasonDao = FakeSeasonDao()
        val tournamentDao = FakeTournamentDao()
        val placementDao = FakeTournamentPlacementDao()
        val playerDao = FakePlayerDao()

        // Career & Season 1, Season 2
        careerDao.career = CareerWithProfile(
            career = Career(id = 1L, name = "My Career", level = 5, season = 2),
            profileName = "Danny"
        )
        seasonDao.seasons = listOf(
            Season(id = 101L, careerId = 1L, seasonNumber = 1, isCompleted = true),
            Season(id = 102L, careerId = 1L, seasonNumber = 2, isCompleted = false)
        )

        // Tournaments:
        // PC 1 in Season 1 (id 1) and Season 2 (id 2)
        // ET 1 in Season 1 (id 3) and Season 2 (id 4)
        val tournaments = listOf(
            Tournament(id = 1L, seasonId = 101L, name = "Players Championship 1", type = "PLAYERS_CHAMPIONSHIP", order = 1),
            Tournament(id = 2L, seasonId = 102L, name = "Players Championship 1", type = "PLAYERS_CHAMPIONSHIP", order = 1),
            Tournament(id = 3L, seasonId = 101L, name = "European Tour 1", type = "EUROPEAN_TOUR", order = 1),
            Tournament(id = 4L, seasonId = 102L, name = "European Tour 1", type = "EUROPEAN_TOUR", order = 1)
        )
        tournamentDao.tournaments = tournaments

        // Placements:
        // Player 1 (CPU): £1000 in S1 PC1, £2000 in S2 PC1, £500 in S1 ET1, £1500 in S2 ET1
        // User (USER_ID): £500 in S1 PC1, £3000 in S2 PC1, £1000 in S2 ET1
        val placements = listOf(
            TournamentPlacement(id = 1L, tournamentId = 1L, playerId = 1L, reachedRound = "WINNER", prizeMoney = 1000.0),
            TournamentPlacement(id = 2L, tournamentId = 2L, playerId = 1L, reachedRound = "WINNER", prizeMoney = 2000.0),
            TournamentPlacement(id = 3L, tournamentId = 3L, playerId = 1L, reachedRound = "SF", prizeMoney = 500.0),
            TournamentPlacement(id = 4L, tournamentId = 4L, playerId = 1L, reachedRound = "F", prizeMoney = 1500.0),

            TournamentPlacement(id = 5L, tournamentId = 1L, playerId = USER_ID, reachedRound = "SF", prizeMoney = 500.0),
            TournamentPlacement(id = 6L, tournamentId = 2L, playerId = USER_ID, reachedRound = "WINNER", prizeMoney = 3000.0),
            TournamentPlacement(id = 7L, tournamentId = 4L, playerId = USER_ID, reachedRound = "WINNER", prizeMoney = 1000.0)
        )
        placementDao.placements = placements

        val players = listOf(
            Player(id = 1L, name = "Gerwyn Price", skill = 9, countryCode = "WAL", prizeMoney = 0.0, tourCardYear = 2)
        )
        playerDao.players = players

        val useCase = GetOrderOfMeritUseCase(careerDao, seasonDao, tournamentDao, placementDao, playerDao)

        // 1. OOM: Last 2 editions per event (saisonübergreifend). Since there are 2 editions (S1 and S2), both editions are taken.
        // Player 1: 1000 + 2000 + 500 + 1500 = 5000.0
        // User (-1L): 500 + 3000 + 1000 = 4500.0
        val oomResult = useCase(1L, OrderOfMeritType.OOM)
        assertEquals(2, oomResult.size)
        assertEquals("Gerwyn Price", oomResult[0].name)
        assertEquals(5000.0, oomResult[0].prizeMoney, 0.01)
        assertEquals(1, oomResult[0].rank)
        assertEquals("Danny", oomResult[1].name)
        assertEquals(4500.0, oomResult[1].prizeMoney, 0.01)
        assertEquals(2, oomResult[1].rank)

        // 2. PT_OOM: Sum from PC and ET, only youngest edition per event. Youngest edition is S2 (id 2 and 4).
        // Player 1 in S2 PC1: 2000.0, S2 ET1: 1500.0 -> Total: 3500.0
        // User in S2 PC1: 3000.0, S2 ET1: 1000.0 -> Total: 4000.0
        val ptOomResult = useCase(1L, OrderOfMeritType.PT_OOM)
        assertEquals(2, ptOomResult.size)
        assertEquals("Danny", ptOomResult[0].name)
        assertEquals(4000.0, ptOomResult[0].prizeMoney, 0.01)
        assertEquals(1, ptOomResult[0].rank)
        assertEquals("Gerwyn Price", ptOomResult[1].name)
        assertEquals(3500.0, ptOomResult[1].prizeMoney, 0.01)
        assertEquals(2, ptOomResult[1].rank)

        // 3. PC_OOM: Only Players Championship of current season (Season 2, tournament id 2).
        // Player 1: 2000.0
        // User: 3000.0
        val pcOomResult = useCase(1L, OrderOfMeritType.PC_OOM)
        assertEquals(2, pcOomResult.size)
        assertEquals("Danny", pcOomResult[0].name)
        assertEquals(3000.0, pcOomResult[0].prizeMoney, 0.01)
        assertEquals("Gerwyn Price", pcOomResult[1].name)
        assertEquals(2000.0, pcOomResult[1].prizeMoney, 0.01)

        // 4. ET_OOM: Only European Tour of current season (Season 2, tournament id 4).
        // Player 1: 1500.0
        // User: 1000.0
        val etOomResult = useCase(1L, OrderOfMeritType.ET_OOM)
        assertEquals(2, etOomResult.size)
        assertEquals("Gerwyn Price", etOomResult[0].name)
        assertEquals(1500.0, etOomResult[0].prizeMoney, 0.01)
        assertEquals("Danny", etOomResult[1].name)
        assertEquals(1000.0, etOomResult[1].prizeMoney, 0.01)
    }

    // --- Fakes for Testing ---

    private class FakeCareerDao : CareerDao {
        var career: CareerWithProfile? = null

        override fun getAllCareers(): Flow<List<CareerWithProfile>> = flowOf(emptyList())
        override suspend fun getAllCareersList(): List<CareerWithProfile> = career?.let { listOf(it) } ?: emptyList()
        override suspend fun getCareerById(id: Long): CareerWithProfile? = career
        override suspend fun getCareerCountForProfile(profileId: Long): Int = 0
        override suspend fun insertCareer(career: Career): Long = 1L
        override suspend fun deleteCareerById(id: Long) {}
        override suspend fun deleteCareer(career: Career) {}
    }

    private class FakeSeasonDao : SeasonDao {
        var seasons = listOf<Season>()

        override fun getSeasonsForCareer(careerId: Long): Flow<List<Season>> = flowOf(seasons)
        override suspend fun getSeasonsListForCareer(careerId: Long): List<Season> = seasons
        override suspend fun getCurrentSeasonForCareer(careerId: Long): Season? = seasons.maxByOrNull { it.seasonNumber }
        override suspend fun getSeasonById(id: Long): Season? = seasons.find { it.id == id }
        override suspend fun insertSeason(season: Season): Long = 1L
        override suspend fun updateSeason(season: Season) {}
        override suspend fun deleteSeason(season: Season) {}
        override suspend fun deleteSeasonById(id: Long) {}
    }

    private class FakeTournamentDao : TournamentDao {
        var tournaments = listOf<Tournament>()

        override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> = flowOf(tournaments.filter { it.seasonId == seasonId })
        override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = tournaments.filter { it.seasonId == seasonId }
        override suspend fun getTournamentById(id: Long): Tournament? = tournaments.find { it.id == id }
        override suspend fun insertTournament(tournament: Tournament): Long = 1L
        override suspend fun insertTournaments(tournaments: List<Tournament>) {}
        override suspend fun updateTournament(tournament: Tournament) {}
        override suspend fun deleteTournament(tournament: Tournament) {}
        override suspend fun deleteTournamentById(id: Long) {}
        override suspend fun deleteTournamentsForSeason(seasonId: Long) {}
    }

    private class FakeTournamentPlacementDao : TournamentPlacementDao {
        var placements = listOf<TournamentPlacement>()

        override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> = flowOf(placements.filter { it.tournamentId == tournamentId })
        override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = placements.filter { it.tournamentId == tournamentId }
        override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> = flowOf(placements.filter { it.playerId == playerId })
        override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> = placements
        override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> = flowOf(placements)
        override suspend fun insertPlacement(placement: TournamentPlacement): Long = 1L
        override suspend fun insertPlacements(placements: List<TournamentPlacement>) {}
        override suspend fun updatePlacement(placement: TournamentPlacement) {}
        override suspend fun deletePlacement(placement: TournamentPlacement) {}
        override suspend fun deletePlacementsForTournament(tournamentId: Long) {}
    }

    private class FakePlayerDao : PlayerDao {
        var players = listOf<Player>()

        override fun getAllPlayers(): Flow<List<Player>> = flowOf(players)
        override suspend fun getAllPlayersList(): List<Player> = players
        override fun getAllPlayersSortedBySkill(): Flow<List<Player>> = flowOf(players)
        override fun getAllPlayersSortedByName(): Flow<List<Player>> = flowOf(players)
        override suspend fun getPlayerById(id: Long): Player? = players.find { it.id == id }
        override suspend fun getPlayerCount(): Int = players.size
        override suspend fun insertPlayer(player: Player): Long = 1L
        override suspend fun insertAll(players: List<Player>) {}
        override suspend fun updatePlayer(player: Player) {}
        override suspend fun deletePlayer(player: Player) {}
        override suspend fun deletePlayerById(id: Long) {}
        override suspend fun csvInsertNewPlayers(players: List<Player>) {}
        override suspend fun csvUpdatePlayerStats(name: String, skill: Int, countryCode: String, prizeMoney: Double, tourCardYear: Int, updatedAt: Long) {}
    }
}
