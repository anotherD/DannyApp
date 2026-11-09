package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tour_cards",
    foreignKeys = [
        ForeignKey(
            entity = Season::class,
            parentColumns = ["id"],
            childColumns = ["season_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Player::class,
            parentColumns = ["id"],
            childColumns = ["player_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["season_id", "player_id"], unique = true),
        Index(value = ["season_id"]),
        Index(value = ["player_id"])
    ]
)
data class TourCard(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "season_id")
    val seasonId: Long,
    @ColumnInfo(name = "player_id")
    val playerId: Long? = null,
    @ColumnInfo(name = "tour_card_year")
    val tourCardYear: Int
) {
    val isUser: Boolean
        get() = playerId == null

    init {
        require(tourCardYear in 1..2) { "Tour card year must be 1 or 2, got $tourCardYear" }
    }
}
