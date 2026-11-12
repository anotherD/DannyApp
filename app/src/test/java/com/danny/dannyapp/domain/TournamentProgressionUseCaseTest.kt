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
import com.danny.dannyapp.domain.model.OrderOfMeritType
import com.danny.dannyapp.domain.model.TournamentEvent
import com.danny.dannyapp.domain.model.TournamentRound
import com.danny.dannyapp.domain.usecase.AdvanceTournamentRoundUseCase
import com.danny.dannyapp.domain.usecase.GenerateBracketUseCase
import com.danny.dannyapp.domain.usecase.GetOrderOfMeritUseCase
import com.danny.dannyapp.domain.usecase.SimulateCpuMatchUseCase
import com.danny.dannyapp.domain.usecase.TournamentProgressionUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TournamentProgressionUseCaseTest {

    private lateinit var careerDao: FakeCareerDao
    private lateinit var seasonDao: FakeSeasonDao
    private lateinit var tournamentDao: FakeTournamentDao
    private lateinit var tourCardDao: FakeTourCardDao
    private lateinit var playerDao: FakePlayerDao
    private lateinit var tournamentMatchDao: FakeTournamentMatchDao
    private lateinit var placementDao: FakeTournamentPlacementDao
    private lateinit var getOrderOfMeritUseCase: GetOrderOfMeritUseCase

    private lateinit var generateBracketUseCase: GenerateBracketUseCase
    private lateinit var simulateCpuMatchUseCase: SimulateCpuMatchUseCase
    private lateinit var advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase
    private lateinit var progressionUseCase: TournamentProgressionUseCase

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
        simulateCpuMatchUseCase = SimulateCpuMatchUseCase(
            tournamentMatchDao, tournamentDao, playerDao
        )
        advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(
            tournamentDao, tournamentMatchDao, placementDao, seasonDao, tourCardDao, playerDao, getOrderOfMeritUseCase
        )
        progressionUseCase = TournamentProgressionUseCase(
            simulateCpuMatchUseCase, advanceTournamentRoundUseCase
        )

        // Setup 1 Career & Season
        careerDao.career = CareerWithProfile(
            career = Career(id = 1L, name = "My Career", level = 5, season = 1),
            profileName = "Danny"
        )
        seasonDao.seasons = listOf(
            Season(id = 100L, careerId = 1L, seasonNumber = 1, isCompleted = false)
        )

        // Create 200 CPU players
        val cpuPlayers = (1..200).map { i ->
            Player(
                id = i.toLong(),
                name = "Player $i",
                skill = (i % 10) + 1,
                countryCode = if (i <= 20) "GER" else "ENG",
                prizeMoney = 0.0,
                tourCardYear = if (i <= 128) 1 else 0
            )
        }
        playerDao.players = cpuPlayers

        // Create 128 Tour Cards
        val tourCards = (1..128).map { i ->
            TourCard(id = i.toLong(), seasonId = 100L, playerId = i.toLong(), tourCardYear = 1)
        }
        tourCardDao.tourCards = tourCards
    }

    private suspend fun simulateAllMatchesIncludingUser(tournamentId: Long) {
        val matches = tournamentMatchDao.getMatchesListForTournament(tournamentId)
        for (match in matches) {
            if (!match.isCompleted) {
                if (match.player1Id == USER_ID || match.player2Id == USER_ID) {
                    val winnerId = if (match.player1Id == USER_ID) match.player1Id else match.player2Id
                    tournamentMatchDao.updateMatch(
                        match.copy(player1Score = 6, player2Score = 2, winnerId = winnerId, isCompleted = true)
                    )
                } else {
                    simulateCpuMatchUseCase(match)
                }
            }
        }
    }

    private suspend fun runTournamentToCompletion(tournamentId: Long) {
        while (true) {
            simulateAllMatchesIncludingUser(tournamentId)
            val advanced = advanceTournamentRoundUseCase(tournamentId)
            if (!advanced) break
        }
    }

    @Test
    fun testEuropeanTourCompleteSimulationAndRule45() = runBlocking {
        val etTournament = Tournament(
            id = 200L,
            seasonId = 100L,
            name = "European Tour 1",
            type = TournamentEvent.EUROPEAN_TOUR.name,
            order = 1
        )
        tournamentDao.tournaments.add(etTournament)

        // 1. Generate initial bracket -> 16 matches for L48
        val initialMatches = generateBracketUseCase(200L)
        assertEquals(16, initialMatches.size)
        assertTrue(initialMatches.all { it.round == TournamentRound.L48.name })

        // Pre-qualified seeds for ET: Top 16 OOM and Top 16 PT OOM
        val tourCardPlayerIds = tourCardDao.tourCards.map { it.playerId }
        val allPlayers = playerDao.getAllPlayersList()
        val oomEntries = getOrderOfMeritUseCase(1L, OrderOfMeritType.OOM)
        val ptOomEntries = getOrderOfMeritUseCase(1L, OrderOfMeritType.PT_OOM)

        // Helper sorting logic identical to GenerateBracketUseCase
        val playerMap = oomEntries.associateBy { it.playerId }
        val dbPlayerMap = allPlayers.associateBy { it.id }
        val sortedOomTc = tourCardPlayerIds.sortedWith(
            compareBy<Long> { playerMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { playerMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending { if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5 }
                .thenBy { if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: "" }
        )
        val top16Oom = sortedOomTc.take(16)

        val remainingTcAfterOom = tourCardPlayerIds.filterNot { it in top16Oom }
        val ptPlayerMap = ptOomEntries.associateBy { it.playerId }
        val sortedPtTc = remainingTcAfterOom.sortedWith(
            compareBy<Long> { ptPlayerMap[it]?.rank ?: Int.MAX_VALUE }
                .thenByDescending { ptPlayerMap[it]?.prizeMoney ?: 0.0 }
                .thenByDescending { if (it == USER_ID) 5 else dbPlayerMap[it]?.skill ?: 5 }
                .thenBy { if (it == USER_ID) "User" else dbPlayerMap[it]?.name ?: "" }
        )
        val top16PtOom = sortedPtTc.take(16)
        val preQualifiedEtSeeds = (top16Oom + top16PtOom).toSet()

        // Simulate L48 matches (including user match if present)
        simulateAllMatchesIncludingUser(200L)

        val l48CompletedMatches = tournamentMatchDao.getMatchesListForTournament(200L)
        assertTrue(l48CompletedMatches.all { it.isCompleted })

        // 2. Advance round to L32
        val advancedToL32 = advanceTournamentRoundUseCase(200L)
        assertTrue(advancedToL32)

        // Check L48 placements:
        val l48Placements = placementDao.placements.filter { it.reachedRound == TournamentRound.L48.name }
        assertEquals(16, l48Placements.size)

        for (placement in l48Placements) {
            val isPreQualified = placement.playerId in preQualifiedEtSeeds
            if (isPreQualified) {
                // ET Rule 4.5: Pre-qualified seed losing first match gets 0.0 prize money
                assertEquals(0.0, placement.prizeMoney, 0.001)
            } else {
                // Non-pre-qualified qualifier losing in L48 gets 2000.0
                assertEquals(2000.0, placement.prizeMoney, 0.001)
            }
        }

        // Check that L32 matches were created (16 matches)
        val l32Matches = tournamentMatchDao.getMatchesListForTournament(200L).filter { it.round == TournamentRound.L32.name }
        assertEquals(16, l32Matches.size)

        // Verify cross-over seeding in L32 (OOM #1 vs L48 winner match 15, OOM #16 vs L48 winner match 0)
        val l48Winners = l48CompletedMatches.sortedBy { it.matchIndex }.map { it.winnerId!! }
        val match0 = l32Matches.find { it.matchIndex == 0 }
        assertNotNull(match0)
        assertEquals(top16Oom[0], match0!!.player1Id)
        assertEquals(l48Winners[15], match0.player2Id)

        val match15 = l32Matches.find { it.matchIndex == 15 }
        assertNotNull(match15)
        assertEquals(top16Oom[15], match15!!.player1Id)
        assertEquals(l48Winners[0], match15.player2Id)

        // 3. Simulate remaining rounds to completion (L32 -> L16 -> QF -> SF -> F)
        runTournamentToCompletion(200L)

        val updatedTournament = tournamentDao.getTournamentById(200L)
        assertNotNull(updatedTournament)
        assertTrue(updatedTournament!!.isCompleted)

        // Verify winner placement
        val winnerPlacement = placementDao.placements.find { it.reachedRound == TournamentRound.WINNER.name }
        assertNotNull(winnerPlacement)
        assertEquals(35000.0, winnerPlacement!!.prizeMoney, 0.001)

        // Verify runner-up placement
        val runnerUpPlacement = placementDao.placements.find { it.reachedRound == TournamentRound.F.name }
        assertNotNull(runnerUpPlacement)
        assertEquals(15000.0, runnerUpPlacement!!.prizeMoney, 0.001)

        // Check L32 placements for Top 16 OOM seeds who lost their first match in L32
        val l32Placements = placementDao.placements.filter { it.reachedRound == TournamentRound.L32.name }
        assertEquals(16, l32Placements.size)

        for (placement in l32Placements) {
            val isTop16OomSeed = placement.playerId in top16Oom
            if (isTop16OomSeed) {
                // Top 16 OOM seed losing their first match in L32 -> 0.0 prize money (Rule 4.5)
                assertEquals(0.0, placement.prizeMoney, 0.001)
            } else {
                // Winner of L48 who lost in L32 -> 3500.0 prize money
                assertEquals(3500.0, placement.prizeMoney, 0.001)
            }
        }
    }

    @Test
    fun testPlayersChampionshipFullSimulation() = runBlocking {
        val pcTournament = Tournament(
            id = 300L,
            seasonId = 100L,
            name = "Players Championship 1",
            type = TournamentEvent.PLAYERS_CHAMPIONSHIP.name,
            order = 1
        )
        tournamentDao.tournaments.add(pcTournament)

        // Generate initial bracket -> 64 matches for L128
        generateBracketUseCase(300L)

        // Run complete tournament until finished
        runTournamentToCompletion(300L)

        val updatedTournament = tournamentDao.getTournamentById(300L)
        assertNotNull(updatedTournament)
        assertTrue(updatedTournament!!.isCompleted)

        // Verify winner placement
        val winnerPlacement = placementDao.placements.find { it.tournamentId == 300L && it.reachedRound == TournamentRound.WINNER.name }
        assertNotNull(winnerPlacement)
        assertEquals(15000.0, winnerPlacement!!.prizeMoney, 0.001)

        // Total placements for 128 players tournament: 127 losers + 1 winner = 128 placements
        val pcPlacements = placementDao.placements.filter { it.tournamentId == 300L }
        assertEquals(128, pcPlacements.size)
    }

    @Test
    fun testUkOpenGradualEntryProgression() = runBlocking {
        val ukOpen = Tournament(
            id = 400L,
            seasonId = 100L,
            name = "UK Open",
            type = TournamentEvent.UK_OPEN.name,
            order = 7
        )
        tournamentDao.tournaments.add(ukOpen)

        // 1. Initial R1_160 matches (32 matches)
        generateBracketUseCase(400L)
        var matches = tournamentMatchDao.getMatchesListForTournament(400L)
        assertEquals(32, matches.size)
        assertTrue(matches.all { it.round == TournamentRound.R1_160.name })

        // Simulate R1_160 and advance to R2_128
        simulateAllMatchesIncludingUser(400L)
        val advancedR1 = advanceTournamentRoundUseCase(400L)
        assertTrue(advancedR1)

        matches = tournamentMatchDao.getMatchesListForTournament(400L)
        val r2Matches = matches.filter { it.round == TournamentRound.R2_128.name }
        assertEquals(32, r2Matches.size)

        // Simulate R2_128 and advance to R3_96
        simulateAllMatchesIncludingUser(400L)
        val advancedR2 = advanceTournamentRoundUseCase(400L)
        assertTrue(advancedR2)

        matches = tournamentMatchDao.getMatchesListForTournament(400L)
        val r3Matches = matches.filter { it.round == TournamentRound.R3_96.name }
        assertEquals(32, r3Matches.size)

        // Simulate R3_96 and advance to L64
        simulateAllMatchesIncludingUser(400L)
        val advancedR3 = advanceTournamentRoundUseCase(400L)
        assertTrue(advancedR3)

        matches = tournamentMatchDao.getMatchesListForTournament(400L)
        val l64Matches = matches.filter { it.round == TournamentRound.L64.name }
        assertEquals(32, l64Matches.size)
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
        val tournaments = mutableListOf<Tournament>()

        override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> = flowOf(tournaments.filter { it.seasonId == seasonId })
        override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = tournaments.filter { it.seasonId == seasonId }
        override suspend fun getTournamentById(id: Long): Tournament? = tournaments.find { it.id == id }
        override suspend fun insertTournament(tournament: Tournament): Long {
            tournaments.add(tournament)
            return tournament.id
        }
        override suspend fun insertTournaments(tournaments: List<Tournament>) {
            this.tournaments.addAll(tournaments)
        }
        override suspend fun updateTournament(tournament: Tournament) {
            val index = tournaments.indexOfFirst { it.id == tournament.id }
            if (index != -1) {
                tournaments[index] = tournament
            }
        }
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
        val placements = mutableListOf<TournamentPlacement>()
        private var idCounter = 1L

        override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> = flowOf(placements.filter { it.tournamentId == tournamentId })
        override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = placements.filter { it.tournamentId == tournamentId }
        override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> = flowOf(placements.filter { it.playerId == playerId })
        override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> = placements
        override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> = flowOf(placements)
        override suspend fun insertPlacement(placement: TournamentPlacement): Long {
            val assignedId = if (placement.id == 0L) idCounter++ else placement.id
            val item = placement.copy(id = assignedId)
            placements.add(item)
            return assignedId
        }
        override suspend fun insertPlacements(placements: List<TournamentPlacement>) {
            placements.forEach { insertPlacement(it) }
        }
        override suspend fun updatePlacement(placement: TournamentPlacement) {
            val index = placements.indexOfFirst { it.id == placement.id }
            if (index != -1) {
                placements[index] = placement
            }
        }
        override suspend fun deletePlacement(placement: TournamentPlacement) {
            placements.removeIf { it.id == placement.id }
        }
        override suspend fun deletePlacementsForTournament(tournamentId: Long) {
            placements.removeIf { it.tournamentId == tournamentId }
        }
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
            val existingIdx = matches.indexOfFirst { it.id == assignedId }
            if (existingIdx != -1) {
                matches[existingIdx] = item
            } else {
                matches.add(item)
            }
            return assignedId
        }

        override suspend fun insertMatches(matches: List<TournamentMatch>) {
            matches.forEach { insertMatch(it) }
        }

        override suspend fun updateMatch(match: TournamentMatch) {
            val index = matches.indexOfFirst { it.id == match.id }
            if (index != -1) {
                matches[index] = match
            } else {
                matches.add(match)
            }
        }

        override suspend fun deleteMatch(match: TournamentMatch) {
            matches.removeIf { it.id == match.id }
        }

        override suspend fun deleteMatchesForTournament(tournamentId: Long) {
            matches.removeIf { it.tournamentId == tournamentId }
        }
    }
}
