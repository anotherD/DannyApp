package com.danny.dannyapp.domain

import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.TourCard
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TourCardWithPlayer
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.USER_ID
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GenerateBracketUseCaseTest {

    private lateinit var careerDao: FakeCareerDao
    private lateinit var seasonDao: FakeSeasonDao
    private lateinit var tournamentDao: FakeTournamentDao
    private lateinit var tourCardDao: FakeTourCardDao
    private lateinit var playerDao: FakePlayerDao
    private lateinit var tournamentMatchDao: FakeTournamentMatchDao
    private lateinit var placementDao: FakeTournamentPlacementDao
    private lateinit var getOrderOfMeritUseCase: GetOrderOfMeritUseCase
    private lateinit var generateBracketUseCase: GenerateBracketUseCase

    @Before
    fun setUp() {
        careerDao = FakeCareerDao()
        seasonDao = FakeSeasonDao()
        tournamentDao = FakeTournamentDao()
        tourCardDao = FakeTourCardDao()
        playerDao = FakePlayerDao()
        tournamentMatchDao = FakeTournamentMatchDao()
        placementDao = FakeTournamentPlacementDao()

        getOrderOfMeritUseCase = GetOrderOfMeritUseCase(
            careerDao, seasonDao, tournamentDao, placementDao, playerDao
        )

        generateBracketUseCase = GenerateBracketUseCase(
            tournamentDao, seasonDao, tourCardDao, playerDao, tournamentMatchDao, getOrderOfMeritUseCase
        )

        // Setup 1 Career & Season
        careerDao.career = CareerWithProfile(
            career = Career(id = 1L, name = "My Career", level = 5, season = 1),
            profileName = "Danny"
        )
        seasonDao.seasons = listOf(
            Season(id = 100L, careerId = 1L, seasonNumber = 1, isCompleted = false)
        )

        // Create 200 players (CPU)
        val cpuPlayers = (1..200).map { i ->
            Player(
                id = i.toLong(),
                name = "Player $i",
                skill = (i % 10) + 1,
                countryCode = if (i <= 20) "GER" else "ENG",
                prizeMoney = 0.0,
                tourCardYear = if (i <= 127) 1 else 0
            )
        }
        playerDao.players = cpuPlayers

        // Create 128 Tour Cards (1 User + 127 CPU)
        val tourCards = mutableListOf(
            TourCard(id = 1L, seasonId = 100L, playerId = USER_ID, tourCardYear = 2)
        )
        (1..127).forEach { i ->
            tourCards.add(TourCard(id = (i + 1).toLong(), seasonId = 100L, playerId = i.toLong(), tourCardYear = 1))
        }
        tourCardDao.tourCards = tourCards
    }

    @Test
    fun testGenerateBracketForPlayersChampionship() = runBlocking {
        val pcTournament = Tournament(
            id = 10L,
            seasonId = 100L,
            name = "Players Championship 1",
            type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name,
            order = 1
        )
        tournamentDao.tournaments = listOf(pcTournament)

        val matches = generateBracketUseCase(10L)

        // 128 players -> 64 matches for Round L128
        assertEquals(64, matches.size)
        assertTrue(matches.all { it.round == TournamentRound.L128.name })

        val participants = matches.flatMap { listOf(it.player1Id, it.player2Id) }.toSet()
        assertEquals(128, participants.size)
        assertTrue(USER_ID in participants)
    }

    @Test
    fun testGenerateBracketForEuropeanTour() = runBlocking {
        val etTournament = Tournament(
            id = 20L,
            seasonId = 100L,
            name = "European Tour 1",
            type = TournamentEvent.EUROPEAN_TOUR.name,
            order = 3
        )
        tournamentDao.tournaments = listOf(etTournament)

        val matches = generateBracketUseCase(20L)

        // ET Round 1 has 16 matches for Round L48
        assertEquals(16, matches.size)
        assertTrue(matches.all { it.round == TournamentRound.L48.name })

        val R1Participants = matches.flatMap { listOf(it.player1Id, it.player2Id) }.toSet()
        assertEquals(32, R1Participants.size)
    }

    @Test
    fun testGenerateBracketForUkOpen() = runBlocking {
        val ukOpenTournament = Tournament(
            id = 30L,
            seasonId = 100L,
            name = "UK Open",
            type = TournamentEvent.UK_OPEN.name,
            order = 7
        )
        tournamentDao.tournaments = listOf(ukOpenTournament)

        val matches = generateBracketUseCase(30L)

        // UK Open Round 1 has 32 matches for Round R1_160 (64 players: 32 OOM 97..128 + 32 qualifiers)
        assertEquals(32, matches.size)
        assertTrue(matches.all { it.round == TournamentRound.R1_160.name })

        val R1Participants = matches.flatMap { listOf(it.player1Id, it.player2Id) }.toSet()
        assertEquals(64, R1Participants.size)
    }

    @Test
    fun testGenerateBracketForWorldMatchplay() = runBlocking {
        val wmTournament = Tournament(
            id = 40L,
            seasonId = 100L,
            name = "World Matchplay",
            type = TournamentEvent.WORLD_MATCHPLAY.name,
            order = 27
        )
        tournamentDao.tournaments = listOf(wmTournament)

        val matches = generateBracketUseCase(40L)

        // World Matchplay Round 1 has 16 matches for Round L32 (32 players)
        assertEquals(16, matches.size)
        assertTrue(matches.all { it.round == TournamentRound.L32.name })

        val participants = matches.flatMap { listOf(it.player1Id, it.player2Id) }.toSet()
        assertEquals(32, participants.size)
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

    private class FakeTourCardDao : TourCardDao {
        var tourCards = listOf<TourCard>()

        override fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>> = flowOf(emptyList())
        override suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer> =
            tourCards.filter { it.seasonId == seasonId }.map { TourCardWithPlayer(tourCard = it, player = null) }
        override suspend fun getRawTourCardsForSeason(seasonId: Long): List<TourCard> = tourCards.filter { it.seasonId == seasonId }
        override suspend fun insertTourCard(tourCard: TourCard): Long = 1L
        override suspend fun insertTourCards(tourCards: List<TourCard>) {}
        override suspend fun deleteTourCardsForSeason(seasonId: Long) {}
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

    private class FakeTournamentMatchDao : TournamentMatchDao {
        private val matches = mutableListOf<TournamentMatch>()
        private var idCounter = 1L

        override fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> =
            flowOf(matches.filter { it.tournamentId == tournamentId })

        override suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> =
            matches.filter { it.tournamentId == tournamentId }

        override fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> =
            flowOf(matches)

        override suspend fun getMatchById(id: Long): TournamentMatch? = matches.find { it.id == id }

        override suspend fun insertMatch(match: TournamentMatch): Long {
            val assignedId = if (match.id == 0L) idCounter++ else match.id
            val item = match.copy(id = assignedId)
            matches.add(item)
            return assignedId
        }

        override suspend fun insertMatches(matches: List<TournamentMatch>) {
            matches.forEach { insertMatch(it) }
        }

        override suspend fun updateMatch(match: TournamentMatch) {}
        override suspend fun deleteMatch(match: TournamentMatch) {}
        override suspend fun deleteMatchesForTournament(tournamentId: Long) {
            matches.removeIf { it.tournamentId == tournamentId }
        }
    }
}
