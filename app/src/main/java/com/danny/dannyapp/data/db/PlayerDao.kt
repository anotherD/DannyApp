package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlayerDao {

    @Query("SELECT * FROM players ORDER BY skill DESC, price_money DESC, name ASC")
    fun getAllPlayers(): Flow<List<Player>>

    @Query("SELECT * FROM players ORDER BY skill DESC, price_money DESC, name ASC")
    fun getAllPlayersSortedBySkill(): Flow<List<Player>>

    @Query("SELECT * FROM players ORDER BY name ASC")
    fun getAllPlayersSortedByName(): Flow<List<Player>>

    @Query("SELECT * FROM players WHERE id = :id")
    suspend fun getPlayerById(id: Long): Player?

    @Query("SELECT COUNT(*) FROM players")
    suspend fun getPlayerCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayer(player: Player): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(players: List<Player>)

    @Update
    suspend fun updatePlayer(player: Player)

    @Delete
    suspend fun deletePlayer(player: Player)

    @Query("DELETE FROM players WHERE id = :id")
    suspend fun deletePlayerById(id: Long)

    /* CSV Import functions */
    // Fügt nur Spieler hinzu, deren Name noch NICHT in der DB existiert
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun csvInsertNewPlayers(players: List<Player>)

    // Aktualisiert gezielt nur die Stammdaten bestehender Spieler, ID bleibt erhalten!
    @Query("UPDATE players SET skill = :skill, country_code = :countryCode, price_money = :prizeMoney, tour_card_year = :tourCardYear WHERE name = :name")
    suspend fun csvUpdatePlayerStats(name: String, skill: Int, countryCode: String, prizeMoney: Double, tourCardYear: Int)

    // Hilfsfunktion, die beides sauber kombiniert
    @Transaction
    suspend fun syncPlayersFromCsv(csvPlayers: List<Player>) {
        csvInsertNewPlayers(csvPlayers)
        csvPlayers.forEach { player ->
            csvUpdatePlayerStats(player.name, player.skill, player.countryCode, player.prizeMoney, player.tourCardYear)
        }
    }
}