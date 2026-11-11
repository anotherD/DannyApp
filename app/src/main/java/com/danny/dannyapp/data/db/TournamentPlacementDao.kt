package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentPlacementDao {

    @Query("SELECT * FROM tournament_placements WHERE tournament_id = :tournamentId")
    fun getPlacementsForTournament(tournamentId: Long): Flow<List<TournamentPlacement>>

    @Query("SELECT * FROM tournament_placements WHERE tournament_id = :tournamentId")
    suspend fun getPlacementsListForTournament(tournamentId: Long): List<TournamentPlacement>

    @Query("SELECT * FROM tournament_placements WHERE player_id = :playerId")
    fun getPlacementsForPlayer(playerId: Long): Flow<List<TournamentPlacement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacement(placement: TournamentPlacement): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlacements(placements: List<TournamentPlacement>)

    @Update
    suspend fun updatePlacement(placement: TournamentPlacement)

    @Delete
    suspend fun deletePlacement(placement: TournamentPlacement)

    @Query("DELETE FROM tournament_placements WHERE tournament_id = :tournamentId")
    suspend fun deletePlacementsForTournament(tournamentId: Long)
}
