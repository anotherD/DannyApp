package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class TournamentPlacementRepository(context: Context) {

    private val tournamentPlacementDao: TournamentPlacementDao = AppDatabase.getDatabase(context.applicationContext).tournamentPlacementDao()

    fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>> =
        tournamentPlacementDao.getPlacementsForTournament(tournamentId)

    suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement> = withContext(Dispatchers.IO) {
        tournamentPlacementDao.getPlacementsListForTournament(tournamentId)
    }

    fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>> =
        tournamentPlacementDao.getPlacementsForPlayer(playerId)

    suspend fun insertPlacement(placement: TournamentPlacement): Long = withContext(Dispatchers.IO) {
        tournamentPlacementDao.insertPlacement(placement)
    }

    suspend fun insertPlacements(placements: List<TournamentPlacement>) = withContext(Dispatchers.IO) {
        tournamentPlacementDao.insertPlacements(placements)
    }

    suspend fun updatePlacement(placement: TournamentPlacement) = withContext(Dispatchers.IO) {
        tournamentPlacementDao.updatePlacement(placement)
    }

    suspend fun deletePlacement(placement: TournamentPlacement) = withContext(Dispatchers.IO) {
        tournamentPlacementDao.deletePlacement(placement)
    }

    suspend fun deletePlacementsForTournament(tournamentId: Long) = withContext(Dispatchers.IO) {
        tournamentPlacementDao.deletePlacementsForTournament(tournamentId)
    }
}
