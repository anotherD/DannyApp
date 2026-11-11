package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TournamentRepository(context: Context) {

    private val tournamentDao: TournamentDao = AppDatabase.getDatabase(context.applicationContext).tournamentDao()

    fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>> =
        tournamentDao.getTournamentsForSeason(seasonId)

    suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament> = withContext(Dispatchers.IO) {
        tournamentDao.getTournamentsListForSeason(seasonId)
    }

    suspend fun getTournamentById(id: Long): Tournament? = withContext(Dispatchers.IO) {
        tournamentDao.getTournamentById(id)
    }

    suspend fun insertTournament(tournament: Tournament): Long = withContext(Dispatchers.IO) {
        tournamentDao.insertTournament(tournament)
    }

    suspend fun insertTournaments(tournaments: List<Tournament>) = withContext(Dispatchers.IO) {
        tournamentDao.insertTournaments(tournaments)
    }

    suspend fun updateTournament(tournament: Tournament) = withContext(Dispatchers.IO) {
        tournamentDao.updateTournament(tournament)
    }

    suspend fun deleteTournament(tournament: Tournament) = withContext(Dispatchers.IO) {
        tournamentDao.deleteTournament(tournament)
    }

    suspend fun deleteTournamentById(id: Long) = withContext(Dispatchers.IO) {
        tournamentDao.deleteTournamentById(id)
    }

    suspend fun deleteTournamentsForSeason(seasonId: Long) = withContext(Dispatchers.IO) {
        tournamentDao.deleteTournamentsForSeason(seasonId)
    }
}
