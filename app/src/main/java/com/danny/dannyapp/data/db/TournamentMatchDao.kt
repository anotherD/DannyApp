package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TournamentMatchDao {

    @Query("SELECT * FROM tournament_matches WHERE tournament_id = :tournamentId ORDER BY `round` ASC, match_index ASC")
    fun getMatchesForTournament(tournamentId: Long): Flow<List<TournamentMatch>>

    @Query("SELECT tm.* FROM tournament_matches tm INNER JOIN tournaments t ON tm.tournament_id = t.id WHERE t.season_id = :seasonId")
    fun getMatchesFlowForSeason(seasonId: Long): Flow<List<TournamentMatch>>

    @Query("SELECT * FROM tournament_matches WHERE tournament_id = :tournamentId ORDER BY `round` ASC, match_index ASC")
    suspend fun getMatchesListForTournament(tournamentId: Long): List<TournamentMatch>

    @Query("SELECT * FROM tournament_matches WHERE id = :id")
    suspend fun getMatchById(id: Long): TournamentMatch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: TournamentMatch): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<TournamentMatch>)

    @Update
    suspend fun updateMatch(match: TournamentMatch)

    @Delete
    suspend fun deleteMatch(match: TournamentMatch)

    @Query("DELETE FROM tournament_matches WHERE tournament_id = :tournamentId")
    suspend fun deleteMatchesForTournament(tournamentId: Long)
}
