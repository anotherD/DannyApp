package com.danny.dannyapp.domain.usecase

import android.content.Context
import com.danny.dannyapp.data.db.AppDatabase
import com.danny.dannyapp.data.db.Player
import com.danny.dannyapp.data.db.PlayerRepository
import kotlinx.coroutines.flow.Flow

/**
 * UseCase zum Abrufen und Beobachten von Spielern aus dem PlayerRepository.
 */
class GetPlayersUseCase(private val playerRepository: PlayerRepository) {

    constructor(context: Context) : this(
        PlayerRepository(
            AppDatabase.getDatabase(context.applicationContext).playerDao()
        )
    )

    /**
     * Beobachtet die nach Fähigkeit (Skill) sortierte Spielerliste.
     */
    fun observePlayersSortedBySkill(): Flow<List<Player>> = playerRepository.playersSortedBySkill

    /**
     * Beobachtet die nach Name sortierte Spielerliste.
     */
    fun observePlayersSortedByName(): Flow<List<Player>> = playerRepository.playersSortedByName
}
