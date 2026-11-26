package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class CareerWithProfile(
    @Embedded val career: Career,
    @ColumnInfo(name = "profile_name") val profileName: String,
    @ColumnInfo(name = "player_name") val playerName: String,
    @ColumnInfo(name = "country_code") val countryCode: String,
    @ColumnInfo(name = "current_tournament_name") val currentTournamentName: String?,
    @ColumnInfo(name = "current_tournament_round") val currentTournamentRound: String?
)

@Dao
interface CareerDao {

    @Query("""
        SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code,
               (SELECT t.name FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_name,
               (SELECT t.current_round FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_round
        FROM careers c 
        INNER JOIN user_profiles p ON c.profile_id = p.id 
        ORDER BY c.created_at DESC
    """)
    fun getAllCareers(): Flow<List<CareerWithProfile>>

    @Query("""
        SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code,
               (SELECT t.name FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_name,
               (SELECT t.current_round FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_round
        FROM careers c 
        INNER JOIN user_profiles p ON c.profile_id = p.id 
        ORDER BY c.created_at DESC
    """)
    suspend fun getAllCareersList(): List<CareerWithProfile>

    @Query("""
        SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code,
               (SELECT t.name FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_name,
               (SELECT t.current_round FROM tournaments t INNER JOIN seasons s ON t.season_id = s.id WHERE s.career_id = c.id AND s.season_number = c.season AND t.is_completed = 0 ORDER BY t.`order` ASC LIMIT 1) as current_tournament_round
        FROM careers c 
        INNER JOIN user_profiles p ON c.profile_id = p.id 
        WHERE c.id = :id
    """)
    suspend fun getCareerById(id: Long): CareerWithProfile?

    @Query("SELECT COUNT(*) FROM careers WHERE profile_id = :profileId")
    suspend fun getCareerCountForProfile(profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertCareer(career: Career): Long

    @Update
    suspend fun updateCareer(career: Career)

    @Query("DELETE FROM careers WHERE id = :id")
    suspend fun deleteCareerById(id: Long)

    @Delete
    suspend fun deleteCareer(career: Career)
}
