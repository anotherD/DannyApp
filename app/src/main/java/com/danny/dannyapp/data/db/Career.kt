package com.danny.dannyapp.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "careers")
data class Career(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "name")
    val name: String,
    @ColumnInfo(name = "level")
    val level: Int,
    @ColumnInfo(name = "season")
    val season: Int = 1,
    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
) {
    init {
        require(season >= 1) { "Season must be at least 1" }
    }
}
