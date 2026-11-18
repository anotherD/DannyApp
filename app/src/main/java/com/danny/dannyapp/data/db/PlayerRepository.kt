package com.danny.dannyapp.data.db

import android.content.Context
import kotlinx.coroutines.flow.Flow

class PlayerRepository(context: Context) {

    private val playerDao: PlayerDao = AppDatabase.getDatabase(context.applicationContext).playerDao()

    val playersSortedBySkill: Flow<List<Player>> = playerDao.getAllPlayersSortedBySkill()

    val playersSortedByName: Flow<List<Player>> = playerDao.getAllPlayersSortedByName()
}
