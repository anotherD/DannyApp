package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournament_matches",
    foreignKeys = [
        ForeignKey(
            entity = Tournament::class,
            parentColumns = ["id"],
            childColumns = ["tournament_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["tournament_id"]),
        Index(value = ["player_1_id"]),
        Index(value = ["player_2_id"])
    ]
)
data class TournamentMatch(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: Long,
    @ColumnInfo(name = "round")
    val round: String,
    @ColumnInfo(name = "match_index")
    val matchIndex: Int,
    @ColumnInfo(name = "player_1_id")
    val player1Id: Long,
    @ColumnInfo(name = "player_2_id")
    val player2Id: Long,
    @ColumnInfo(name = "player_1_score")
    val player1Score: Int = 0,
    @ColumnInfo(name = "player_2_score")
    val player2Score: Int = 0,
    @ColumnInfo(name = "winner_id")
    val winnerId: Long? = null,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "group_name")
    val groupName: String? = null
) {
    init {
        require(matchIndex >= 0) { "Match index cannot be negative, got $matchIndex" }
        require(player1Score >= 0) { "Player 1 score cannot be negative, got $player1Score" }
        require(player2Score >= 0) { "Player 2 score cannot be negative, got $player2Score" }
    }
}
