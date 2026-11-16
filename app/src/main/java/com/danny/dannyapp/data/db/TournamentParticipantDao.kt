package com.danny.dannyapp.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TournamentParticipantDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertParticipants(participants: List<TournamentParticipant>)

    @Query("SELECT * FROM tournament_participants WHERE tournament_id = :tournamentId")
    suspend fun getParticipantsForTournament(tournamentId: Long): List<TournamentParticipant>

    @Query("SELECT * FROM tournament_participants WHERE tournament_id = :tournamentId AND seed_number IS NOT NULL")
    suspend fun getSeededParticipantsForTournament(tournamentId: Long): List<TournamentParticipant>
}
