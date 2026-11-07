package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CareerDao {

    @Query("SELECT * FROM careers ORDER BY created_at DESC")
    fun getAllCareers(): Flow<List<Career>>

    @Query("SELECT * FROM careers ORDER BY created_at DESC")
    suspend fun getAllCareersList(): List<Career>

    @Query("SELECT * FROM careers WHERE id = :id")
    suspend fun getCareerById(id: String): Career?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCareer(career: Career)

    @Query("DELETE FROM careers WHERE id = :id")
    suspend fun deleteCareerById(id: String)

    @Delete
    suspend fun deleteCareer(career: Career)
}
