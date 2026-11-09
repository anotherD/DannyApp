package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "seasons",
    foreignKeys = [
        ForeignKey(
            entity = Career::class,
            parentColumns = ["id"],
            childColumns = ["career_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["career_id"])]
)
data class Season(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "career_id")
    val careerId: Long,
    @ColumnInfo(name = "season_number")
    val seasonNumber: Int = 1,
    @ColumnInfo(name = "is_completed")
    val isCompleted: Boolean = false,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(seasonNumber >= 1) { "Season number must be at least 1, got $seasonNumber" }
    }
}
