package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class PlayerRepository(context: Context) {

    private val playerDao: PlayerDao = AppDatabase.getDatabase(context).playerDao()

    val allPlayers: Flow<List<Player>> = playerDao.getAllPlayers()

    suspend fun getPlayerById(id: Long): Player? = withContext(Dispatchers.IO) {
        playerDao.getPlayerById(id)
    }

    suspend fun insertPlayer(player: Player): Long = withContext(Dispatchers.IO) {
        playerDao.insertPlayer(player)
    }

    suspend fun updatePlayer(player: Player) = withContext(Dispatchers.IO) {
        playerDao.updatePlayer(player)
    }

    suspend fun deletePlayer(player: Player) = withContext(Dispatchers.IO) {
        playerDao.deletePlayer(player)
    }

    suspend fun deletePlayerById(id: Long) = withContext(Dispatchers.IO) {
        playerDao.deletePlayerById(id)
    }
}
