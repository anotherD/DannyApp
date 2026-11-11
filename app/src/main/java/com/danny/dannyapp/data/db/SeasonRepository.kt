package com.danny.dannyapp.data.db

import android.content.Context
import com.danny.dannyapp.domain.usecase.GenerateSeasonCalendarUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class SeasonRepository(
    private val seasonDao: SeasonDao,
    private val tourCardDao: TourCardDao,
    private val playerDao: PlayerDao,
    private val tournamentDao: TournamentDao
) {

    constructor(context: Context) : this(
        AppDatabase.getDatabase(context.applicationContext).seasonDao(),
        AppDatabase.getDatabase(context.applicationContext).tourCardDao(),
        AppDatabase.getDatabase(context.applicationContext).playerDao(),
        AppDatabase.getDatabase(context.applicationContext).tournamentDao()
    )

    constructor(
        seasonDao: SeasonDao,
        tourCardDao: TourCardDao,
        playerDao: PlayerDao
    ) : this(
        seasonDao,
        tourCardDao,
        playerDao,
        object : TournamentDao {
            private val list = mutableListOf<Tournament>()
            override fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> = kotlinx.coroutines.flow.flowOf(list)
            override suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = list.filter { it.seasonId == seasonId }
            override suspend fun getTournamentById(id: Long): Tournament? = list.find { it.id == id }
            override suspend fun insertTournament(tournament: Tournament): Long = tournament.id
            override suspend fun insertTournaments(tournaments: List<Tournament>) { list.addAll(tournaments) }
            override suspend fun updateTournament(tournament: Tournament) {}
            override suspend fun deleteTournament(tournament: Tournament) {}
            override suspend fun deleteTournamentById(id: Long) {}
            override suspend fun deleteTournamentsForSeason(seasonId: Long) { list.removeIf { it.seasonId == seasonId } }
        }
    )

    fun getSeasonsForCareer(careerId: Long): Flow<List<Season>> =
        seasonDao.getSeasonsForCareer(careerId)

    suspend fun getCurrentSeasonForCareer(careerId: Long): Season? = withContext(Dispatchers.IO) {
        seasonDao.getCurrentSeasonForCareer(careerId)
    }

    fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>> =
        tourCardDao.getTourCardsForSeason(seasonId)

    suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer> = withContext(Dispatchers.IO) {
        tourCardDao.getTourCardsListForSeason(seasonId)
    }

    suspend fun createSeason(careerId: Long): Long = withContext(Dispatchers.IO) {
        val currentSeason = seasonDao.getCurrentSeasonForCareer(careerId)
        val nextSeasonNumber = (currentSeason?.seasonNumber ?: 0) + 1

        if (currentSeason != null) {
            seasonDao.updateSeason(currentSeason.copy(isCompleted = true))
        }

        val newSeason = Season(
            careerId = careerId,
            seasonNumber = nextSeasonNumber,
            isCompleted = false
        )
        val seasonId = seasonDao.insertSeason(newSeason)

        val tourCards = determineTourCardsForSeason(
            seasonId = seasonId,
            seasonNumber = nextSeasonNumber,
            previousSeasonId = currentSeason?.id
        )

        tourCardDao.insertTourCards(tourCards)

        val generateCalendarUseCase = GenerateSeasonCalendarUseCase(tournamentDao)
        generateCalendarUseCase(seasonId)

        seasonId
    }

    private suspend fun determineTourCardsForSeason(
        seasonId: Long,
        seasonNumber: Int,
        previousSeasonId: Long?
    ): List<TourCard> {
        val tourCards = mutableListOf<TourCard>()

        // 1. Human User receives 1 Tour Card (playerId = USER_ID, tourCardYear = 2)
        tourCards.add(
            TourCard(
                seasonId = seasonId,
                playerId = USER_ID,
                tourCardYear = 2
            )
        )

        // 2. Determine CPU Tour Cards (127 spots)
        val cpuTourCards = if (seasonNumber == 1 || previousSeasonId == null) {
            determineInitialCpuTourCards(seasonId, targetCount = CPU_TOUR_CARDS_TARGET)
        } else {
            determineNextSeasonCpuTourCards(seasonId, previousSeasonId, targetCount = CPU_TOUR_CARDS_TARGET)
        }

        tourCards.addAll(cpuTourCards)
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
        targetCount: Int
    ): List<TourCard> {
        // TODO: Calculate tour cards from previous season Order of Merit / rankings when implemented.
        // Temporary logic: fallback to initial player selection rules
        return determineInitialCpuTourCards(seasonId, targetCount)
    }

    companion object {
        const val TOTAL_TOUR_CARDS_PER_SEASON = 128
        const val CPU_TOUR_CARDS_TARGET = 127
    }
}
