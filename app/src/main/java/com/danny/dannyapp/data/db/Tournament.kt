package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tournaments",
    foreignKeys = [
        ForeignKey(
            entity = Season::class,
            parentColumns = ["id"],
            childColumns = ["season_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["season_id"])]
)
data class Tournament(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "season_id")
    val seasonId: Long,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "type")
    val type: String,
    @ColumnInfo(name = "order")
    val order: Int,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "host_country")
    val hostCountry: String? = null
) {
    init {
        require(name.isNotBlank()) { "Tournament name must not be blank" }
        require(order >= 1) { "Tournament order must be at least 1, got $order" }
    }
}
