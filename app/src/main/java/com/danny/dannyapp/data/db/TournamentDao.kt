package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentDao {

    @Query("SELECT * FROM tournaments WHERE season_id = :seasonId ORDER BY `order` ASC")
    fun getTournamentsForSeason(seasonId: Long): Flow<List<Tournament>>

    @Query("SELECT * FROM tournaments WHERE season_id = :seasonId ORDER BY `order` ASC")
    suspend fun getTournamentsListForSeason(seasonId: Long): List<Tournament>

    @Query("SELECT * FROM tournaments WHERE id = :id")
    suspend fun getTournamentById(id: Long): Tournament?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournament(tournament: Tournament): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTournaments(tournaments: List<Tournament>)

    @Update
    suspend fun updateTournament(tournament: Tournament)

    @Delete
    suspend fun deleteTournament(tournament: Tournament)

    @Query("DELETE FROM tournaments WHERE id = :id")
    suspend fun deleteTournamentById(id: Long)

    @Query("DELETE FROM tournaments WHERE season_id = :seasonId")
    suspend fun deleteTournamentsForSeason(seasonId: Long)
}
