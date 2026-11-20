package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournament_participants",
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
        Index(value = ["player_id"]),
        Index(value = ["tournament_id", "player_id"], unique = true)
    ]
)
data class TournamentParticipant(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "tournament_id")
    val tournamentId: Long,
    @ColumnInfo(name = "player_id")
    val playerId: Long,
    @ColumnInfo(name = "seed_number")
    val seedNumber: Int? = null,
    @ColumnInfo(name = "qualification_type")
    val qualificationType: String
) {
    init {
        require(qualificationType.isNotBlank()) { "Qualification type must not be blank" }
    }
}
