package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SeasonDao {

    @Query("SELECT * FROM seasons WHERE career_id = :careerId ORDER BY season_number DESC")
    fun getSeasonsForCareer(careerId: Long): Flow<List<Season>>

    @Query("SELECT * FROM seasons WHERE career_id = :careerId ORDER BY season_number ASC")
    suspend fun getSeasonsListForCareer(careerId: Long): List<Season>

    @Query("SELECT * FROM seasons WHERE career_id = :careerId ORDER BY season_number DESC LIMIT 1")
    suspend fun getCurrentSeasonForCareer(careerId: Long): Season?

    @Query("SELECT * FROM seasons WHERE id = :id")
    suspend fun getSeasonById(id: Long): Season?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeason(season: Season): Long

    @Update
    suspend fun updateSeason(season: Season)

    @Delete
    suspend fun deleteSeason(season: Season)

    @Query("DELETE FROM seasons WHERE id = :id")
    suspend fun deleteSeasonById(id: Long)
}
