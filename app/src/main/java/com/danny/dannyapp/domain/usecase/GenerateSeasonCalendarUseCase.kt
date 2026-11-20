package com.danny.dannyapp.domain.usecase

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class GenerateSeasonCalendarUseCase(
    private val tournamentRepository: TournamentRepository,
    private val context: Context? = null
) {
    constructor(tournamentDao: TournamentDao, context: Context? = null) : this(
        TournamentRepository(
            tournamentDao = tournamentDao,
            tournamentMatchDao = DummyMatchDao,
            tournamentPlacementDao = DummyPlacementDao,
            tournamentParticipantDao = DummyParticipantDao
        ),
        context
    )

    constructor(context: Context) : this(
        TournamentRepository(context),
        context
    )

    suspend operator fun invoke(seasonId: Long): List<Tournament> = withContext(Dispatchers.IO) {
        val existing = tournamentRepository.getTournamentsListForSeason(seasonId)
        if (existing.isNotEmpty()) {
            return@withContext existing
        }

        val tournaments = createSeasonCalendar(seasonId)
        tournamentRepository.insertTournaments(tournaments)
        tournamentRepository.getTournamentsListForSeason(seasonId)
    }

    fun createSeasonCalendar(seasonId: Long): List<Tournament> {
        val config = if (context != null) {
            TournamentConfigLoader.loadFromAssets(context)
        } else {
            TournamentConfigLoader.safeConfig()
                ?: throw IllegalStateException("TournamentConfigLoader wurde nicht initialisiert.")
        }

        return config.calendar.map { entry ->
            Tournament(
                seasonId = seasonId,
                name = entry.name,
                type = entry.type.name,
                order = entry.order,
                hostCountry = entry.hostCountry,
                hostCity = entry.hostCity
            )
        }
    }
}

private object DummyMatchDao : TournamentMatchDao {
    override fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> = throw UnsupportedOperationException()
    override fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>> = throw UnsupportedOperationException()
    override suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> = throw UnsupportedOperationException()
    override suspend fun getMatchById(id: Long): TournamentMatch? = throw UnsupportedOperationException()
    override suspend fun insertMatch(match: TournamentMatch): Long = throw UnsupportedOperationException()
    override suspend fun insertMatches(matches: List<TournamentMatch>) = throw UnsupportedOperationException()
    override suspend fun updateMatch(match: TournamentMatch) = throw UnsupportedOperationException()
    override suspend fun deleteMatch(match: TournamentMatch) = throw UnsupportedOperationException()
    override suspend fun deleteMatchesForTournament(tournamentId: Long) = throw UnsupportedOperationException()
}

private object DummyPlacementDao : TournamentPlacementDao {
    override fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> = throw UnsupportedOperationException()
    override suspend fun getPlacementForPlayerInTournament(tournamentId: Long, playerId: Long): TournamentPlacement? = throw UnsupportedOperationException()
    override suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = throw UnsupportedOperationException()
    override fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> = throw UnsupportedOperationException()
    override suspend fun getPlacementsForSeasons(seasonIds: List<Long>): List<TournamentPlacement> = throw UnsupportedOperationException()
    override fun getPlacementsFlowForSeasons(seasonIds: List<Long>): Flow<List<TournamentPlacement>> = throw UnsupportedOperationException()
    override suspend fun insertPlacement(placement: TournamentPlacement): Long = throw UnsupportedOperationException()
    override suspend fun insertPlacements(placements: List<TournamentPlacement>) = throw UnsupportedOperationException()
    override suspend fun updatePlacement(placement: TournamentPlacement) = throw UnsupportedOperationException()
    override suspend fun deletePlacement(placement: TournamentPlacement) = throw UnsupportedOperationException()
    override suspend fun deletePlacementsForTournament(tournamentId: Long) = throw UnsupportedOperationException()
    override suspend fun upsertPlayerPlacement(tournamentId: Long, playerId: Long, reachedRound: String, prizeMoney: Double) = throw UnsupportedOperationException()
}

private object DummyParticipantDao : TournamentParticipantDao {
    override suspend fun insertParticipants(participants: List<TournamentParticipant>) = throw UnsupportedOperationException()
    override suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = throw UnsupportedOperationException()
    override suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant> = throw UnsupportedOperationException()
}
