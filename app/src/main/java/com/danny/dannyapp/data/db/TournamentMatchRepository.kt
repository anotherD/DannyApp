package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TournamentMatchRepository(context: Context) {

    private val tournamentMatchDao: TournamentMatchDao = AppDatabase.getDatabase(context.applicationContext).tournamentMatchDao()

    fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>> =
        tournamentMatchDao.getMatchesForTournament(tournamentId)

    suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch> = withContext(Dispatchers.IO) {
        tournamentMatchDao.getMatchesListForTournament(tournamentId)
    }

    suspend fun getMatchById(id: Long): TournamentMatch? = withContext(Dispatchers.IO) {
        tournamentMatchDao.getMatchById(id)
    }

    suspend fun insertMatch(match: TournamentMatch): Long = withContext(Dispatchers.IO) {
        tournamentMatchDao.insertMatch(match)
    }

    suspend fun insertMatches(matches: List<TournamentMatch>) = withContext(Dispatchers.IO) {
        tournamentMatchDao.insertMatches(matches)
    }

    suspend fun updateMatch(match: TournamentMatch) = withContext(Dispatchers.IO) {
        tournamentMatchDao.updateMatch(match)
    }

    suspend fun deleteMatch(match: TournamentMatch) = withContext(Dispatchers.IO) {
        tournamentMatchDao.deleteMatch(match)
    }

    suspend fun deleteMatchesForTournament(tournamentId: Long) = withContext(Dispatchers.IO) {
        tournamentMatchDao.deleteMatchesForTournament(tournamentId)
    }
}
