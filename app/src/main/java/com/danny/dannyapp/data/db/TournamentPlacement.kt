package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournament_placements",
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
        Index(value = ["player_id"])
    ]
)
data class TournamentPlacement(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: Long,
    @ColumnInfo(name = "player_id")
    val playerId: Long,
    @ColumnInfo(name = "reached_round")
    val reachedRound: String,
    @ColumnInfo(name = "prize_money")
    val prizeMoney: Double = 0.0
) {
    init {
        require(prizeMoney >= 0.0) { "Prize money cannot be negative, got $prizeMoney" }
    }
}
