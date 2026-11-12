package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

data class CareerWithProfile(
    @Embedded val career: Career,
    @ColumnInfo(name = "profile_name") val profileName: String? = null,
    @ColumnInfo(name = "player_name") val playerName: String? = null,
    @ColumnInfo(name = "country_code") val countryCode: String? = null
)

@Dao
interface CareerDao {

    @Query("SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code FROM careers c LEFT JOIN user_profiles p ON c.profile_id = p.id ORDER BY c.created_at DESC")
    fun getAllCareers(): Flow<List<CareerWithProfile>>

    @Query("SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code FROM careers c LEFT JOIN user_profiles p ON c.profile_id = p.id ORDER BY c.created_at DESC")
    suspend fun getAllCareersList(): List<CareerWithProfile>

    @Query("SELECT c.*, p.name as profile_name, p.player_name as player_name, p.country_code as country_code FROM careers c LEFT JOIN user_profiles p ON c.profile_id = p.id WHERE c.id = :id")
    suspend fun getCareerById(id: Long): CareerWithProfile?

    @Query("SELECT COUNT(*) FROM careers WHERE profile_id = :profileId")
    suspend fun getCareerCountForProfile(profileId: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareer(career: Career): Long

    @Query("DELETE FROM careers WHERE id = :id")
    suspend fun deleteCareerById(id: Long)

    @Delete
    suspend fun deleteCareer(career: Career)
}
