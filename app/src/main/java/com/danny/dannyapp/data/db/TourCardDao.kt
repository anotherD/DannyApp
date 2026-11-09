package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface TourCardDao {

    @Transaction
    @Query("SELECT * FROM tour_cards WHERE season_id = :seasonId")
    fun getTourCardsForSeason(seasonId: Long): Flow<List<TourCardWithPlayer>>

    @Transaction
    @Query("SELECT * FROM tour_cards WHERE season_id = :seasonId")
    suspend fun getTourCardsListForSeason(seasonId: Long): List<TourCardWithPlayer>

    @Query("SELECT * FROM tour_cards WHERE season_id = :seasonId")
    suspend fun getRawTourCardsForSeason(seasonId: Long): List<TourCard>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTourCard(tourCard: TourCard): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTourCards(tourCards: List<TourCard>)

    @Query("DELETE FROM tour_cards WHERE season_id = :seasonId")
    suspend fun deleteTourCardsForSeason(seasonId: Long)
}
