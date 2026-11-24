package com.danny.dannyapp.domain.usecase

import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.Career
import com.danny.dannyapp.data.db.CareerDao
import com.danny.dannyapp.data.db.CareerRepository
import com.danny.dannyapp.data.db.CareerWithProfile
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerDao
import com.danny.dannyapp.data.db.PlayerRepository
import com.danny.dannyapp.data.db.Season
import com.danny.dannyapp.data.db.SeasonDao
import com.danny.dannyapp.data.db.SeasonRepository
import com.danny.dannyapp.data.db.TourCard
import com.danny.dannyapp.data.db.TourCardDao
import com.danny.dannyapp.data.db.TourCardWithPlayer
import com.danny.dannyapp.data.db.Tournament
import com.danny.dannyapp.data.db.TournamentDao
import com.danny.dannyapp.data.db.TournamentMatch
import com.danny.dannyapp.data.db.TournamentMatchDao
import com.danny.dannyapp.data.db.TournamentParticipant
import com.danny.dannyapp.data.db.TournamentParticipantDao
import com.danny.dannyapp.data.db.TournamentPlacement
import com.danny.dannyapp.data.db.TournamentPlacementDao
import com.danny.dannyapp.data.db.TournamentRepository
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.enums.MatchDistancePreset
import com.danny.dannyapp.domain.enums.OrderOfMeritType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class FullSeasonSimulationTest {

    private lateinit var playerDao: InMemoryPlayerDao
    private lateinit var careerDao: InMemoryCareerDao
    private lateinit var seasonDao: InMemorySeasonDao
    private lateinit var tourCardDao: InMemoryTourCardDao
    private lateinit var tournamentDao: InMemoryTournamentDao
    private lateinit var tournamentMatchDao: InMemoryTournamentMatchDao
    private lateinit var tournamentPlacementDao: InMemoryTournamentPlacementDao
    private lateinit var tournamentParticipantDao: InMemoryTournamentParticipantDao

    private lateinit var playerRepository: PlayerRepository
    private lateinit var tournamentRepository: TournamentRepository
    private lateinit var seasonRepository: SeasonRepository
    private lateinit var careerRepository: CareerRepository

    private lateinit var initializeCareerOomUseCase: InitializeCareerOomUseCase
    private lateinit var getOrderOfMeritUseCase: GetOrderOfMeritUseCase
    private lateinit var tournamentQualificationUseCase: TournamentQualificationUseCase
    private lateinit var generateBracketUseCase: GenerateBracketUseCase
    private lateinit var advanceTournamentRoundUseCase: AdvanceTournamentRoundUseCase
    private lateinit var completeMatchUseCase: CompleteMatchUseCase
    private lateinit var tournamentProgressionUseCase: TournamentProgressionUseCase

    @Before
    fun setUp() {
        TournamentConfigLoader.clearCache()

        val jsonFile = File("src/main/assets/season_calendar_2027.json").let {
            if (it.exists()) it else File("app/src/main/assets/season_calendar_2027.json")
        }
        FileInputStream(jsonFile).use { stream ->
            TournamentConfigLoader.loadFromStream(stream)
        }

        val csvFile = File("src/main/assets/darts_rankings.csv").let {
            if (it.exists()) it else File("app/src/main/assets/darts_rankings.csv")
        }
        val playersList = mutableListOf<Player>()
        var idCounter = 1L
        csvFile.bufferedReader().useLines { lines ->
            val iterator = lines.iterator()
            if (iterator.hasNext()) {
                val headerLine = iterator.next()
                val cleanHeader = if (headerLine.startsWith("\uFEFF")) headerLine.substring(1) else headerLine
                val columnIndexMap = cleanHeader.split(";")
                    .mapIndexed { index, columnName -> columnName.trim().lowercase() to index }
                    .toMap()

                fun List<String>.getCol(colName: String): String? {
                    val idx = columnIndexMap[colName.lowercase()] ?: return null
                    return this.getOrNull(idx)?.trim()
                }

                while (iterator.hasNext()) {
                    val line = iterator.next()
                    if (line.isBlank()) continue
                    val tokens = line.split(";")
                    val name = tokens.getCol("name") ?: continue
                    if (name.isEmpty()) continue

                    val countryCode = tokens.getCol("country").orEmpty()
                    val skillStr = tokens.getCol("skill_level")
                    val skill = skillStr?.toDoubleOrNull()?.toInt() ?: skillStr?.toIntOrNull() ?: 5
                    val tourYearStr = tokens.getCol("tour_card_year")
                    val tourCardYear = tourYearStr?.toDoubleOrNull()?.toInt() ?: tourYearStr?.toIntOrNull() ?: 0
                    val prizeMoneyStr = tokens.getCol("price_money")
                    val prizeMoney = prizeMoneyStr?.toDoubleOrNull() ?: 0.0

                    playersList.add(
                        Player(
                            id = idCounter++,
                            name = name,
                            skill = skill,
                            countryCode = countryCode,
                            prizeMoney = prizeMoney,
                            tourCardYear = tourCardYear
                        )
                    )
                }
            }
        }

        AppDatabase.initialSyncCompleted.complete(Unit)

        playerDao = InMemoryPlayerDao(playersList)
        careerDao = InMemoryCareerDao()
        seasonDao = InMemorySeasonDao()
        tourCardDao = InMemoryTourCardDao(playerDao)
        tournamentDao = InMemoryTournamentDao()
        tournamentMatchDao = InMemoryTournamentMatchDao()
        tournamentPlacementDao = InMemoryTournamentPlacementDao(tournamentDao)
        tournamentParticipantDao = InMemoryTournamentParticipantDao()

        playerRepository = PlayerRepository(playerDao)
        tournamentRepository = TournamentRepository(
            tournamentDao = tournamentDao,
            tournamentMatchDao = tournamentMatchDao,
            tournamentPlacementDao = tournamentPlacementDao,
            tournamentParticipantDao = tournamentParticipantDao,
            database = null
        )
        seasonRepository = SeasonRepository(
            seasonDao = seasonDao,
            tourCardDao = tourCardDao,
            playerDao = playerDao,
            tournamentRepository = tournamentRepository
        )

        initializeCareerOomUseCase = InitializeCareerOomUseCase(
            seasonRepository = seasonRepository,
            tournamentRepository = tournamentRepository,
            playerRepository = playerRepository,
            readInitialPlacementsLines = {
                val file = File("src/main/assets/initial_placements.csv").let {
                    if (it.exists()) it else File("app/src/main/assets/initial_placements.csv")
                }
                file.bufferedReader().lineSequence()
            }
        )

        careerRepository = CareerRepository(
            careerDao = careerDao,
            seasonRepository = seasonRepository,
            initializeCareerOomUseCase = initializeCareerOomUseCase
        )

        getOrderOfMeritUseCase = GetOrderOfMeritUseCase(
            careerRepository = careerRepository,
            seasonRepository = seasonRepository,
            tournamentRepository = tournamentRepository,
            playerRepository = playerRepository
        )

        tournamentQualificationUseCase = TournamentQualificationUseCase(
            seasonRepository = seasonRepository,
            playerRepository = playerRepository,
            tournamentRepository = tournamentRepository,
            careerRepository = careerRepository,
            getOrderOfMeritUseCase = getOrderOfMeritUseCase
        )

        generateBracketUseCase = GenerateBracketUseCase(
            tournamentRepository = tournamentRepository,
            seasonRepository = seasonRepository,
            tournamentQualificationUseCase = tournamentQualificationUseCase
        )

        advanceTournamentRoundUseCase = AdvanceTournamentRoundUseCase(
            tournamentRepository = tournamentRepository
        )

        completeMatchUseCase = CompleteMatchUseCase(
            tournamentRepository = tournamentRepository,
            playerRepository = playerRepository,
            advanceTournamentRoundUseCase = advanceTournamentRoundUseCase,
            seasonRepository = seasonRepository,
            careerRepository = careerRepository
        )

        val dummyContext = object : android.content.ContextWrapper(null) {
            override fun getApplicationContext(): android.content.Context = this
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences {
                return object : android.content.SharedPreferences {
                    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
                    override fun getString(key: String?, defValue: String?): String? = defValue
                    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
                    override fun getInt(key: String?, defValue: Int): Int = defValue
                    override fun getLong(key: String?, defValue: Long): Long = defValue
                    override fun getFloat(key: String?, defValue: Float): Float = defValue
                    override fun getBoolean(key: String?, defValue: Boolean): Boolean = true
                    override fun contains(key: String?): Boolean = false
                    override fun edit(): android.content.SharedPreferences.Editor = object : android.content.SharedPreferences.Editor {
                        override fun putString(key: String?, value: String?): android.content.SharedPreferences.Editor = this
                        override fun putStringSet(key: String?, values: MutableSet<String>?): android.content.SharedPreferences.Editor = this
                        override fun putInt(key: String?, value: Int): android.content.SharedPreferences.Editor = this
                        override fun putLong(key: String?, value: Long): android.content.SharedPreferences.Editor = this
                        override fun putFloat(key: String?, value: Float): android.content.SharedPreferences.Editor = this
                        override fun putBoolean(key: String?, value: Boolean): android.content.SharedPreferences.Editor = this
                        override fun remove(key: String?): android.content.SharedPreferences.Editor = this
                        override fun clear(): android.content.SharedPreferences.Editor = this
                        override fun commit(): Boolean = true
                        override fun apply() {}
                    }
                    override fun registerOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
                    override fun unregisterOnSharedPreferenceChangeListener(listener: android.content.SharedPreferences.OnSharedPreferenceChangeListener?) {}
                }
            }
        }
        val settingsManager = com.danny.dannyapp.data.SettingsManager(dummyContext)

        tournamentProgressionUseCase = TournamentProgressionUseCase(
            completeMatchUseCase = completeMatchUseCase,
            advanceTournamentRoundUseCase = advanceTournamentRoundUseCase,
            tournamentRepository = tournamentRepository,
            generateBracketUseCase = generateBracketUseCase,
            settingsManager = settingsManager
        )
    }

    @Test
    fun simulateCompleteSeason_runsAllTournamentsWithoutErrors() {
        org.junit.Assume.assumeTrue(
            "Manueller Test: Starte mit RUN_SIM=true",
            System.getenv("RUN_SIM") == "true"
        )
        runBlocking {
            val career = Career(
                id = 1L,
                name = "Test",
                level = 10,
                season = 1,
                profileId = 1L,
                matchDistancePreset = MatchDistancePreset.RAPID.name
            )
            careerDao.insertCareer(career)

            initializeCareerOomUseCase(1L)
            val seasonId = seasonRepository.createSeason(1L)

            val seasonTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
                .sortedBy { it.order }

            assertTrue("Saison 1 muss Turniere enthalten", seasonTournaments.isNotEmpty())

            for (tournament in seasonTournaments) {
                println("Simuliere #${tournament.order}: ${tournament.name} (${tournament.type})")
                tournamentProgressionUseCase.simulateCompleteTournament(tournament.id)
                val completedTournament = tournamentRepository.getTournamentById(tournament.id)
                assertTrue(
                    "Turnier ${tournament.name} muss erfolgreich simuliert werden",
                    completedTournament.isCompleted
                )
            }

            val updatedTournaments = tournamentRepository.getTournamentsListForSeason(seasonId)
            assertTrue(
                "Alle Turniere müssen nach der Simulation isCompleted == true haben",
                updatedTournaments.all { it.isCompleted }
            )

            for (oomType in OrderOfMeritType.entries) {
                val oomEntries = getOrderOfMeritUseCase(1L, oomType)
                assertTrue(
                    "Order of Merit ($oomType) darf nicht leer sein",
                    oomEntries.isNotEmpty()
                )
            }
        }
    }

    private class InMemoryPlayerDao(val players: MutableList<Player>) : PlayerDao {
        override fun getAllPlayers(): Flow<List<Player>> = flowOf(players)
        override suspend fun getAllPlayersList(): List<Player> = players.toList()
        override fun getAllPlayersSortedBySkill(): Flow<List<Player>> =
            flowOf(players.sortedWith(compareByDescending<Player> { it.skill }.thenByDescending { it.prizeMoney }.thenBy { it.name }))
        override fun getAllPlayersSortedByName(): Flow<List<Player>> =
            flowOf(players.sortedBy { it.name })
        override suspend fun getPlayerById(id: Long): Player? =
            players.find { it.id == id }
        override suspend fun getPlayerCount(): Int = players.size
        override suspend fun insertPlayer(player: Player): Long {
            players.removeIf { it.id == player.id }
            players.add(player)
            return player.id
        }
        override suspend fun insertAll(players: List<Player>) {
            players.forEach { insertPlayer(it) }
        }
        override suspend fun updatePlayer(player: Player) {
            insertPlayer(player)
        }
        override suspend fun deletePlayer(player: Player) {
            players.removeIf { it.id == player.id }
        }
        override suspend fun deletePlayerById(id: Long) {
            players.removeIf { it.id == id }
        }
        override suspend fun csvInsertNewPlayers(players: List<Player>) {
            players.forEach { p ->
                if (this.players.none { it.name == p.name }) {
                    this.players.add(p)
                }
            }
        }
        override suspend fun csvUpdatePlayerStats(name: String, skill: Int, countryCode: String, prizeMoney: Double, tourCardYear: Int, updatedAt: Long) {
            val idx = players.indexOfFirst { it.name == name }
            if (idx >= 0) {
                val existing = players[idx]
                players[idx] = existing.copy(
                    skill = skill,
                    countryCode = countryCode,
                    prizeMoney = prizeMoney,
                    tourCardYear = tourCardYear,
                    updatedAt = updatedAt
                )
            }
        }
    }

    private class InMemoryCareerDao : CareerDao {
        val careers = mutableListOf<Career>()
        private var nextId = 1L

        override fun getAllCareers(): Flow<List<CareerWithProfile>> = flowOf(getAllCareersListSync())
        override suspend fun getAllCareersList(): List<CareerWithProfile> = getAllCareersListSync()

        private fun getAllCareersListSync(): List<CareerWithProfile> {
            return careers.map { career ->
                CareerWithProfile(
                    career = career,
                    profileName = "TestProfile",
                    playerName = "TestUser",
                    countryCode = "DEU",
                    currentTournamentName = null,
                    currentTournamentRound = null
                )
            }
        }

        override suspend fun getCareerById(id: Long): CareerWithProfile? {
            val career = careers.find { it.id == id } ?: return null
            return CareerWithProfile(
                career = career,
                profileName = "TestProfile",
                playerName = "TestUser",
                countryCode = "DEU",
                currentTournamentName = null,
                currentTournamentRound = null
            )
        }

        override suspend fun getCareerCountForProfile(profileId: Long): Int =
            careers.count { it.profileId == profileId }

        override suspend fun insertCareer(career: Career): Long {
            val id = if (career.id == 0L) nextId++ else career.id
            val updated = career.copy(id = id)
            careers.removeIf { it.id == id }
            careers.add(updated)
            return id
        }

        override suspend fun deleteCareerById(id: Long) {
            careers.removeIf { it.id == id }
        }

        override suspend fun deleteCareer(career: Career) {
            careers.removeIf { it.id == career.id }
        }
    }

    private class InMemorySeasonDao : SeasonDao {
        val seasons = mutableListOf<Season>()
        private var nextId = 1L

        override fun getSeasonsForCareer(careerId: Long): Flow<List<Season>> =
            flowOf(seasons.filter { it.careerId == careerId }.sortedByDescending { it.seasonNumber })

        override suspend fun getSeasonsListForCareer(careerId: Long): List<Season> =
            seasons.filter { it.careerId == careerId }.sortedBy { it.seasonNumber }

        override suspend fun getCurrentSeasonForCareer(careerId: Long): Season? =
            seasons.filter { it.careerId == careerId }.maxByOrNull { it.seasonNumber }

        override suspend fun getSeasonById(id: Long): Season? =
            seasons.find { it.id == id }

        override suspend fun insertSeason(season: Season): Long {
            val id = if (season.id == 0L) nextId++ else season.id
            val updated = season.copy(id = id)
            seasons.removeIf { it.id == id }
            seasons.add(updated)
            return id
        }

        override suspend fun updateSeason(season: Season) {
            seasons.removeIf { it.id == season.id }
            seasons.add(season)
        }

        override suspend fun deleteSeason(season: Season) {
            seasons.removeIf { it.id == season.id }
        }

        override suspend fun deleteSeasonById(id: Long) {
            seasons.removeIf { it.id == id }
        }
    }

    private class InMemoryTourCardDao(private val playerDao: PlayerDao) : TourCardDao {
        val tourCards = mutableListOf<TourCard>()
        private var nextId = 1L

        override fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>> =
            flowOf(runBlocking { getTourCardsListForSeason(seasonId) })

        override suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer> {
            return tourCards.filter { it.seasonId == seasonId }.map { tourCard ->
                val player = if (tourCard.playerId > 0L) playerDao.getPlayerById(tourCard.playerId) else null
                TourCardWithPlayer(tourCard = tourCard, player = player)
            }
        }

        override suspend fun getRawTourCardsForSeason(seasonId: Long): List<TourCard> =
            tourCards.filter { it.seasonId == seasonId }

        override suspend fun insertTourCard(tourCard: TourCard): Long {
            val id = if (tourCard.id == 0L) nextId++ else tourCard.id
            val updated = tourCard.copy(id = id)
            tourCards.removeIf { it.id == id }
            tourCards.add(updated)
            return id
        }

        override suspend fun insertTourCards(tourCards: List<TourCard>) {
            tourCards.forEach { insertTourCard(it) }
        }

        override suspend fun deleteTourCardsForSeason(seasonId: Long) {
            tourCards.removeIf { it.seasonId == seasonId }
        }
    }

    private class InMemoryTournamentDao : TournamentDao {
        val tournaments = mutableListOf<Tournament>()
        private var nextId = 1L

        override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> =
            flowOf(tournaments.filter { it.seasonId == seasonId })

        override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> =
            tournaments.filter { it.seasonId == seasonId }

        override suspend fun getTournamentsListForSeasons(seasonIds: List<Long>): List<Tournament> =
            tournaments.filter { it.seasonId in seasonIds }

        override suspend fun getTournamentById(id: Long): Tournament? =
            tournaments.find { it.id == id }

        override suspend fun insertTournament(tournament: Tournament): Long {
            val id = if (tournament.id == 0L) nextId++ else tournament.id
            val updated = tournament.copy(id = id)
            tournaments.removeIf { it.id == id }
            tournaments.add(updated)
            return id
        }

        override suspend fun insertTournaments(tournaments: List<Tournament>) {
            tournaments.forEach { insertTournament(it) }
        }

        override suspend fun updateTournament(tournament: Tournament) {
            tournaments.removeIf { it.id == tournament.id }
            tournaments.add(tournament)
        }

        override suspend fun deleteTournament(tournament: Tournament) {
            tournaments.removeIf { it.id == tournament.id }
        }

        override suspend fun deleteTournamentById(id: Long) {
            tournaments.removeIf { it.id == id }
        }

        override suspend fun deleteTournamentsForSeason(seasonId: Long) {
            tournaments.removeIf { it.seasonId == seasonId }
        }
    }

    private class InMemoryTournamentMatchDao : TournamentMatchDao {
        val matches = mutableListOf<TournamentMatch>()
        private var nextId = 1L

        override fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> =
            flowOf(matches.filter { it.tournamentId == tournamentId })

        override fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> =
            flowOf(matches)

        override suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> =
            matches.filter { it.tournamentId == tournamentId }

        override suspend fun getMatchesListForSeason(seasonId: Long): List<TournamentMatch> =
            matches

        override suspend fun getMatchById(id: Long): TournamentMatch? =
            matches.find { it.id == id }

        override suspend fun insertMatch(match: TournamentMatch): Long {
            val id = if (match.id == 0L) nextId++ else match.id
            val updated = match.copy(id = id)
            matches.removeIf { it.id == id }
            matches.add(updated)
            return id
        }

        override suspend fun insertMatches(matches: List<TournamentMatch>) {
            matches.forEach { insertMatch(it) }
        }

        override suspend fun updateMatch(match: TournamentMatch) {
            matches.removeIf { it.id == match.id }
            matches.add(match)
        }

        override suspend fun deleteMatch(match: TournamentMatch) {
            matches.removeIf { it.id == match.id }
        }

        override suspend fun deleteMatchesForTournament(tournamentId: Long) {
            matches.removeIf { it.tournamentId == tournamentId }
        }
    }

    private class InMemoryTournamentPlacementDao(private val tournamentDao: TournamentDao) : TournamentPlacementDao {
        val placements = mutableListOf<TournamentPlacement>()
        private var nextId = 1L

        override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> =
            flowOf(placements.filter { it.tournamentId == tournamentId })

        override suspend fun getPlacementForPlayerInTournament(tournamentId: Long, playerId: Long): TournamentPlacement? =
            placements.find { it.tournamentId == tournamentId && it.playerId == playerId }

        override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> =
            placements.filter { it.tournamentId == tournamentId }

        override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> =
            flowOf(placements.filter { it.playerId == playerId })

        override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> {
            val tournaments = tournamentDao.getTournamentsListForSeasons(seasonIds)
            val tIds = tournaments.map { it.id }.toSet()
            return placements.filter { it.tournamentId in tIds }
        }

        override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> =
            flowOf(runBlocking { getPlacementsForSeasons(seasonIds) })

        override suspend fun insertPlacement(placement: TournamentPlacement): Long {
            val id = if (placement.id == 0L) nextId++ else placement.id
            val updated = placement.copy(id = id)
            placements.removeIf { it.id == id }
            placements.add(updated)
            return id
        }

        override suspend fun insertPlacements(placements: List<TournamentPlacement>) {
            placements.forEach { insertPlacement(it) }
        }

        override suspend fun updatePlacement(placement: TournamentPlacement) {
            placements.removeIf { it.id == placement.id }
            placements.add(placement)
        }

        override suspend fun deletePlacement(placement: TournamentPlacement) {
            placements.removeIf { it.id == placement.id }
        }

        override suspend fun deletePlacementsForTournament(tournamentId: Long) {
            placements.removeIf { it.tournamentId == tournamentId }
        }

        override suspend fun upsertPlayerPlacement(tournamentId: Long, playerId: Long, reachedRound: String, prizeMoney: Double) {
            val existing = getPlacementForPlayerInTournament(tournamentId, playerId)
            if (existing != null) {
                updatePlacement(existing.copy(reachedRound = reachedRound, prizeMoney = prizeMoney))
            } else {
                insertPlacement(
                    TournamentPlacement(
                        tournamentId = tournamentId,
                        playerId = playerId,
                        reachedRound = reachedRound,
                        prizeMoney = prizeMoney
                    )
                )
            }
        }
    }

    private class InMemoryTournamentParticipantDao : TournamentParticipantDao {
        val participantsList = mutableListOf<TournamentParticipant>()
        private var nextId = 1L

        override suspend fun insertParticipants(participants: List<TournamentParticipant>) {
            participants.forEach { p ->
                val id = if (p.id == 0L) nextId++ else p.id
                val updated = p.copy(id = id)
                participantsList.removeIf { it.tournamentId == p.tournamentId && it.playerId == p.playerId }
                participantsList.add(updated)
            }
        }

        override suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> =
            participantsList.filter { it.tournamentId == tournamentId }

        override suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> =
            participantsList.filter { it.tournamentId == tournamentId && it.seedNumber != null }
    }
}
