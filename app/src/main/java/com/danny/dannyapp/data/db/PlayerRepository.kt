package com.danny.dannyapp.data.db

import kotlinx.coroutines.flow.Flow

/**
 * Repository zur Verwaltung aller Spielerdaten.
 */
class PlayerRepository(private val playerDao: PlayerDao) {

    /**
     * Liefert einen [Flow] aller Spieler, sortiert nach Spielstärke.
     */
    val playersSortedBySkill: Flow<List<Player>> = playerDao.getAllPlayersSortedBySkill()

    /**
     * Liefert einen [Flow] aller Spieler, alphabetisch sortiert nach Name.
     */
    val playersSortedByName: Flow<List<Player>> = playerDao.getAllPlayersSortedByName()

    /**
     * Ruft die Liste aller Spieler einmalig ab.
     */
    suspend fun getAllPlayersList(): List<Player> {
        AppDatabase.initialSyncCompleted.await()
        return playerDao.getAllPlayersList()
    }

    /**
     * Ruft einen Spieler anhand seiner [id] ab.
     *
     * @throws IllegalArgumentException wenn [id] <= 0.
     * @throws IllegalStateException wenn der Spieler nicht gefunden wird.
     */
    suspend fun getPlayerById(id: Long): Player {
        require(id > 0L) { "Ungültige Spieler-ID: $id" }
        return requireNotNull(playerDao.getPlayerById(id)) { "CPU-Spieler mit ID $id nicht gefunden" }
    }
}
