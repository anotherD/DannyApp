package com.danny.dannyapp.data.db

import android.content.Context
import com.danny.dannyapp.domain.config.TournamentConfigLoader
import com.danny.dannyapp.domain.model.OrderOfMeritEntry
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * Repository zur Verwaltung von Saisons und Tour-Cards.
 */
class SeasonRepository(
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentRepository: TournamentRepository,
    private val context: Context? = null
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        TournamentRepository(
            AppDatabase.getDatabase(context.applicationContext).tournamentDao(),
            AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao(),
            AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao(),
            AppDatabase.getDatabase(context.applicationContext).tournamentParticipantDao()
        ),
        context
    ) {
        TournamentConfigLoader.loadFromAssets(context)
    }

    constructor(
        seasonDao: SeasonDao,
        tourCardDao: TourCardDao,
        playerDao: PlayerDao
    ) : this(
        seasonDao,
        tourCardDao,
        playerDao,
        TournamentRepository(
            object : TournamentDao {
                private val list = mutableListOf<Tournament>()
                override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> = kotlinx.coroutines.flow.flowOf(list)
                override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = list.filter { it.seasonId == seasonId }
                override suspend fun getTournamentsListForSeasons(seasonIds: List<Long>): List<Tournament> = list.filter { it.seasonId in seasonIds }
                override suspend fun getTournamentById(id: Long): Tournament? = list.find { it.id == id }
                override suspend fun insertTournament(tournament: Tournament): Long = tournament.id
                override suspend fun insertTournaments(tournaments: List<Tournament>) { list.addAll(tournaments) }
                override suspend fun updateTournament(tournament: Tournament) {}
                override suspend fun deleteTournament(tournament: Tournament) {}
                override suspend fun deleteTournamentById(id: Long) {}
                override suspend fun deleteTournamentsForSeason(seasonId: Long) { list.removeIf { it.seasonId == seasonId } }
            },
            object : TournamentMatchDao {
                override fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> = emptyList()
                override suspend fun getMatchesListForSeason(seasonId: Long): List<TournamentMatch> = emptyList()
                override suspend fun getMatchById(id: Long): TournamentMatch? = null
                override suspend fun insertMatch(match: TournamentMatch): Long = 0L
                override suspend fun insertMatches(matches: List<TournamentMatch>) {}
                override suspend fun updateMatch(match: TournamentMatch) {}
                override suspend fun deleteMatch(match: TournamentMatch) {}
                override suspend fun deleteMatchesForTournament(tournamentId: Long) {}
            },
            object : TournamentPlacementDao {
                override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun getPlacementForPlayerInTournament(tournamentId: Long, playerId: Long): TournamentPlacement? = null
                override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = emptyList()
                override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> = emptyList()
                override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> = kotlinx.coroutines.flow.flowOf(emptyList())
                override suspend fun insertPlacement(placement: TournamentPlacement): Long = 0L
                override suspend fun insertPlacements(placements: List<TournamentPlacement>) {}
                override suspend fun updatePlacement(placement: TournamentPlacement) {}
                override suspend fun deletePlacement(placement: TournamentPlacement) {}
                override suspend fun deletePlacementsForTournament(tournamentId: Long) {}
                override suspend fun upsertPlayerPlacement(tournamentId: Long, playerId: Long, reachedRound: String, prizeMoney: Double) {}
            },
            object : TournamentParticipantDao {
                override suspend fun insertParticipants(participants: List<TournamentParticipant>) {}
                override suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = emptyList()
                override suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = emptyList()
            }
        )
    )

    /**
     * Liefert einen [Flow] aller Saisons einer bestimmten Karriere, absteigend nach Saisonnummer sortiert.
     */
    fun getSeasonsForCareer(careerId: Long): Flow<List<Season>> =
        seasonDao.getSeasonsForCareer(careerId)

    /**
     * Ruft die Liste aller Saisons einer Karriere ab.
     */
    suspend fun getSeasonsListForCareer(careerId: Long): List<Season> = withContext(Dispatchers.IO) {
        seasonDao.getSeasonsListForCareer(careerId)
    }

    /**
     * Ruft eine Saison anhand ihrer [id] ab.
     *
     * @throws IllegalArgumentException wenn [id] <= 0.
     * @throws IllegalStateException wenn die Saison nicht gefunden wird.
     */
    suspend fun getSeasonById(id: Long): Season = withContext(Dispatchers.IO) {
        require(id > 0L) { "Ungültige Saison-ID: $id" }
        requireNotNull(seasonDao.getSeasonById(id)) { "Saison mit ID $id nicht gefunden" }
    }

    /**
     * Ruft die aktuelle Saison einer Karriere ab oder erstellt Saison 1, falls noch keine Saison existiert.
     */
    suspend fun getOrCreateInitialSeasonForCareer(careerId: Long): Season {
        val existingSeason = seasonDao.getCurrentSeasonForCareer(careerId)
        if (existingSeason != null) {
            return existingSeason
        }
        val newSeasonId = createSeason(careerId)
        return getSeasonById(newSeasonId)
    }

    /**
     * Liefert einen [Flow] aller Tour-Cards inkl. Spielerdaten für eine Saison.
     */
    fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>> =
        tourCardDao.getTourCardsForSeason(seasonId)

    /**
     * Ruft die Liste aller Tour-Cards inkl. Spielerdaten für eine Saison einmalig ab.
     */
    suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer> = withContext(Dispatchers.IO) {
        tourCardDao.getTourCardsListForSeason(seasonId)
    }

    /**
     * Liefert alle eindeutigen Spieler-IDs, die in der angegebenen Saison eine Tour-Card besitzen.
     */
    suspend fun getTourCardPlayerIdsForSeason(seasonId: Long): List<Long> {
        return tourCardDao.getTourCardsListForSeason(seasonId).map { it.tourCard.playerId }.distinct()
    }

    /**
     * Erstellt eine historische Vor-Saison (-1 oder 0) für eine Karriere und generiert den Saisonkalender.
     */
    suspend fun createHistoricalSeason(careerId: Long, seasonNumber: Int): Long = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        require(seasonNumber == -1 || seasonNumber == 0) { "Historische Saisonnummer muss -1 oder 0 sein, war: $seasonNumber" }
        val newSeason = Season(
            careerId = careerId,
            seasonNumber = seasonNumber,
            isCompleted = true
        )
        val seasonId = seasonDao.insertSeason(newSeason)
        val generateCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentRepository)
        generateCalendarUseCase(seasonId)
        seasonId
    }

    /**
     * Erstellt eine neue Saison für eine Karriere und generiert den Saisonkalender sowie die Tour-Cards.
     */
    suspend fun createSeason(careerId: Long): Long = withContext(Dispatchers.IO) {
        val currentSeason = seasonDao.getCurrentSeasonForCareer(careerId)
        val nextSeasonNumber = (currentSeason?.seasonNumber ?: 0) + 1

        if (currentSeason != null) {
            seasonDao.updateSeason(currentSeason.copy(isCompleted = true))
        }

        val newSeason = Season(
            careerId = careerId,
            seasonNumber = nextSeasonNumber
        )
        val seasonId = seasonDao.insertSeason(newSeason)

        val tourCards = determineTourCardsForSeason(
            seasonId = seasonId,
            seasonNumber = nextSeasonNumber,
            previousSeasonId = currentSeason?.id
        )

        tourCardDao.insertTourCards(tourCards)

        val generateCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentRepository)
        generateCalendarUseCase(seasonId)

        seasonId
    }

    /**
     * Schließt die aktuelle Saison ab, legt die nächste Saison an,
     * erzeugt die 128 Tour Cards über die OOM- und Q-School-Logik und generiert den Saisonkalender.
     */
    suspend fun advanceToNextSeason(
        careerId: Long,
        oomEntries: List<OrderOfMeritEntry>
    ): Long = withContext(Dispatchers.IO) {
        require(careerId > 0L) { "Ungültige Karriere-ID: $careerId" }
        val currentSeason = seasonDao.getCurrentSeasonForCareer(careerId)
        requireNotNull(currentSeason) { "Keine aktuelle Saison für Karriere-ID $careerId gefunden" }

        seasonDao.updateSeason(currentSeason.copy(isCompleted = true))

        val nextSeasonNumber = currentSeason.seasonNumber + 1
        val newSeason = Season(
            careerId = careerId,
            seasonNumber = nextSeasonNumber
        )
        val newSeasonId = seasonDao.insertSeason(newSeason)

        val tourCards = determineTourCardsForSeason(
            seasonId = newSeasonId,
            seasonNumber = nextSeasonNumber,
            previousSeasonId = currentSeason.id,
            oomEntries = oomEntries
        )

        tourCardDao.insertTourCards(tourCards)

        val generateCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentRepository)
        generateCalendarUseCase(newSeasonId)

        newSeasonId
    }

    private suspend fun determineTourCardsForSeason(
        seasonId: Long,
        seasonNumber: Int,
        previousSeasonId: Long?,
        oomEntries: List<OrderOfMeritEntry> = emptyList()
    ): List<TourCard> {
        val tourCards = if (seasonNumber == 1 || previousSeasonId == null) {
            val cards = mutableListOf<TourCard>()
            // 1. Human User receives 1 Tour Card (playerId = USER_ID, tourCardYear = 2)
            cards.add(
                TourCard(
                    seasonId = seasonId,
                    playerId = USER_ID,
                    tourCardYear = 2
                )
            )
            // 2. Determine CPU Tour Cards (127 spots)
            val cpuTourCards = determineInitialCpuTourCards(seasonId, targetCount = CPU_TOUR_CARDS_TARGET)
            cards.addAll(cpuTourCards)
            cards
        } else {
            determineNextSeasonCpuTourCards(seasonId, previousSeasonId, oomEntries)
        }

        check(tourCards.size == TOTAL_TOUR_CARDS_PER_SEASON) {
            "Soll-Anzahl von $TOTAL_TOUR_CARDS_PER_SEASON Tour Cards nicht erreicht: ${tourCards.size}"
        }

        return tourCards
    }

    private suspend fun determineInitialCpuTourCards(
        seasonId: Long,
        targetCount: Int
    ): List<TourCard> {
        val allPlayers = playerDao.getAllPlayersList()

        val candidateTourCardPlayers = allPlayers
            .filter { it.hasTourCard }
            .sortedWith(
                compareByDescending<Player> { it.skill }
                    .thenByDescending { it.prizeMoney }
                    .thenBy { it.name }
            )

        val result = mutableListOf<TourCard>()

        if (candidateTourCardPlayers.size >= targetCount) {
            // Take top targetCount candidates (truncate weaker tour card players)
            candidateTourCardPlayers.take(targetCount).forEach { player ->
                result.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = player.id,
                        tourCardYear = player.tourCardYear
                    )
                )
            }
        } else {
            // Take all candidates and fill remaining spots from non-tour card players
            candidateTourCardPlayers.forEach { player ->
                result.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = player.id,
                        tourCardYear = player.tourCardYear
                    )
                )
            }

            val neededCount = targetCount - result.size
            val nonTourCardPlayers = allPlayers
                .filter { !it.hasTourCard }
                .sortedWith(
                    compareByDescending<Player> { it.skill }
                        .thenByDescending { it.prizeMoney }
                        .thenBy { it.name }
                )

            nonTourCardPlayers.take(neededCount).forEach { player ->
                result.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = player.id,
                        tourCardYear = 2 // Assigned tour card year 2 for backfilled players
                    )
                )
            }
        }

        return result
    }

    private suspend fun determineNextSeasonCpuTourCards(
        seasonId: Long,
        previousSeasonId: Long,
        oomEntries: List<OrderOfMeritEntry>
    ): List<TourCard> {
        val previousTourCards = tourCardDao.getRawTourCardsForSeason(previousSeasonId)
        require(previousTourCards.isNotEmpty()) { "Keine Tour-Cards der Vorsaison (ID $previousSeasonId) gefunden" }

        val top64PlayerIds = oomEntries.take(64).map { it.playerId }.toSet()

        val retainedCards = mutableListOf<TourCard>()
        for (previousCard in previousTourCards) {
            val isTop64 = previousCard.playerId in top64PlayerIds
            if (isTop64) {
                // Behält Tour Card; Jahr 1 steigt auf Jahr 2 auf, Jahr 2 bleibt Jahr 2
                retainedCards.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = previousCard.playerId,
                        tourCardYear = 2
                    )
                )
            } else if (previousCard.tourCardYear == 1) {
                // Nicht in Top 64, aber Jahr 1 -> steigt ins 2. Jahr auf
                retainedCards.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = previousCard.playerId,
                        tourCardYear = 2
                    )
                )
            }
            // Sonst: Nicht in Top 64 & war Jahr 2 -> verliert Tour Card
        }

        val retainedPlayerIds = retainedCards.map { it.playerId }.toSet()
        val userRetained = USER_ID in retainedPlayerIds
        val userWonQSchool = !userRetained

        val resultCards = mutableListOf<TourCard>()
        resultCards.addAll(retainedCards)

        if (userWonQSchool) {
            resultCards.add(
                TourCard(
                    seasonId = seasonId,
                    playerId = USER_ID,
                    tourCardYear = 1
                )
            )
        }

        val remainingSlots = TOTAL_TOUR_CARDS_PER_SEASON - resultCards.size
        if (remainingSlots > 0) {
            val allCpuPlayers = playerDao.getAllPlayersList()
            val cpuCandidates = allCpuPlayers.filter { it.id !in retainedPlayerIds }

            val selectedCpuQSchoolPlayers = cpuCandidates
                .groupBy { it.skill }
                .entries
                .sortedByDescending { it.key }
                .flatMap { (_, playersWithSameSkill) -> playersWithSameSkill.shuffled() }
                .take(remainingSlots)

            check(selectedCpuQSchoolPlayers.size == remainingSlots) {
                "Nicht genügend CPU-Kandidaten für Q-School. Benötigt: $remainingSlots, verfügbar: ${selectedCpuQSchoolPlayers.size}"
            }

            selectedCpuQSchoolPlayers.forEach { cpuPlayer ->
                resultCards.add(
                    TourCard(
                        seasonId = seasonId,
                        playerId = cpuPlayer.id,
                        tourCardYear = 1
                    )
                )
            }
        }

        check(resultCards.size == TOTAL_TOUR_CARDS_PER_SEASON) {
            "Soll-Anzahl von $TOTAL_TOUR_CARDS_PER_SEASON Tour Cards nicht erreicht: ${resultCards.size}"
        }

        return resultCards
    }

    companion object {
        const val TOTAL_TOUR_CARDS_PER_SEASON = 128
        const val CPU_TOUR_CARDS_TARGET = 127
    }
}
