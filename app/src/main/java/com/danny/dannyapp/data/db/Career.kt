package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "careers",
    foreignKeys = [
        ForeignKey(
            entity = UserProfile::class,
            parentColumns = ["id"],
            childColumns = ["profile_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profile_id"])]
)
data class Career(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "level")
    val level: Int,
    @ColumnInfo(name = "season")
    val season: Int = 1,
    @ColumnInfo(name = "profile_id")
    val profileId: Long = 0,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(level in MIN_LEVEL..MAX_LEVEL) { "Level must be between $MIN_LEVEL and $MAX_LEVEL" }
        require(season >= 1) { "Season must be at least 1" }
    }

    companion object {
        const val MIN_LEVEL = 1
        const val MAX_LEVEL = 25
    }
}
